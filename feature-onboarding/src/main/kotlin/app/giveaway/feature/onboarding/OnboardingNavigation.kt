package app.giveaway.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.giveaway.core.designsystem.component.PlaceholderAction
import app.giveaway.core.designsystem.component.PlaceholderScreen
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

@Composable
internal fun AppLockSetupScreen(onDone: () -> Unit) = PlaceholderScreen(
    screenId = "S3",
    title = stringResource(R.string.app_lock_title),
    actions = listOf(
        PlaceholderAction(stringResource(R.string.app_lock_use_biometrics), onClick = onDone),
        PlaceholderAction(stringResource(R.string.app_lock_set_pin), primary = false, onClick = onDone),
        PlaceholderAction(stringResource(R.string.app_lock_skip), primary = false, onClick = onDone),
    ),
)
