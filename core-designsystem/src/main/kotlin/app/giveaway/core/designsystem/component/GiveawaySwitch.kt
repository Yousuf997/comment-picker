package app.giveaway.core.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.giveaway.core.designsystem.GiveawayTheme

private val TrackWidth = 48.dp
private val TrackHeight = 28.dp
private val ThumbSize = 22.dp
private val ThumbInset = 3.dp
private const val THUMB_ANIMATION_MS = 200

/**
 * 48 x 28 dp switch, near-black track when on. Pass `null` for [onCheckedChange] when a parent row handles the
 * toggle, so the row is the single focusable control for TalkBack.
 */
@Composable
fun GiveawaySwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = GiveawayTheme.colors
    // On: light thumb on a near-black track. Off: muted thumb on a muted track. Thumb position also shows the state.
    val thumbColor = when {
        !enabled -> colors.outline
        checked -> colors.surface
        else -> colors.onMuted
    }
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) TrackWidth - ThumbSize - ThumbInset * 2 else 0.dp,
        animationSpec = tween(THUMB_ANIMATION_MS),
        label = "thumb",
    )
    val interaction = if (onCheckedChange != null) {
        Modifier
            .minimumInteractiveComponentSize()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
    } else {
        Modifier
    }
    Box(modifier = modifier.then(interaction), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(TrackWidth, TrackHeight)
                .background(if (checked) colors.onBackground else colors.surfaceMuted, CircleShape)
                .padding(ThumbInset),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .offset { IntOffset(thumbOffset.roundToPx(), 0) }
                    .size(ThumbSize)
                    .background(thumbColor, CircleShape),
            )
        }
    }
}
