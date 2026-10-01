package app.giveaway.core.designsystem

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Corner shapes from the spec: cards 18 dp, buttons 16 dp, chips fully rounded, sheets 28 dp on top. */
@Immutable
data class GiveawayShapes(
    val card: Shape = RoundedCornerShape(18.dp),
    val button: Shape = RoundedCornerShape(16.dp),
    val chip: Shape = CircleShape,
    val sheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
)

internal val MaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Spacing and size tokens on the 8 dp grid. */
object GiveawayDimens {
    val grid = 8.dp
    val screenPadding = 20.dp
    val screenPaddingWide = 24.dp
    val minTouchTarget = 48.dp
    val minTouchTargetHard = 44.dp
    val primaryButtonHeight = 56.dp
    val secondaryButtonHeight = 52.dp
    val secondaryButtonBorder = 1.5.dp
    val cardBorder = 1.dp
    val settingsRowHeight = 50.dp
    val stepperButton = 40.dp
    const val SHEET_SCRIM_ALPHA = 0.62f
}
