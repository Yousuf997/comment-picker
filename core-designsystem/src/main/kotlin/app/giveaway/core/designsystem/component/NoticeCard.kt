package app.giveaway.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.designsystem.R

enum class NoticeTone { Info, Warning, Success }

/**
 * Highlighted message card: an icon, a title, a body and an optional text action. Used for requirements
 * ("Business or Creator account needed") and recoverable problems (network errors, partial imports).
 */
@Composable
fun NoticeCard(
    title: String,
    body: String?,
    tone: NoticeTone,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = GiveawayTheme.colors
    val (container: Color, content: Color, icon: Int) = when (tone) {
        NoticeTone.Info -> Triple(colors.infoContainer, colors.info, R.drawable.ic_info)
        NoticeTone.Warning -> Triple(colors.accentSoft, colors.accentOnSoft, R.drawable.ic_warning)
        NoticeTone.Success -> Triple(colors.successContainer, colors.success, R.drawable.ic_task_alt)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(container, GiveawayTheme.shapes.card)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Decorative: the title carries the meaning.
        Icon(painterResource(icon), contentDescription = null, tint = content)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = GiveawayTheme.typography.bodyStrong, color = content)
            if (body != null) Text(body, style = GiveawayTheme.typography.body, color = content)
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel, style = GiveawayTheme.typography.bodyStrong, color = content)
                }
            }
        }
    }
}
