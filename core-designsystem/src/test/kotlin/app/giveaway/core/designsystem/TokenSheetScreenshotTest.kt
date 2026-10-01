package app.giveaway.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Token sheet at the canvas size (390 x 844) in light, dark and Arabic. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xxhdpi")
class TokenSheetScreenshotTest {

    @Test
    fun light() = captureRoboImage("src/test/screenshots/token_sheet_light.png") {
        GiveawayTheme(darkTheme = false) { TokenSheet() }
    }

    @Test
    fun dark() = captureRoboImage("src/test/screenshots/token_sheet_dark.png") {
        GiveawayTheme(darkTheme = true) { TokenSheet() }
    }

    @Test
    @Config(qualifiers = "ar-w390dp-h844dp-xxhdpi")
    fun arabic() = captureRoboImage("src/test/screenshots/token_sheet_arabic.png") {
        GiveawayTheme(darkTheme = false) { TokenSheet(sample = ARABIC_SAMPLE) }
    }

    private companion object {
        const val LATIN_SAMPLE = "Fair giveaways, with proof."
        const val ARABIC_SAMPLE = "سحوبات عادلة، مع إثبات."
        const val HASH_SAMPLE = "#draw 9f2c41e07ab3d5c8"
    }

    @Composable
    private fun TokenSheet(sample: String = LATIN_SAMPLE) {
        val colors = GiveawayTheme.colors
        val type = GiveawayTheme.typography
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
                .padding(GiveawayDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(sample, style = type.display, color = colors.onBackground)
            TypeRow("Title", type.titleLarge, sample)
            TypeRow("Body", type.body, sample)
            TypeRow("Body strong", type.bodyStrong, sample)
            TypeRow("Caption", type.caption, sample)
            TypeRow("OVERLINE", type.overline, sample.uppercase())
            TypeRow("Code", type.code, HASH_SAMPLE)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Swatch("bg", colors.background, colors.onBackground)
                Swatch("surface", colors.surface, colors.onBackground)
                Swatch("muted", colors.surfaceMuted, colors.onMuted)
                Swatch("accent", colors.accent, colors.onAccent)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Swatch("soft", colors.accentSoft, colors.accentOnSoft)
                Swatch("success", colors.successContainer, colors.success)
                Swatch("info", colors.infoContainer, colors.info)
                Swatch("danger", colors.danger, colors.onDanger)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Swatch("stage", colors.drawBackground, colors.onDrawBackground)
                Swatch("stage 2", colors.drawBackground, colors.onDrawMuted)
                Swatch("rec", colors.drawBackground, colors.recording)
                Swatch("primary", colors.onBackground, colors.surface)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShapeSample("card", GiveawayTheme.shapes.card)
                ShapeSample("button", GiveawayTheme.shapes.button)
                ShapeSample("chip", GiveawayTheme.shapes.chip)
                ShapeSample("sheet", GiveawayTheme.shapes.sheet)
            }
        }
    }

    @Composable
    private fun TypeRow(label: String, style: TextStyle, text: String) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = GiveawayTheme.typography.captionSmall,
                color = GiveawayTheme.colors.onMuted,
                modifier = Modifier.width(84.dp),
            )
            Text(text, style = style, color = GiveawayTheme.colors.onBackground, maxLines = 1)
        }
    }

    @Composable
    private fun Swatch(name: String, fill: Color, content: Color) {
        Box(
            modifier = Modifier
                .size(width = 80.dp, height = 44.dp)
                .background(fill, GiveawayTheme.shapes.button)
                .border(GiveawayDimens.cardBorder, GiveawayTheme.colors.outline, GiveawayTheme.shapes.button),
            contentAlignment = Alignment.Center,
        ) {
            Text(name, style = GiveawayTheme.typography.caption, color = content)
        }
    }

    @Composable
    private fun ShapeSample(name: String, shape: Shape) {
        Box(
            modifier = Modifier
                .size(width = 80.dp, height = 44.dp)
                .background(GiveawayTheme.colors.surface, shape)
                .border(GiveawayDimens.cardBorder, GiveawayTheme.colors.outline, shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(name, style = GiveawayTheme.typography.captionSmall, color = GiveawayTheme.colors.onMuted)
        }
    }
}
