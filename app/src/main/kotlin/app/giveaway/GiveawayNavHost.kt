package app.giveaway

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import app.giveaway.feature.create.LockInDrawRoute
import app.giveaway.feature.create.PickPostRoute
import app.giveaway.feature.create.ReviewEntriesRoute
import app.giveaway.feature.create.SetRulesRoute
import app.giveaway.feature.create.importCommentsScreen
import app.giveaway.feature.create.lockInDrawScreen
import app.giveaway.feature.create.pickPostScreen
import app.giveaway.feature.create.reviewEntriesScreen
import app.giveaway.feature.create.setRulesScreen
import app.giveaway.feature.draw.CertificateRoute
import app.giveaway.feature.draw.DrawRoute
import app.giveaway.feature.draw.DrawingRoute
import app.giveaway.feature.draw.SaveVideoRoute
import app.giveaway.feature.draw.WinnersRoute
import app.giveaway.feature.draw.certificateScreen
import app.giveaway.feature.draw.drawScreen
import app.giveaway.feature.draw.drawingScreen
import app.giveaway.feature.draw.saveVideoSheet
import app.giveaway.feature.draw.winnersScreen
import app.giveaway.feature.home.HomeRoute
import app.giveaway.feature.home.homeScreen
import app.giveaway.feature.onboarding.AppLockSetupRoute
import app.giveaway.feature.onboarding.ConnectInstagramRoute
import app.giveaway.feature.onboarding.WelcomeRoute
import app.giveaway.feature.onboarding.appLockSetupScreen
import app.giveaway.feature.onboarding.connectInstagramScreen
import app.giveaway.feature.onboarding.welcomeScreen
import app.giveaway.feature.settings.SettingsRoute
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
        welcomeScreen(onGetStarted = { navController.navigate(ConnectInstagramRoute) })
        connectInstagramScreen(onConnected = { navController.navigate(AppLockSetupRoute) })
        appLockSetupScreen(onDone = {
            navController.navigate(HomeRoute) { popUpTo<WelcomeRoute> { inclusive = true } }
        })

        homeScreen(
            onNewGiveaway = { navController.navigate(PickPostRoute) },
            onOpenSettings = { navController.navigate(SettingsRoute) },
        )
        settingsScreen(onBack = { navController.popBackStack() })

        // Creation wizard. S8 returns to Home ("Waiting for deadline"); the giveaway resumes at S9 from its card.
        pickPostScreen(onPostPicked = { mediaId -> navController.navigate(SetRulesRoute(mediaId)) })
        setRulesScreen(onRulesSaved = { id -> navController.navigate(LockInDrawRoute(id)) })
        lockInDrawScreen(onDone = { navController.backToHome() })
        importCommentsScreen(onReviewEntries = { id -> navController.navigate(ReviewEntriesRoute(id)) })
        reviewEntriesScreen(onContinueToDraw = { id -> navController.navigate(DrawRoute(id)) })

        // Draw. Once the real draw ran, the result is fixed: S12 leaves the back stack and S13 opens over S14.
        drawScreen(onDrawWinners = { id -> navController.navigate(DrawingRoute(id)) })
        drawingScreen(onDrawFinished = { id ->
            navController.navigate(WinnersRoute(id)) { popUpTo<DrawingRoute> { inclusive = true } }
            navController.navigate(SaveVideoRoute(id))
        })
        saveVideoSheet(onChoiceMade = { navController.popBackStack() })
        winnersScreen(onCreateCertificate = { id -> navController.navigate(CertificateRoute(id)) })
        certificateScreen(onDone = { navController.backToHome() })
    }
}

/** Opens Home and drops everything above it, so Back from Home leaves the app. */
private fun NavHostController.backToHome() = navigate(HomeRoute) {
    popUpTo<HomeRoute> { inclusive = true }
    launchSingleTop = true
}
