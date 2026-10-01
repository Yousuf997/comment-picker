package app.giveaway.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** S5 Settings. */
@Serializable
data object SettingsRoute

/** Open-source licenses, from S5. */
@Serializable
data object LicensesRoute

fun NavGraphBuilder.settingsScreen(actions: SettingsActions) {
    composable<SettingsRoute> { SettingsScreen(actions) }
}

fun NavGraphBuilder.licensesScreen(onBack: () -> Unit) {
    composable<LicensesRoute> { LicensesScreen(onBack) }
}
