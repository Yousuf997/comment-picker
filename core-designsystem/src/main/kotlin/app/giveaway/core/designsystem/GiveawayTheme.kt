package app.giveaway.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** Placeholder theme; F-04 adds the spec's color tokens, fonts and shapes. Dynamic color stays off. */
@Composable
fun GiveawayTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
