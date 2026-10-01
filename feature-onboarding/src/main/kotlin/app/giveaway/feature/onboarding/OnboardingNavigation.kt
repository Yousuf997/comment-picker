package app.giveaway.feature.onboarding

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** S1 Welcome. */
@Serializable
data object WelcomeRoute

/** S2 Connect Instagram. */
@Serializable
data object ConnectInstagramRoute

/** S3 App lock setup. */
@Serializable
data object AppLockSetupRoute

fun NavGraphBuilder.welcomeScreen(onGetStarted: () -> Unit) {
    composable<WelcomeRoute> { WelcomeScreen(onGetStarted) }
}

fun NavGraphBuilder.connectInstagramScreen(onConnected: () -> Unit) {
    composable<ConnectInstagramRoute> { ConnectInstagramScreen(onConnected) }
}

fun NavGraphBuilder.appLockSetupScreen(onDone: () -> Unit) {
    composable<AppLockSetupRoute> { AppLockSetupScreen(onDone) }
}
