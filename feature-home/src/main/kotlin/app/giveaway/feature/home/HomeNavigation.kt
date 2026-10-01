package app.giveaway.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.giveaway.core.designsystem.component.PlaceholderAction
import app.giveaway.core.designsystem.component.PlaceholderScreen
import kotlinx.serialization.Serializable

/** S4 Home. */
@Serializable
data object HomeRoute

fun NavGraphBuilder.homeScreen(onNewGiveaway: () -> Unit, onOpenSettings: () -> Unit) {
    composable<HomeRoute> { HomeScreen(onNewGiveaway, onOpenSettings) }
}

@Composable
internal fun HomeScreen(onNewGiveaway: () -> Unit, onOpenSettings: () -> Unit) = PlaceholderScreen(
    screenId = "S4",
    title = stringResource(R.string.home_title),
    actions = listOf(
        PlaceholderAction(stringResource(R.string.home_new_giveaway), onClick = onNewGiveaway),
        PlaceholderAction(stringResource(R.string.home_settings), primary = false, onClick = onOpenSettings),
    ),
)
