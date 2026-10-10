package app.giveaway.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.giveaway.core.designsystem.GiveawayTheme

/**
 * Small fully rounded status label. It always carries words, so color is never the only signal (spec: Accessibility).
 */
@Composable
fun StatusChip(
    text: String,
    tone: ChipTone,
    modifier: Modifier = Modifier,
) {
    val colors = GiveawayTheme.colors
    val (container, content) = when (tone) {
        ChipTone.Success -> colors.successContainer to colors.success
        ChipTone.Info -> colors.infoContainer to colors.info
        ChipTone.Neutral -> colors.surfaceMuted to colors.onMuted
        ChipTone.Accent -> colors.accentSoft to colors.accentOnSoft
        ChipTone.Strong -> colors.accent to colors.onAccent
    }
    Text(
        text = text,
        modifier = modifier
            .background(container, GiveawayTheme.shapes.chip)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = GiveawayTheme.typography.captionSmall,
        color = content,
        maxLines = 1,
    )
}

enum class ChipTone {
    Success,
    Info,
    Neutral,
    Accent,

    /** Filled with the accent: the one thing that's due now, e.g. a giveaway whose draw is due. */
    Strong,
}
