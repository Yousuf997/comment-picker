package app.giveaway.feature.draw

import android.app.Application
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.giveaway.core.data.draw.SavedDraw
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.designsystem.handle
import app.giveaway.core.media.DrawTimeline
import app.giveaway.draw.Pick
import app.giveaway.draw.Role
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/** C-22 acceptance: each pick is announced as it lands; reduced motion is a short reveal; the screen moves on. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class DrawingTest {

    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val picks = listOf(
        Pick(1, "amy.designs", Role.WINNER),
        Pick(2, "bob", Role.WINNER),
        Pick(3, "cat", Role.ALTERNATE),
    )
    private val draw = SavedDraw(
        title = "Win a tote bag!",
        picks = picks,
        entrants = listOf("amy.designs", "bob", "cat", "dan", "eve", "fay", "gus", "hal"),
        drawnAt = Instant.parse("2026-10-08T12:00:00Z"),
        entryCount = 8,
    )
    private var finished = 0

    private var stops = 0
    private var started = 0

    private fun show(
        reducedMotion: Boolean = false,
        saved: SavedDraw? = draw,
        recording: RecordingState = RecordingState.OFF,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            GiveawayTheme {
                DrawingScreen(
                    loaded = DrawingViewModel.Loaded(saved),
                    reducedMotion = reducedMotion,
                    onFinished = { finished++ },
                    recording = recording,
                    onStart = { _, _ -> started++ },
                    onStopRecording = { stops++ },
                )
            }
        }
    }

    private fun announced(text: String) =
        compose.onNodeWithTag("drawing:announcement").assertContentDescriptionEquals(text)

    private fun advanceTo(ms: Long) {
        compose.mainClock.advanceTimeBy(ms)
        compose.waitForIdle()
    }

    @Test
    fun eachPickIsAnnouncedAsItLands() {
        val timeline = DrawTimeline(picks, draw.entrants, reducedMotion = false)
        show()
        val (first, second, third) = timeline.segments
        advanceTo(first.landMs + FRAME)
        announced(app.getString(R.string.drawing_announce_winner, "1", handle("amy.designs")))
        advanceTo(second.landMs - first.landMs)
        announced(app.getString(R.string.drawing_announce_winner, "2", handle("bob")))
        advanceTo(third.landMs - second.landMs)
        announced(app.getString(R.string.drawing_announce_alternate, "1", handle("cat")))
    }

    @Test
    fun theScreenMovesOnWhenTheDrawIsDone() {
        val timeline = DrawTimeline(picks, draw.entrants, reducedMotion = false)
        show()
        advanceTo(timeline.durationMs)
        assertEquals(0, finished)
        advanceTo(HOLD + FRAME * 2)
        assertEquals(1, finished)
    }

    @Test
    fun reducedMotionIsAShortReveal() {
        val quick = DrawTimeline(picks, draw.entrants, reducedMotion = true)
        show(reducedMotion = true)
        advanceTo(quick.durationMs + HOLD + FRAME * 2)
        assertEquals(1, finished)
        assertEquals(true, quick.durationMs < DrawTimeline.WINNER_SPIN_MS)
    }

    @Test
    fun recordingShowsTheRecBadgeAndCanBeStopped() {
        show(recording = RecordingState.RECORDING)
        advanceTo(FRAME)
        assertEquals("the recorder starts with the animation", 1, started)
        compose.onNodeWithTag("drawing:rec").assertExists()
        compose.onNodeWithText(app.getString(R.string.drawing_only_screen)).assertExists()
        compose.onNodeWithText(app.getString(R.string.drawing_stop_recording)).performClick()
        assertEquals(1, stops)
    }

    @Test
    fun aFailedRecordingSaysTheResultIsSafe() {
        show(recording = RecordingState.FAILED)
        advanceTo(FRAME)
        compose.onNodeWithText(app.getString(R.string.drawing_recording_failed)).assertExists()
    }

    @Test
    fun withoutASavedDrawItOffersToMoveOn() {
        show(saved = null)
        compose.onNodeWithText(app.getString(R.string.drawing_finish)).assertExists()
    }

    @Test
    fun screenshotMidDraw() {
        val timeline = DrawTimeline(picks, draw.entrants, reducedMotion = false)
        show()
        advanceTo(timeline.segments[1].startMs + timeline.segments[1].spinMs / 2)
        compose.onRoot().captureRoboImage("src/test/screenshots/s12_drawing.png")
    }

    @Test
    @Config(qualifiers = "ar-w390dp-h844dp-xhdpi")
    fun screenshotArabicLanded() {
        val timeline = DrawTimeline(picks, draw.entrants, reducedMotion = false)
        show()
        advanceTo(timeline.segments[1].landMs + FRAME)
        compose.onRoot().captureRoboImage("src/test/screenshots/s12_drawing_arabic.png")
    }

    private companion object {
        const val FRAME = 32L
        const val HOLD = 1_500L
    }
}
