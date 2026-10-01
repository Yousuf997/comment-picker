package app.giveaway.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.giveaway.core.designsystem.GiveawayDimens
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.designsystem.R

/**
 * 50 dp settings row: label at the start, optional value and a chevron at the end. [destructive] colors the label red.
 */
@Composable
fun SettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    destructive: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = GiveawayTheme.colors
    RowLayout(
        modifier = if (onClick != null) modifier.clickable(role = Role.Button, onClick = onClick) else modifier,
        label = label,
        labelColor = if (destructive) colors.danger else colors.onBackground,
    ) {
        if (value != null) {
            Text(value, style = GiveawayTheme.typography.body, color = colors.onMuted, maxLines = 1)
        }
        if (onClick != null) {
            // Decorative: the row's label and value already describe the action.
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = colors.onMuted)
        }
    }
}

/** Settings row with a switch; the whole row toggles, so it is one control for TalkBack. */
@Composable
fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    RowLayout(
        modifier = modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
        label = label,
        labelColor = if (enabled) GiveawayTheme.colors.onBackground else GiveawayTheme.colors.onMuted,
    ) {
        GiveawaySwitch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun RowLayout(
    modifier: Modifier,
    label: String,
    labelColor: Color,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = GiveawayDimens.settingsRowHeight)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = GiveawayTheme.typography.body, color = labelColor, modifier = Modifier.weight(1f))
        trailing()
    }
}
