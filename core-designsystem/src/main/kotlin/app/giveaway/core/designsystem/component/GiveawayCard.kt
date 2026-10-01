package app.giveaway.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.giveaway.core.designsystem.GiveawayDimens
import app.giveaway.core.designsystem.GiveawayTheme

private val CardPadding = PaddingValues(16.dp)

/** White card with a 1 dp outline and 18 dp corners. Clickable when [onClick] is set. */
@Composable
fun GiveawayCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = CardPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = GiveawayTheme.colors
    val border = BorderStroke(GiveawayDimens.cardBorder, colors.outline)
    val body: @Composable () -> Unit = { Column(Modifier.padding(contentPadding), content = content) }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = GiveawayTheme.shapes.card,
            color = colors.surface,
            contentColor = colors.onBackground,
            border = border,
            content = body,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = GiveawayTheme.shapes.card,
            color = colors.surface,
            contentColor = colors.onBackground,
            border = border,
            content = body,
        )
    }
}
