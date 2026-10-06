package app.giveaway.feature.draw

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.graphics.createBitmap
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.media.DrawSceneRenderer
import app.giveaway.core.media.DrawTimeline
import app.giveaway.draw.Pick
import app.giveaway.draw.Role
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** M-19: the recorded video's frames in English and Arabic; Arabic lists start at the right, handles stay LTR. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecordedSceneTest {

    @get:Rule
    val compose = createComposeRule()

    private val picks = listOf(
        Pick(1, "maya.k", Role.WINNER),
        Pick(2, "sam_r", Role.WINNER),
        Pick(3, "lina.art", Role.ALTERNATE),
    )
    private val entrants = listOf("maya.k", "sam_r", "lina.art", "omar99", "zed", "noor.h", "ali_22")
    private val timeline = DrawTimeline(picks, entrants, reducedMotion = false)

    /** Renders one frame at half the video size, with the app's real fonts and words. */
    private fun frame(timeMs: Long): Bitmap {
        lateinit var bitmap: Bitmap
        compose.setContent {
            GiveawayTheme {
                val context = LocalContext.current
                val style = rememberSceneStyle(context)
                SideEffect {
                    val text = AndroidSceneText(context, winners = 2, alternates = 1)
                    val renderer = DrawSceneRenderer(style, text, "Win a tote bag!", 1_204)
                    bitmap = createBitmap(WIDTH, HEIGHT).also { renderer.draw(Canvas(it), timeline, timeMs) }
                }
            }
        }
        compose.waitForIdle()
        return bitmap
    }

    private val afterSecondPick get() = timeline.segments[1].landMs + LANDED

    @Test
    fun midDraw() = frame(afterSecondPick).captureRoboImage("src/test/screenshots/recording_mid.png")

    @Test
    @Config(qualifiers = "ar")
    fun midDrawArabic() = frame(afterSecondPick).captureRoboImage("src/test/screenshots/recording_mid_ar.png")

    @Test
    @Config(qualifiers = "ar")
    fun summaryArabic() =
        frame(timeline.durationMs + LANDED).captureRoboImage("src/test/screenshots/recording_summary_ar.png")

    private companion object {
        const val WIDTH = 540
        const val HEIGHT = 960
        const val LANDED = 400L
    }
}
