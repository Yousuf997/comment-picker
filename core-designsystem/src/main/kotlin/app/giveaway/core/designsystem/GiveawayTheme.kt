package app.giveaway.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration

private val LocalGiveawayColors = staticCompositionLocalOf { LightGiveawayColors }
private val LocalGiveawayTypography = staticCompositionLocalOf { LatinGiveawayTypography }
private val LocalGiveawayShapes = staticCompositionLocalOf { GiveawayShapes() }

/** Brand theme. Dynamic color stays off so the brand looks the same on every device. */
@Composable
fun GiveawayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkGiveawayColors else LightGiveawayColors
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val typography = if (isArabic) ArabicGiveawayTypography else LatinGiveawayTypography
    CompositionLocalProvider(
        LocalGiveawayColors provides colors,
        LocalGiveawayTypography provides typography,
        LocalGiveawayShapes provides GiveawayShapes(),
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterial(),
            typography = typography.toMaterial(),
            shapes = MaterialShapes,
            content = content,
        )
    }
}

object GiveawayTheme {
    val colors: GiveawayColors
        @Composable @ReadOnlyComposable get() = LocalGiveawayColors.current
    val typography: GiveawayTypography
        @Composable @ReadOnlyComposable get() = LocalGiveawayTypography.current
    val shapes: GiveawayShapes
        @Composable @ReadOnlyComposable get() = LocalGiveawayShapes.current
}

/** Maps brand tokens onto Material roles so stock Material 3 components pick them up. Primary is near-black. */
private fun GiveawayColors.toMaterial(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = onBackground,
        onPrimary = surface,
        primaryContainer = accentSoft,
        onPrimaryContainer = accentOnSoft,
        secondary = accent,
        onSecondary = onAccent,
        tertiary = info,
        onTertiary = surface,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onBackground,
        surfaceVariant = surfaceMuted,
        onSurfaceVariant = onMuted,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surfaceMuted,
        outline = outline,
        outlineVariant = outline,
        error = danger,
        onError = onDanger,
        scrim = drawBackground,
    )
}

private fun GiveawayTypography.toMaterial() = Typography(
    displayLarge = displayLarge,
    displayMedium = display,
    displaySmall = display,
    headlineLarge = display,
    headlineMedium = titleLarge,
    headlineSmall = titleLarge,
    titleLarge = titleLarge,
    titleMedium = title,
    titleSmall = bodyStrong,
    bodyLarge = body,
    bodyMedium = body,
    bodySmall = caption,
    labelLarge = title,
    labelMedium = caption,
    labelSmall = captionSmall,
)
