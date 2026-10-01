package app.giveaway.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.giveaway.core.designsystem.GiveawayDimens
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.designsystem.R

private val ValueMinWidth = 40.dp

/**
 * Minus and plus buttons (40 dp visually, 48 dp touch target) with the value between. [label] names the quantity
 * for TalkBack, e.g. "winners", and the value is announced when it changes.
 */
@Composable
fun Stepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = GiveawayTheme.colors
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StepButton(
            icon = R.drawable.ic_remove,
            description = stringResource(R.string.stepper_decrease, label),
            enabled = value > range.first,
            onClick = { onValueChange(value - 1) },
        )
        Text(
            text = value.toString(),
            modifier = Modifier
                .widthIn(min = ValueMinWidth)
                .semantics { liveRegion = LiveRegionMode.Polite },
            style = GiveawayTheme.typography.titleLarge,
            color = colors.onBackground,
            textAlign = TextAlign.Center,
        )
        StepButton(
            icon = R.drawable.ic_add,
            description = stringResource(R.string.stepper_increase, label),
            enabled = value < range.last,
            onClick = { onValueChange(value + 1) },
        )
    }
}

@Composable
private fun StepButton(icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = GiveawayTheme.colors
    // Material's icon buttons already pad their touch target to 48 dp around the 40 dp visual.
    OutlinedIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(GiveawayDimens.stepperButton),
        shape = CircleShape,
        border = BorderStroke(GiveawayDimens.cardBorder, if (enabled) colors.onBackground else colors.outline),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = description,
            tint = if (enabled) colors.onBackground else colors.onMuted,
        )
    }
}
