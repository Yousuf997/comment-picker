package app.giveaway.core.media

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.SystemClock
import android.view.Surface
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

private const val AVC = MediaFormat.MIMETYPE_VIDEO_AVC

/** The recording's frame size: 1080 x 1920, or 720 x 1280 when the encoder can't do Full HD (plan A20). */
@Suppress("MagicNumber") // The spec's frame sizes (1080 x 1920, 720 x 1280 fallback) and their bit rates.
enum class VideoSize(val width: Int, val height: Int, val bitRate: Int) {
    FULL_HD(1080, 1920, 6_000_000),
    HD(720, 1280, 3_500_000),
    ;

    companion object {
        /** The largest size the device's H.264 encoder supports in portrait. */
        fun best(): VideoSize {
            val supported = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { it.isEncoder && AVC in it.supportedTypes }
                .mapNotNull { info ->
                    runCatching { info.getCapabilitiesForType(AVC).videoCapabilities }.getOrNull()
                }
            return if (supported.any { it.isSizeSupported(FULL_HD.width, FULL_HD.height) }) FULL_HD else HD
        }
    }
}

/** How a recording ended. A failure never touches the draw: its result was saved before the animation began. */
sealed interface RecordingResult {
    data class Saved(val file: File, val durationMs: Long, val size: VideoSize) : RecordingResult

    data class Failed(val error: Throwable) : RecordingResult
}

/**
 * Records the draw animation, not the screen (spec: Draw recording): frames come from a [SceneRenderer] at the
 * timeline's time and go to an H.264 encoder's input surface, muxed into an MP4 at 30 fps with no audio. Frames are
 * paced in real time, in step with what S12 shows, and pausing stops the clock (app in the background). [stop] ends
 * the video early; the draw itself always completes. Runs on its own thread.
 */
class DrawRecorder(
    private val renderer: SceneRenderer,
    private val timeline: DrawTimeline,
    private val output: File,
    private val size: VideoSize = VideoSize.best(),
) {
    private val stopped = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    @Volatile private var thread: Thread? = null

    /** Starts recording; [onDone] is called on the recorder thread when the file is complete or recording failed. */
    fun start(onDone: (RecordingResult) -> Unit) {
        check(thread == null) { "Already recording" }
        thread = Thread({ onDone(record()) }, "draw-recorder").apply { start() }
    }

    fun pause() = paused.set(true)

    fun resume() = paused.set(false)

    /** Ends the video now ("Stop recording"); the file keeps what was recorded so far. */
    fun stop() = stopped.set(true)

    /** Waits for the recording to finish, for tests and for leaving S12. */
    fun join(timeoutMs: Long) = thread?.join(timeoutMs)

    private fun record(): RecordingResult {
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var surface: Surface? = null
        return try {
            encoder = MediaCodec.createEncoderByType(AVC)
            encoder.configure(format(), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = encoder.createInputSurface()
            encoder.start()
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val writer = Writer(encoder, muxer)
            val endMs = timeline.durationMs + SUMMARY_HOLD_MS
            var videoMs = 0L
            var last = SystemClock.elapsedRealtime()
            while (videoMs <= endMs && !stopped.get()) {
                val now = SystemClock.elapsedRealtime()
                if (!paused.get()) videoMs += now - last
                last = now
                if (!paused.get()) {
                    val canvas = surface.lockHardwareCanvas()
                    try {
                        renderer.draw(canvas, timeline, videoMs)
                    } finally {
                        surface.unlockCanvasAndPost(canvas)
                    }
                    writer.drain(endOfStream = false)
                }
                Thread.sleep(FRAME_MS)
            }
            encoder.signalEndOfInputStream()
            writer.drain(endOfStream = true)
            muxer.stop()
            RecordingResult.Saved(output, videoMs.coerceAtMost(endMs), size)
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            // MediaCodec reports problems as IllegalStateException, CodecException or IOException; all mean "no video".
            output.delete()
            RecordingResult.Failed(error)
        } finally {
            runCatching { encoder?.stop() }
            encoder?.release()
            runCatching { muxer?.release() }
            surface?.release()
        }
    }

    private fun format() = MediaFormat.createVideoFormat(AVC, size.width, size.height).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        setInteger(MediaFormat.KEY_BIT_RATE, size.bitRate)
        setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
    }

    /** Moves encoded frames into the MP4. */
    private class Writer(private val encoder: MediaCodec, private val muxer: MediaMuxer) {
        private val info = MediaCodec.BufferInfo()
        private var track = -1

        fun drain(endOfStream: Boolean) {
            var waits = 0
            while (true) {
                val index = encoder.dequeueOutputBuffer(info, if (endOfStream) DRAIN_TIMEOUT_US else 0L)
                when {
                    index == MediaCodec.INFO_TRY_AGAIN_LATER ->
                        if (!endOfStream || ++waits > MAX_EOS_WAITS) return
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                    }
                    index >= 0 -> {
                        val buffer = checkNotNull(encoder.getOutputBuffer(index))
                        val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (!config && info.size > 0 && track >= 0) muxer.writeSampleData(track, buffer, info)
                        encoder.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }
    }

    companion object {
        const val FPS = 30
        private const val FRAME_MS = 1_000L / FPS
        private const val DRAIN_TIMEOUT_US = 10_000L
        private const val MAX_EOS_WAITS = 300

        /** The closing summary frame with the commit hash stays on screen this long (spec: ends on a summary). */
        const val SUMMARY_HOLD_MS = 2_500L

        /** Rough file size for the S11 storage check (plan M-03): bit rate times length, plus muxing overhead. */
        fun estimatedBytes(timeline: DrawTimeline, size: VideoSize): Long = estimatedBytes(timeline.durationMs, size)

        fun estimatedBytes(animationMs: Long, size: VideoSize): Long =
            (animationMs + SUMMARY_HOLD_MS) * size.bitRate / BITS_PER_BYTE / MS_PER_S *
                OVERHEAD_PERCENT / PERCENT

        private const val BITS_PER_BYTE = 8
        private const val MS_PER_S = 1_000
        private const val OVERHEAD_PERCENT = 110
        private const val PERCENT = 100
    }
}
