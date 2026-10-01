package app.giveaway.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private const val DISPLAY_OPTICAL_SIZE = 36f

// Bundled in res/font (spec: no runtime download). Bricolage, Manrope and JetBrains Mono are variable fonts.
private fun variable(resId: Int, weight: FontWeight, vararg extra: FontVariation.Setting) =
    Font(resId, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight), *extra))

private val BricolageGrotesque = FontFamily(
    variable(R.font.bricolage_grotesque, FontWeight.ExtraBold, FontVariation.Setting("opsz", DISPLAY_OPTICAL_SIZE)),
)

private val Manrope = FontFamily(
    variable(R.font.manrope, FontWeight.Normal),
    variable(R.font.manrope, FontWeight.Medium),
    variable(R.font.manrope, FontWeight.SemiBold),
    variable(R.font.manrope, FontWeight.Bold),
)

private val JetBrainsMono = FontFamily(
    variable(R.font.jetbrains_mono, FontWeight.Medium),
)

/** Arabic face matching Manrope's role; it also covers Latin, so handles render in the same face. */
private val IbmPlexSansArabic = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_arabic_bold, FontWeight.Bold),
)

/** Type scale from the spec. Overline text must also be uppercased by the caller. */
@Immutable
data class GiveawayTypography(
    val displayLarge: TextStyle,
    val display: TextStyle,
    val titleLarge: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val caption: TextStyle,
    val captionSmall: TextStyle,
    val overline: TextStyle,
    val codeLarge: TextStyle,
    val code: TextStyle,
    val codeSmall: TextStyle,
)

private fun style(
    family: FontFamily,
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    tracking: TextUnit = TextUnit.Unspecified,
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking,
)

/** Codes and hashes stay in JetBrains Mono and left to right in both languages (spec: Localization). */
private fun code(size: Int, lineHeight: Int) =
    style(JetBrainsMono, FontWeight.Medium, size, lineHeight).copy(textDirection = TextDirection.Ltr)

private fun typography(display: FontFamily, displayWeight: FontWeight, text: FontFamily) = GiveawayTypography(
    displayLarge = style(display, displayWeight, size = 38, lineHeight = 42, tracking = (-0.02).em),
    display = style(display, displayWeight, size = 30, lineHeight = 34, tracking = (-0.02).em),
    titleLarge = style(text, FontWeight.Bold, size = 17, lineHeight = 22),
    title = style(text, FontWeight.Bold, size = 16, lineHeight = 21),
    body = style(text, FontWeight.Normal, size = 15, lineHeight = 22),
    bodyStrong = style(text, FontWeight.SemiBold, size = 15, lineHeight = 22),
    caption = style(text, FontWeight.SemiBold, size = 13, lineHeight = 18),
    captionSmall = style(text, FontWeight.SemiBold, size = 12, lineHeight = 16),
    overline = style(text, FontWeight.Bold, size = 12, lineHeight = 16, tracking = 0.06.em),
    codeLarge = code(size = 16, lineHeight = 22),
    code = code(size = 13, lineHeight = 18),
    codeSmall = code(size = 11, lineHeight = 15),
)

val LatinGiveawayTypography = typography(BricolageGrotesque, FontWeight.ExtraBold, Manrope)

/** Plex Sans Arabic's heaviest bundled weight is Bold, used in place of the 800 display weight. */
val ArabicGiveawayTypography = typography(IbmPlexSansArabic, FontWeight.Bold, IbmPlexSansArabic)
