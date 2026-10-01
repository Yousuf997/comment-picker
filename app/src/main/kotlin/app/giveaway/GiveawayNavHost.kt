package app.giveaway

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import app.giveaway.feature.create.ImportCommentsRoute
import app.giveaway.feature.create.LockInDrawRoute
import app.giveaway.feature.create.PickPostRoute
import app.giveaway.feature.create.ReviewEntriesRoute
import app.giveaway.feature.create.importCommentsScreen
import app.giveaway.feature.create.lockInDrawScreen
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
import app.giveaway.feature.settings.SettingsRoute
import app.giveaway.feature.settings.LicensesRoute
import app.giveaway.feature.settings.SettingsActions
import app.giveaway.feature.settings.licensesScreen
import app.giveaway.feature.settings.settingsScreen

/**
 * The app's navigation graph: screens S1 to S15 from the spec. Feature modules own their routes and screens and
 * never depend on each other; this graph connects them.
 */
@Composable
fun GiveawayNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: Any = WelcomeRoute,
) {
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
                // The backup flow lives in Settings (S5).
                onExportBackup = { navController.navigate(SettingsRoute) },
            ),
        )
        settingsScreen(
            SettingsActions(
                onBack = { navController.popBackStack() },
                onSetUpAppLock = { navController.navigate(AppLockSetupRoute(fromSettings = true)) },
                onDisconnected = { navController.navigate(ConnectInstagramRoute(reconnect = true)) },
                // Backup, restore and "Delete everything" arrive with M-11..M-14.
                onExportBackup = {},
                onRestoreBackup = {},
                onDeleteEverything = {},
                onOpenLicenses = { navController.navigate(LicensesRoute) },
            ),
        )
        licensesScreen(onBack = { navController.popBackStack() })

        creationWizard(navController)
        draw(navController)
    }
}

/** S6 to S10. */
private fun NavGraphBuilder.creationWizard(navController: NavHostController) {
    // Creation wizard. S8 returns to Home ("Waiting for deadline"); the giveaway resumes at S9 from its card.
    pickPostScreen(
        onBack = { navController.popBackStack() },
        onPostPicked = { route -> navController.navigate(route) },
    )
    setRulesScreen(
        onBack = { navController.popBackStack() },
        onRulesSaved = { id -> navController.navigate(LockInDrawRoute(id)) },
    )
    lockInDrawScreen(onBack = { navController.popBackStack() }, onDone = { navController.backToHome() })
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
    winnersScreen(onCreateCertificate = { id ->
        navController.navigate(CertificateRoute(id)) { popUpTo<WinnersRoute> { inclusive = true } }
    })
    certificateScreen(onDone = { navController.backToHome() })
}

/** Opens Home and drops everything above it, so Back from Home leaves the app. */
private fun NavHostController.backToHome() = navigate(HomeRoute) {
    popUpTo<HomeRoute> { inclusive = true }
    launchSingleTop = true
}

/** The screen a Home card opens, by the giveaway's status (spec: S4). */
private fun GiveawayDestination.route(giveawayId: Long): Any = when (this) {
    GiveawayDestination.LOCK_IN -> LockInDrawRoute(giveawayId)
    GiveawayDestination.IMPORT -> ImportCommentsRoute(giveawayId)
    GiveawayDestination.REVIEW -> ReviewEntriesRoute(giveawayId)
    GiveawayDestination.WINNERS -> WinnersRoute(giveawayId)
    GiveawayDestination.CERTIFICATE -> CertificateRoute(giveawayId)
}
