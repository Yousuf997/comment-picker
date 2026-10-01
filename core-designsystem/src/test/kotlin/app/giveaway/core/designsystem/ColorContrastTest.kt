package app.giveaway.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Spec: text contrast at least 4.5:1 in light and dark themes. */
class ColorContrastTest {

    @Test
    fun lightThemeTextPairsMeetAa() = assertAllPairs(LightGiveawayColors)

    @Test
    fun darkThemeTextPairsMeetAa() = assertAllPairs(DarkGiveawayColors)

    @Test
    fun contrastRatioMatchesWcagReferenceValues() {
        assertTrue(contrast(Color.Black, Color.White) in 20.99..21.01)
        assertTrue(contrast(Color.White, Color.White) in 0.99..1.01)
    }

    @Test
    fun lightThemeGraphicsMeetNonTextContrast() =
        assertAllPairs(LightGiveawayColors, ::graphicPairs, MIN_GRAPHIC_CONTRAST)

    @Test
    fun darkThemeGraphicsMeetNonTextContrast() =
        assertAllPairs(DarkGiveawayColors, ::graphicPairs, MIN_GRAPHIC_CONTRAST)

    private fun assertAllPairs(
        colors: GiveawayColors,
        pairs: (GiveawayColors) -> List<Triple<String, Color, Color>> = ::textPairs,
        minimum: Double = MIN_TEXT_CONTRAST,
    ) {
        val failures = pairs(colors)
            .map { (name, fg, bg) -> name to contrast(fg, bg) }
            .filter { (_, ratio) -> ratio < minimum }
            .map { (name, ratio) -> "$name: ${"%.2f".format(ratio)}:1" }
        val message = "Pairs under $minimum:1 (dark=${colors.isDark}):\n" + failures.joinToString("\n")
        assertTrue(message, failures.isEmpty())
    }

    /** Non-text UI parts that carry meaning (WCAG 1.4.11): progress, selection, draw ring, switches, REC dot. */
    private fun graphicPairs(c: GiveawayColors) = listOf(
        Triple("accent on background", c.accent, c.background),
        Triple("accent on surface", c.accent, c.surface),
        Triple("accent on drawBackground", c.accent, c.drawBackground),
        Triple("switch track (onBackground) on surface", c.onBackground, c.surface),
        Triple("recording on drawBackground", c.recording, c.drawBackground),
    )

    /** Every foreground/background combination the UI uses for text or icons that carry meaning. */
    private fun textPairs(c: GiveawayColors) = listOf(
        Triple("onBackground on background", c.onBackground, c.background),
        Triple("onBackground on surface", c.onBackground, c.surface),
        Triple("onBackground on surfaceMuted", c.onBackground, c.surfaceMuted),
        Triple("onMuted on background", c.onMuted, c.background),
        Triple("onMuted on surface", c.onMuted, c.surface),
        Triple("onMuted on surfaceMuted", c.onMuted, c.surfaceMuted),
        Triple("surface on onBackground (primary button)", c.surface, c.onBackground),
        Triple("accent on background", c.accent, c.background),
        Triple("accent on surface", c.accent, c.surface),
        Triple("onAccent on accent", c.onAccent, c.accent),
        Triple("accentOnSoft on accentSoft", c.accentOnSoft, c.accentSoft),
        Triple("success on successContainer", c.success, c.successContainer),
        Triple("info on infoContainer", c.info, c.infoContainer),
        Triple("danger on background", c.danger, c.background),
        Triple("danger on surface", c.danger, c.surface),
        Triple("onDanger on danger", c.onDanger, c.danger),
        Triple("onDrawBackground on drawBackground", c.onDrawBackground, c.drawBackground),
        Triple("onDrawMuted on drawBackground", c.onDrawMuted, c.drawBackground),
    )

    private companion object {
        const val MIN_TEXT_CONTRAST = 4.5
        const val MIN_GRAPHIC_CONTRAST = 3.0

        fun contrast(a: Color, b: Color): Double {
            val la = luminance(a)
            val lb = luminance(b)
            return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
        }

        /** WCAG 2.x relative luminance from sRGB channels. */
        fun luminance(c: Color): Double {
            fun channel(v: Float): Double = if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
            return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
        }
    }
}
