package app.giveaway.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.giveaway.core.designsystem.GiveawayDimens
import app.giveaway.core.designsystem.GiveawayTheme

private val ButtonContentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)

/**
 * Main action: 56 dp, near-black fill, white text. On the dark draw stage ([onStage], on by default inside a
 * [DrawStage]) it uses the accent fill.
 * Height is a minimum so text at 200% font size grows the button instead of clipping.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onStage: Boolean = LocalOnDrawStage.current,
) {
    val colors = GiveawayTheme.colors
    val container = if (onStage) colors.accent else colors.onBackground
    val content = if (onStage) colors.onAccent else colors.surface
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = GiveawayDimens.primaryButtonHeight),
        enabled = enabled,
        shape = GiveawayTheme.shapes.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = if (onStage) colors.onDrawMuted else colors.surfaceMuted,
            disabledContentColor = if (onStage) colors.drawBackground else colors.onMuted,
        ),
        contentPadding = ButtonContentPadding,
    ) {
        Text(text, style = GiveawayTheme.typography.title, textAlign = TextAlign.Center)
    }
}

/** Secondary action: 52 dp with a 1.5 dp near-black outline. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = GiveawayTheme.colors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = GiveawayDimens.secondaryButtonHeight),
        enabled = enabled,
        shape = GiveawayTheme.shapes.button,
        border = BorderStroke(
            width = GiveawayDimens.secondaryButtonBorder,
            color = if (enabled) colors.onBackground else colors.outline,
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = colors.onBackground,
            disabledContentColor = colors.onMuted,
        ),
        contentPadding = ButtonContentPadding,
    ) {
        Text(text, style = GiveawayTheme.typography.title, textAlign = TextAlign.Center)
    }
}
