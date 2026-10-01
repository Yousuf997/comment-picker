package app.giveaway.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Brand color tokens (spec: Design system). One burnt-orange accent is reserved for progress, the draw and the winner.
 * Every text/background pair used by the UI is checked for 4.5:1 contrast in ColorContrastTest.
 */
@Immutable
data class GiveawayColors(
    val background: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val onBackground: Color,
    val onMuted: Color,
    val outline: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val accentOnSoft: Color,
    val success: Color,
    val successContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val danger: Color,
    val onDanger: Color,
    /** REC dot only, never text. */
    val recording: Color,
    /** Dark stage for S11 and S12 in both themes. */
    val drawBackground: Color,
    val onDrawBackground: Color,
    val onDrawMuted: Color,
    val isDark: Boolean,
)

/** Values from the spec's light theme table. */
val LightGiveawayColors = GiveawayColors(
    background = Color(0xFFF6F3EE),
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFECE7DF),
    onBackground = Color(0xFF17171C),
    onMuted = Color(0xFF5B5A57),
    outline = Color(0xFFE4DFD6),
    accent = Color(0xFFC2410C),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFFBE3D6),
    accentOnSoft = Color(0xFF9A3412),
    success = Color(0xFF1F6F4A),
    successContainer = Color(0xFFDDEFE4),
    info = Color(0xFF1E3F99),
    infoContainer = Color(0xFFE3EAFB),
    danger = Color(0xFFB42318),
    onDanger = Color(0xFFFFFFFF),
    recording = Color(0xFFF04438),
    drawBackground = Color(0xFF17171C),
    onDrawBackground = Color(0xFFFFFFFF),
    onDrawMuted = Color(0xFFB4B4BC),
    isDark = false,
)

/**
 * Derived from the light theme: the same warm neutrals inverted, and lighter tints of the accent and status colors
 * so they keep 4.5:1 on dark grounds. Colored fills switch to dark text.
 */
val DarkGiveawayColors = GiveawayColors(
    background = Color(0xFF151413),
    surface = Color(0xFF1F1D1B),
    surfaceMuted = Color(0xFF2A2724),
    onBackground = Color(0xFFF3EFE8),
    onMuted = Color(0xFFB0AAA1),
    outline = Color(0xFF3A3632),
    accent = Color(0xFFF5894D),
    onAccent = Color(0xFF17171C),
    accentSoft = Color(0xFF4A2414),
    accentOnSoft = Color(0xFFFDBA8C),
    success = Color(0xFF7BD3A4),
    successContainer = Color(0xFF16382A),
    info = Color(0xFFA9BEF7),
    infoContainer = Color(0xFF1C2A55),
    danger = Color(0xFFF97066),
    onDanger = Color(0xFF17171C),
    recording = Color(0xFFF04438),
    drawBackground = Color(0xFF17171C),
    onDrawBackground = Color(0xFFFFFFFF),
    onDrawMuted = Color(0xFFB4B4BC),
    isDark = true,
)
