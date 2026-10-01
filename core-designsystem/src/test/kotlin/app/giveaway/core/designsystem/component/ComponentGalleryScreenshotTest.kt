package app.giveaway.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.giveaway.core.designsystem.GiveawayDimens
import app.giveaway.core.designsystem.GiveawayTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Spec F-05: every component in English and Arabic, light and dark, at 100% and 200% font size. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h1600dp-xhdpi")
class ComponentGalleryScreenshotTest(private val dark: Boolean, private val fontScale: Float) {

    @Test
    fun english() = capture("en", EnglishText)

    @Test
    @Config(qualifiers = "ar-w390dp-h1600dp-xhdpi")
    fun arabic() = capture("ar", ArabicText)

    private fun capture(language: String, text: GalleryText) {
        val theme = if (dark) "dark" else "light"
        val scale = "${(fontScale * 100).toInt()}"
        captureRoboImage("src/test/screenshots/components_${language}_${theme}_$scale.png") {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                GiveawayTheme(darkTheme = dark) { Gallery(text) }
            }
        }
    }

    @Composable
    private fun Gallery(text: GalleryText) {
        val colors = GiveawayTheme.colors
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
                .padding(GiveawayDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrimaryButton(text.primary, onClick = {}, modifier = Modifier.fillMaxWidth())
            PrimaryButton(text.primary, onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
            SecondaryButton(text.secondary, onClick = {}, modifier = Modifier.fillMaxWidth())
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.drawBackground, GiveawayTheme.shapes.card)
                    .padding(16.dp),
            ) {
                PrimaryButton(text.draw, onClick = {}, onStage = true, modifier = Modifier.fillMaxWidth())
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatusChip(text.valid, ChipTone.Success)
                StatusChip(text.waiting, ChipTone.Info)
                StatusChip(text.draft, ChipTone.Neutral)
                StatusChip(text.winner, ChipTone.Accent)
            }
            GiveawayCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                SettingsRow(text.lockAfter, value = text.oneMinute, onClick = {})
                HorizontalDivider(color = colors.outline)
                SettingsSwitchRow(text.record, checked = true, onCheckedChange = {})
                HorizontalDivider(color = colors.outline)
                SettingsSwitchRow(text.screenshots, checked = false, onCheckedChange = {})
                HorizontalDivider(color = colors.outline)
                SettingsRow(text.deleteAll, destructive = true, onClick = {})
            }
            GiveawayCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text.winners, style = GiveawayTheme.typography.body, modifier = Modifier.weight(1f))
                    Stepper(value = 3, onValueChange = {}, range = 1..50, label = text.winners)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text.alternates, style = GiveawayTheme.typography.body, modifier = Modifier.weight(1f))
                    Stepper(value = 0, onValueChange = {}, range = 0..20, label = text.alternates)
                }
            }
        }
    }

    private data class GalleryText(
        val primary: String,
        val secondary: String,
        val draw: String,
        val valid: String,
        val waiting: String,
        val draft: String,
        val winner: String,
        val lockAfter: String,
        val oneMinute: String,
        val record: String,
        val screenshots: String,
        val deleteAll: String,
        val winners: String,
        val alternates: String,
    )

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "dark={0} fontScale={1}")
        fun parameters() = listOf(
            arrayOf<Any>(false, 1f),
            arrayOf<Any>(true, 1f),
            arrayOf<Any>(false, 2f),
            arrayOf<Any>(true, 2f),
        )

        private val EnglishText = GalleryText(
            primary = "Continue",
            secondary = "Run a test draw first",
            draw = "Draw winners",
            valid = "Valid",
            waiting = "Waiting for deadline",
            draft = "Draft",
            winner = "Winner",
            lockAfter = "Lock after",
            oneMinute = "1 minute",
            record = "Record draws by default",
            screenshots = "Block screenshots",
            deleteAll = "Delete everything on this phone",
            winners = "Winners",
            alternates = "Alternates",
        )

        private val ArabicText = GalleryText(
            primary = "متابعة",
            secondary = "إجراء سحب تجريبي أولًا",
            draw = "اختيار الفائزين",
            valid = "صالح",
            waiting = "بانتظار الموعد النهائي",
            draft = "مسودة",
            winner = "فائز",
            lockAfter = "القفل بعد",
            oneMinute = "دقيقة واحدة",
            record = "تسجيل السحوبات افتراضيًا",
            screenshots = "منع لقطات الشاشة",
            deleteAll = "حذف كل شيء على هذا الهاتف",
            winners = "الفائزون",
            alternates = "البدلاء",
        )
    }
}
