package app.giveaway.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.giveaway.core.designsystem.GiveawayDimens
import app.giveaway.core.designsystem.GiveawayTheme

/** One forward action on a [PlaceholderScreen]. */
data class PlaceholderAction(val label: String, val primary: Boolean = true, val onClick: () -> Unit)

/**
 * Temporary scaffold for screens that are not built yet (build plan F-08). It shows the screen's title and the
 * actions that lead to the next screens, so the whole flow can be walked. Each screen task replaces it.
 * [screenId] is the spec's number (S1 to S15) and doubles as a test tag. [onStage] uses the dark draw stage.
 */
@Composable
fun PlaceholderScreen(
    screenId: String,
    title: String,
    actions: List<PlaceholderAction>,
    modifier: Modifier = Modifier,
    onStage: Boolean = false,
) {
    val colors = GiveawayTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (onStage) colors.drawBackground else colors.background)
            .safeDrawingPadding()
            .padding(GiveawayDimens.screenPadding)
            .testTag("screen:$screenId"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = GiveawayTheme.typography.display,
            color = if (onStage) colors.onDrawBackground else colors.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        actions.forEach { action ->
            if (action.primary) {
                PrimaryButton(
                    action.label,
                    onClick = action.onClick,
                    onStage = onStage,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                SecondaryButton(action.label, onClick = action.onClick, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
