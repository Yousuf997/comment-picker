package app.giveaway.feature.onboarding

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

/** S1 Welcome. */
@Serializable
data object WelcomeRoute

/** S2 Connect Instagram: during onboarding, or to sign in again later ([reconnect]). */
@Serializable
data class ConnectInstagramRoute(val reconnect: Boolean = false)

/** S3 App lock setup: during onboarding, or from S5 when app lock is switched on ([fromSettings]). */
@Serializable
data class AppLockSetupRoute(val fromSettings: Boolean = false)

fun NavGraphBuilder.welcomeScreen(onGetStarted: () -> Unit) {
    composable<WelcomeRoute> { WelcomeScreen(onGetStarted) }
}

fun NavGraphBuilder.connectInstagramScreen(onConnected: (reconnect: Boolean) -> Unit) {
    composable<ConnectInstagramRoute> { entry ->
        val reconnect = entry.toRoute<ConnectInstagramRoute>().reconnect
        ConnectInstagramScreen(onConnected = { onConnected(reconnect) })
    }
}

fun NavGraphBuilder.appLockSetupScreen(onDone: (fromSettings: Boolean) -> Unit) {
    composable<AppLockSetupRoute> { entry ->
        val fromSettings = entry.toRoute<AppLockSetupRoute>().fromSettings
        AppLockSetupScreen(onDone = { onDone(fromSettings) })
    }
}
