package app.giveaway.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.giveaway.core.designsystem.GiveawayTheme

private val SegmentMinHeight = 44.dp

/**
 * Segmented control on the muted surface (spec: Colors, surfaceMuted). The selected segment is raised on the card
 * surface; TalkBack reads each segment as a tab with its selected state.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GiveawayTheme.colors
    val shape = GiveawayTheme.shapes.button
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surfaceMuted, shape)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = SegmentMinHeight)
                    .clip(shape)
                    .background(if (selected) colors.surface else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = GiveawayTheme.typography.bodyStrong,
                    color = if (selected) colors.onBackground else colors.onMuted,
                    maxLines = 1,
                )
            }
        }
    }
}
