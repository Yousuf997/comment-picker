package app.giveaway.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.giveaway.core.designsystem.GiveawayDimens
import app.giveaway.core.designsystem.GiveawayTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Wizard header (step 2 of 6) and the dark draw stage, in light, dark and Arabic. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h640dp-xhdpi")
class WizardAndStageScreenshotTest {

    @Test
    fun light() = capture("light", dark = false, title = "New giveaway", stage = "Ready to draw", draw = "Draw winners")

    @Test
    fun dark() = capture("dark", dark = true, title = "New giveaway", stage = "Ready to draw", draw = "Draw winners")

    @Test
    @Config(qualifiers = "ar-w390dp-h640dp-xhdpi")
    fun arabic() =
        capture("arabic", dark = false, title = "سحب جديد", stage = "جاهز للسحب", draw = "اختيار الفائزين")

    private fun capture(name: String, dark: Boolean, title: String, stage: String, draw: String) =
        captureRoboImage("src/test/screenshots/wizard_stage_$name.png") {
            GiveawayTheme(darkTheme = dark) { Sample(title, stage, draw) }
        }

    @Composable
    private fun Sample(title: String, stage: String, draw: String) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GiveawayTheme.colors.background)
                .padding(GiveawayDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            WizardHeader(title = title, step = 2, onBack = {})
            DrawStage(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(stage, style = GiveawayTheme.typography.display)
                    // No onStage argument: the stage switches the primary button to the accent fill.
                    PrimaryButton(draw, onClick = {}, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
