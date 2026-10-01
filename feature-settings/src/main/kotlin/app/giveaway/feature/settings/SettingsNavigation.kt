package app.giveaway.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** S5 Settings. */
@Serializable
data object SettingsRoute

/** Encrypted backup export, from S5 or the S4 reminder. */
@Serializable
data object BackupRoute

/** Restore from a backup, from S5. */
@Serializable
data object RestoreRoute

/** Open-source licenses, from S5. */
@Serializable
data object LicensesRoute

fun NavGraphBuilder.settingsScreen(actions: SettingsActions) {
    composable<SettingsRoute> { SettingsScreen(actions) }
}

fun NavGraphBuilder.backupScreen(onBack: () -> Unit) {
    composable<BackupRoute> { BackupScreen(onBack) }
}

/** [onRestored] runs once the data is replaced; the user then signs in to Instagram again. */
fun NavGraphBuilder.restoreScreen(onBack: () -> Unit, onRestored: () -> Unit) {
    composable<RestoreRoute> { RestoreScreen(onBack, onRestored) }
}

fun NavGraphBuilder.licensesScreen(onBack: () -> Unit) {
    composable<LicensesRoute> { LicensesScreen(onBack) }
}
