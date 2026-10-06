package app.giveaway.core.media

import android.graphics.Color
import android.graphics.Typeface
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.giveaway.draw.Pick
import app.giveaway.draw.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** M-02 acceptance on a device: the MP4 has one H.264 track at the chosen size; a failure leaves no file. */
@RunWith(AndroidJUnit4::class)
class DrawRecorderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val picks = listOf(Pick(1, "amy", Role.WINNER), Pick(2, "bob", Role.ALTERNATE))
    private val timeline = DrawTimeline(picks, listOf("amy", "bob", "cat", "dan"), reducedMotion = true)

    private val style = SceneStyle(
        background = Color.rgb(0x17, 0x17, 0x1C),
        text = Color.WHITE,
        muted = Color.LTGRAY,
        accent = Color.rgb(0xC2, 0x41, 0x0C),
        onAccent = Color.WHITE,
        display = Typeface.DEFAULT_BOLD,
        body = Typeface.DEFAULT,
    )
    private val text = object : SceneText {
        override fun picking(pick: Pick) = "Picking ${pick.position}"
        override fun announcement(pick: Pick) = "${pick.role} ${pick.position}: @${pick.username}"
        override val done = "All picked"
        override val pickedSoFar = "Picked so far"
        override fun entries(count: Int) = "$count entries"
    }
    private val renderer = DrawSceneRenderer(style, text, "Win a tote bag!", entryCount = 4)

    private fun record(renderer: SceneRenderer, file: File): RecordingResult {
        var result: RecordingResult? = null
        val done = CountDownLatch(1)
        DrawRecorder(renderer, timeline, file).start {
            result = it
            done.countDown()
        }
        assertTrue("recording finished", done.await(TIMEOUT_S, TimeUnit.SECONDS))
        return checkNotNull(result)
    }

    @Test
    fun theVideoIsAnH264Mp4AtTheChosenSize() {
        val file = File(context.cacheDir, "draw.mp4").apply { delete() }
        val saved = record(renderer, file) as RecordingResult.Saved
        val extractor = MediaExtractor().apply { setDataSource(file.absolutePath) }
        try {
            assertEquals("one track, no audio", 1, extractor.trackCount)
            val format = extractor.getTrackFormat(0)
            assertEquals(MediaFormat.MIMETYPE_VIDEO_AVC, format.getString(MediaFormat.KEY_MIME))
            assertEquals(saved.size.width, format.getInteger(MediaFormat.KEY_WIDTH))
            assertEquals(saved.size.height, format.getInteger(MediaFormat.KEY_HEIGHT))
            assertTrue("has a duration", format.getLong(MediaFormat.KEY_DURATION) > 0)
        } finally {
            extractor.release()
            file.delete()
        }
    }

    @Test
    fun aRenderingFailureLeavesNoFileAndIsReported() {
        val file = File(context.cacheDir, "broken.mp4").apply { delete() }
        val broken = SceneRenderer { _, _, _ -> error("injected encoder failure") }
        val result = record(broken, file)
        assertTrue(result is RecordingResult.Failed)
        assertFalse(file.exists())
    }

    private companion object {
        const val TIMEOUT_S = 60L
    }
}
