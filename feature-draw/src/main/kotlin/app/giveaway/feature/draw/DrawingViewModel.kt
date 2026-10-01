package app.giveaway.feature.draw

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.giveaway.core.data.draw.DrawService
import app.giveaway.core.data.draw.SavedDraw
import app.giveaway.core.data.media.MediaRepository
import app.giveaway.core.media.DrawRecorder
import app.giveaway.core.media.DrawTimeline
import app.giveaway.core.media.RecordingResult
import app.giveaway.core.media.SceneRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/** The REC badge's state on S12 (spec: Draw recording). */
enum class RecordingState { OFF, RECORDING, STOPPED, SAVED, FAILED }

/** S12 Drawing (plans C-22, M-02): replays the saved real draw and records it when S11's switch was on. */
@HiltViewModel
class DrawingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    draws: DrawService,
    private val media: MediaRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<DrawingRoute>()

    private val drawState = MutableStateFlow<Loaded?>(null)

    /** Null while loading; [Loaded.draw] is null if there's no saved draw (the screen then just offers to move on). */
    val draw: StateFlow<Loaded?> = drawState.asStateFlow()

    data class Loaded(val draw: SavedDraw?)

    private val recordingState = MutableStateFlow(RecordingState.OFF)
    val recording: StateFlow<RecordingState> = recordingState.asStateFlow()

    private var recorder: DrawRecorder? = null

    init {
        viewModelScope.launch { drawState.value = Loaded(draws.savedDraw(route.giveawayId)) }
    }

    /**
     * Starts the recording once the animation starts. The recorder runs on its own thread and outlives this screen,
     * which leaves the back stack before the closing frame is written; a failure never touches the saved draw.
     */
    fun startRecording(renderer: SceneRenderer, timeline: DrawTimeline) {
        if (!route.record || recorder != null) return
        val file = media.recordingFile(route.giveawayId)
        recordingState.value = RecordingState.RECORDING
        recorder = DrawRecorder(renderer, timeline, file).also { recorder ->
            recorder.start { result ->
                recordingState.value = when (result) {
                    is RecordingResult.Saved -> {
                        // On the recorder thread: the S13 choice is stored before anything can close the app.
                        runBlocking { media.recordingSaved(route.giveawayId, result.file) }
                        RecordingState.SAVED
                    }
                    is RecordingResult.Failed -> RecordingState.FAILED
                }
            }
        }
    }

    /** "Stop recording": ends the video, never the draw (spec: requirement 5). */
    fun stopRecording() {
        recorder?.stop()
        if (recordingState.value == RecordingState.RECORDING) recordingState.value = RecordingState.STOPPED
    }

    /** The app went to the background: the animation and the recording pause together (spec: edge cases). */
    fun pauseRecording() {
        recorder?.pause()
    }

    fun resumeRecording() {
        recorder?.resume()
    }
}
