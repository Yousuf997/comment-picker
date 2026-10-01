package app.giveaway.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import app.giveaway.core.designsystem.GiveawayTheme

/** True inside a [DrawStage]; [PrimaryButton] then uses the accent fill by default. */
val LocalOnDrawStage = staticCompositionLocalOf { false }

/**
 * The dark stage behind S11 and S12, in both themes (spec: drawBackground). Text defaults to the stage's light
 * color and primary buttons switch to the accent fill.
 */
@Composable
fun DrawStage(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = GiveawayTheme.colors
    CompositionLocalProvider(
        LocalOnDrawStage provides true,
        LocalContentColor provides colors.onDrawBackground,
    ) {
        Box(modifier = modifier.background(colors.drawBackground), content = content)
    }
}
