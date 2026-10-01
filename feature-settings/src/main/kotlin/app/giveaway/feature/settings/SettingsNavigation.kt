package app.giveaway.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.giveaway.core.designsystem.component.PlaceholderAction
import app.giveaway.core.designsystem.component.PlaceholderScreen
import kotlinx.serialization.Serializable

/** S5 Settings. */
@Serializable
data object SettingsRoute

fun NavGraphBuilder.settingsScreen(onBack: () -> Unit) {
    composable<SettingsRoute> { SettingsScreen(onBack) }
}

@Composable
internal fun SettingsScreen(onBack: () -> Unit) = PlaceholderScreen(
    screenId = "S5",
    title = stringResource(R.string.settings_title),
    actions = listOf(PlaceholderAction(stringResource(R.string.settings_done), primary = false, onClick = onBack)),
)
