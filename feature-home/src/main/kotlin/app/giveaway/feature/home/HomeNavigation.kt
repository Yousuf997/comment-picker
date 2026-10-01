package app.giveaway.feature.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** S4 Home. */
@Serializable
data object HomeRoute

fun NavGraphBuilder.homeScreen(actions: HomeActions) {
    composable<HomeRoute> { HomeScreen(actions) }
}
