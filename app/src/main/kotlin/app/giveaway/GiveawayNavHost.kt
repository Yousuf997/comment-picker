package app.giveaway

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.giveaway.core.data.giveaway.WizardProgress
import app.giveaway.core.designsystem.component.LocalWizardSteps
import app.giveaway.core.designsystem.component.WizardSteps
import app.giveaway.feature.create.ChangePostRoute
import app.giveaway.feature.create.EditRulesRoute
import app.giveaway.feature.create.ImportCommentsRoute
import app.giveaway.feature.create.PickPostRoute
import app.giveaway.feature.create.ReviewEntriesRoute
import app.giveaway.feature.create.changePostScreen
import app.giveaway.feature.create.editRulesScreen
import app.giveaway.feature.create.importCommentsScreen
import app.giveaway.feature.create.pickPostScreen
import app.giveaway.feature.create.reviewEntriesScreen
import app.giveaway.feature.create.setRulesScreen
import app.giveaway.feature.draw.CertificateRoute
import app.giveaway.feature.draw.DrawRoute
import app.giveaway.feature.draw.DrawingRoute
import app.giveaway.feature.draw.WinnersRoute
import app.giveaway.feature.draw.certificateScreen
import app.giveaway.feature.draw.drawScreen
import app.giveaway.feature.draw.drawingScreen
import app.giveaway.feature.draw.winnersScreen
import app.giveaway.feature.home.GiveawayDestination
import app.giveaway.feature.home.HomeActions
import app.giveaway.feature.home.HomeRoute
import app.giveaway.feature.home.homeScreen
import app.giveaway.feature.onboarding.AppLockSetupRoute
import app.giveaway.feature.onboarding.ConnectInstagramRoute
import app.giveaway.feature.onboarding.WelcomeRoute
import app.giveaway.feature.onboarding.appLockSetupScreen
import app.giveaway.feature.onboarding.connectInstagramScreen
import app.giveaway.feature.onboarding.welcomeScreen
import app.giveaway.feature.settings.BackupRoute
import app.giveaway.feature.settings.LicensesRoute
import app.giveaway.feature.settings.RestoreRoute
import app.giveaway.feature.settings.SettingsActions
import app.giveaway.feature.settings.SettingsRoute
import app.giveaway.feature.settings.backupScreen
import app.giveaway.feature.settings.licensesScreen
import app.giveaway.feature.settings.restoreScreen
import app.giveaway.feature.settings.settingsScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.flowOf

/**
 * The app's navigation graph: screens S1 to S15 from the spec. Feature modules own their routes and screens and
 * never depend on each other; this graph connects them.
 */
@Composable
fun GiveawayNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: Any = WelcomeRoute,
    blockScreenshots: Boolean = false,
    onSecureScreen: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    // FLAG_SECURE follows the screen shown, when the user asked for it (spec: "Block screenshots").
    val entry by navController.currentBackStackEntryAsState()
    val secure = blockScreenshots && SecureWindow.protects(entry?.destination)
    LaunchedEffect(secure) { onSecureScreen(secure) }
    // The setup screens of an existing giveaway show a tappable step bar (plan X-2).
    val steps = rememberWizardSteps(entry?.wizardGiveawayId()) { id, step -> navController.openStep(id, step) }
    CompositionLocalProvider(LocalWizardSteps provides steps) {
        NavHost(navController = navController, startDestination = startDestination) {
            // First launch: S1 -> S2 -> S3 -> S4. Onboarding leaves the back stack once Home is reached.
            welcomeScreen(onGetStarted = { navController.navigate(ConnectInstagramRoute()) })
            connectInstagramScreen(onConnected = { reconnect ->
                if (reconnect) navController.backToHome() else navController.navigate(AppLockSetupRoute())
            })
            appLockSetupScreen(onDone = { fromSettings ->
                if (fromSettings) {
                    navController.popBackStack()
                } else {
                    navController.navigate(HomeRoute) { popUpTo<WelcomeRoute> { inclusive = true } }
                }
            })

            homeScreen(
                HomeActions(
                    onNewGiveaway = { navController.navigate(PickPostRoute) },
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                    onOpenGiveaway = { id, destination -> navController.navigate(destination.route(id)) },
                    onSignInAgain = { navController.navigate(ConnectInstagramRoute(reconnect = true)) },
                    onExportBackup = { navController.navigate(BackupRoute) },
                ),
            )
            settingsScreen(
                SettingsActions(
                    onBack = { navController.popBackStack() },
                    onSetUpAppLock = { navController.navigate(AppLockSetupRoute(fromSettings = true)) },
                    onDisconnected = { navController.navigate(ConnectInstagramRoute(reconnect = true)) },
                    onExportBackup = { navController.navigate(BackupRoute) },
                    onRestoreBackup = { navController.navigate(RestoreRoute) },
                    onEverythingDeleted = { restartApp(context) },
                    onOpenLicenses = { navController.navigate(LicensesRoute) },
                ),
            )
            backupScreen(onBack = { navController.popBackStack() })
            // A restore clears the Instagram account, so the user signs in again with nothing to go back to.
            restoreScreen(
                onBack = { navController.popBackStack() },
                onRestored = {
                    navController.navigate(ConnectInstagramRoute(reconnect = true)) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
            )
            licensesScreen(onBack = { navController.popBackStack() })

            creationWizard(navController)
            draw(navController)
        }
    }
}

/** S6 to S10. */
private fun NavGraphBuilder.creationWizard(navController: NavHostController) {
    // Creation wizard. S7's Done opens the giveaway and returns Home ("Waiting for deadline"); it resumes at S9 from
    // its card (plan A35). When entries closed "Now", S9 opens straight away, with Home below it.
    pickPostScreen(
        onBack = { navController.popBackStack() },
        onPostPicked = { route -> navController.navigate(route) },
    )
    setRulesScreen(
        onBack = { navController.popBackStack() },
        onRulesSaved = { id, closedNow ->
            if (closedNow) navController.openStep(id, WizardProgress.IMPORT) else navController.backToHome()
        },
    )
    // An existing giveaway's post and rules change from the step bar (plan A31, A32).
    changePostScreen(
        onBack = { navController.popBackStack() },
        onChanged = { id -> navController.openStep(id, WizardProgress.IMPORT) },
    )
    editRulesScreen(
        onBack = { navController.popBackStack() },
        onSaved = { id, step -> navController.openStep(id, step) },
    )
    importCommentsScreen(
        onBack = { navController.popBackStack() },
        onReviewEntries = { id -> navController.navigate(ReviewEntriesRoute(id)) },
    )
    reviewEntriesScreen(
        onBack = { navController.popBackStack() },
        onContinueToDraw = { id -> navController.navigate(DrawRoute(id)) },
    )
}

/** S11 to S15. */
private fun NavGraphBuilder.draw(navController: NavHostController) {
    // Draw. Once the real draw ran, the result is fixed: S12 leaves the back stack; S14 shows S13 over itself.
    drawScreen(
        onBack = { navController.popBackStack() },
        // The result is already saved and signed; S12 plays it back and records it if the switch was on.
        onDrawn = { id, record -> navController.navigate(DrawingRoute(id, record)) },
        onAlreadyDrawn = { id -> navController.navigate(WinnersRoute(id)) { popUpTo<DrawRoute> { inclusive = true } } },
    )
    drawingScreen(onDrawFinished = { id ->
        navController.navigate(WinnersRoute(id)) { popUpTo<DrawingRoute> { inclusive = true } }
    })
    // Making the certificate archives the giveaway, so S14 leaves the back stack (plan A28).
    winnersScreen(
        onCreateCertificate = { id ->
            navController.navigate(CertificateRoute(id)) { popUpTo<WinnersRoute> { inclusive = true } }
        },
        onRedrawn = { id -> navController.openStep(id, WizardProgress.DRAW) },
    )
    certificateScreen(
        onDone = { navController.backToHome() },
        onRedrawn = { id -> navController.openStep(id, WizardProgress.DRAW) },
    )
}

/**
 * After "Delete everything": start again at S1 in a fresh process, so nothing from the wiped data (the closed
 * database, cached keys) stays in memory (plan M-14).
 */
private fun restartApp(context: Context) {
    context.startActivity(
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
    )
    Runtime.getRuntime().exit(0)
}

/** Opens a setup step of an existing giveaway from the step bar (plan X-2); Home stays below it. */
private fun NavHostController.openStep(giveawayId: Long, step: Int) {
    val route: Any = when (step) {
        WizardProgress.POST -> ChangePostRoute(giveawayId)
        WizardProgress.RULES -> EditRulesRoute(giveawayId)
        WizardProgress.IMPORT -> ImportCommentsRoute(giveawayId)
        WizardProgress.REVIEW -> ReviewEntriesRoute(giveawayId)
        else -> DrawRoute(giveawayId)
    }
    navigate(route) {
        popUpTo<HomeRoute>()
        launchSingleTop = true
    }
}

/** The giveaway whose setup step is open, if any: the step bar works for existing giveaways only. */
private fun NavBackStackEntry.wizardGiveawayId(): Long? = when {
    destination.hasRoute<ChangePostRoute>() -> toRoute<ChangePostRoute>().giveawayId
    destination.hasRoute<EditRulesRoute>() -> toRoute<EditRulesRoute>().giveawayId
    destination.hasRoute<ImportCommentsRoute>() -> toRoute<ImportCommentsRoute>().giveawayId
    destination.hasRoute<ReviewEntriesRoute>() -> toRoute<ReviewEntriesRoute>().giveawayId
    destination.hasRoute<DrawRoute>() -> toRoute<DrawRoute>().giveawayId
    else -> null
}

@HiltViewModel
class WizardStepsViewModel @Inject constructor(val progress: WizardProgress) : ViewModel()

/** The step bar's reachable steps for [giveawayId], following its status. */
@Composable
private fun rememberWizardSteps(giveawayId: Long?, open: (Long, Int) -> Unit): WizardSteps? {
    val progress = hiltViewModel<WizardStepsViewModel>().progress
    val reachable by remember(giveawayId) { giveawayId?.let(progress::steps) ?: flowOf(emptySet()) }
        .collectAsStateWithLifecycle(emptySet())
    // A new object only when something changed: the provider is static, so a change redraws the whole host.
    return remember(giveawayId, reachable) {
        giveawayId?.let { id -> WizardSteps(reachable) { step -> open(id, step) } }
    }
}

/** Opens Home and drops everything above it, so Back from Home leaves the app. */
private fun NavHostController.backToHome() = navigate(HomeRoute) {
    popUpTo<HomeRoute> { inclusive = true }
    launchSingleTop = true
}

/** The screen a Home card opens, by the giveaway's status (spec: S4). */
private fun GiveawayDestination.route(giveawayId: Long): Any = when (this) {
    GiveawayDestination.RULES -> EditRulesRoute(giveawayId)
    GiveawayDestination.IMPORT -> ImportCommentsRoute(giveawayId)
    GiveawayDestination.REVIEW -> ReviewEntriesRoute(giveawayId)
    GiveawayDestination.WINNERS -> WinnersRoute(giveawayId)
    GiveawayDestination.CERTIFICATE -> CertificateRoute(giveawayId)
}
