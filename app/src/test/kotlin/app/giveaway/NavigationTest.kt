package app.giveaway

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.test.core.app.ApplicationProvider
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.feature.create.ImportCommentsRoute
import app.giveaway.feature.create.LockInDrawRoute
import app.giveaway.feature.create.PickPostRoute
import app.giveaway.feature.create.ReviewEntriesRoute
import app.giveaway.feature.create.SetRulesRoute
import app.giveaway.feature.draw.CertificateRoute
import app.giveaway.feature.draw.DrawRoute
import app.giveaway.feature.draw.DrawingRoute
import app.giveaway.feature.draw.SaveVideoRoute
import app.giveaway.feature.draw.WinnersRoute
import app.giveaway.feature.home.HomeRoute
import app.giveaway.feature.onboarding.AppLockSetupRoute
import app.giveaway.feature.onboarding.ConnectInstagramRoute
import app.giveaway.feature.onboarding.WelcomeRoute
import app.giveaway.feature.settings.SettingsRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import app.giveaway.feature.create.R as CreateR
import app.giveaway.feature.draw.R as DrawR
import app.giveaway.feature.home.R as HomeR
import app.giveaway.feature.onboarding.R as OnboardingR

/** F-08 acceptance: every route S1 to S15 opens its screen, and the spec's flow can be walked end to end. */
@RunWith(RobolectricTestRunner::class)
// Tall enough that each screen's main action is on screen without scrolling; screen tests cover scrolling.
@Config(qualifiers = "w390dp-h1400dp")
class NavigationTest {

    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var navController: NavHostController

    private fun launch(content: @Composable () -> Unit = { GiveawayNavHost(navController) }) {
        compose.setContent {
            navController = rememberNavController()
            GiveawayTheme { content() }
        }
    }

    private fun tap(stringRes: Int) = compose.onNodeWithText(app.getString(stringRes)).performClick()

    private fun assertScreen(id: String) = compose.onNodeWithTag("screen:$id").assertExists()

    @Test
    fun everyRouteOpensItsScreen() {
        launch()
        val routes = listOf<Pair<Any, String>>(
            WelcomeRoute to "S1",
            ConnectInstagramRoute to "S2",
            AppLockSetupRoute to "S3",
            HomeRoute to "S4",
            SettingsRoute to "S5",
            PickPostRoute to "S6",
            SetRulesRoute(mediaId = "m1") to "S7",
            LockInDrawRoute(giveawayId = 1) to "S8",
            ImportCommentsRoute(giveawayId = 1) to "S9",
            ReviewEntriesRoute(giveawayId = 1) to "S10",
            DrawRoute(giveawayId = 1) to "S11",
            DrawingRoute(giveawayId = 1) to "S12",
            WinnersRoute(giveawayId = 1) to "S14",
            SaveVideoRoute(giveawayId = 1) to "S13",
            CertificateRoute(giveawayId = 1) to "S15",
        )
        routes.forEach { (route, id) ->
            compose.runOnUiThread { navController.navigate(route) }
            compose.waitForIdle()
            assertScreen(id)
        }
    }

    @Test
    fun firstLaunchReachesHomeAndLeavesOnboardingOffTheBackStack() {
        launch()
        assertScreen("S1")
        tap(OnboardingR.string.welcome_get_started)
        assertScreen("S2")
        tap(OnboardingR.string.connect_continue)
        assertScreen("S3")
        tap(OnboardingR.string.app_lock_skip)
        assertScreen("S4")
        assertFalse("Back from Home must leave the app", navController.previousBackStackEntry != null)
    }

    @Test
    fun creationWizardReturnsHomeAfterLockingInTheDraw() {
        launch { GiveawayNavHost(navController, startDestination = HomeRoute) }
        tap(HomeR.string.home_new_giveaway)
        assertScreen("S6")
        tap(CreateR.string.wizard_continue)
        assertScreen("S7")
        tap(CreateR.string.wizard_continue)
        assertScreen("S8")
        tap(CreateR.string.lock_in_done)
        assertScreen("S4")
        assertFalse(navController.previousBackStackEntry != null)
    }

    @Test
    fun drawFlowShowsTheSaveSheetOverWinnersAndCannotReturnToTheDraw() {
        launch { GiveawayNavHost(navController, startDestination = HomeRoute) }
        compose.runOnUiThread { navController.navigate(ImportCommentsRoute(giveawayId = 7)) }
        tap(CreateR.string.import_review)
        assertScreen("S10")
        tap(CreateR.string.review_continue)
        assertScreen("S11")
        tap(DrawR.string.draw_winners)
        assertScreen("S12")
        tap(DrawR.string.drawing_finish)
        assertScreen("S13")
        tap(DrawR.string.save_video_save)
        compose.onNodeWithTag("screen:S13").assertDoesNotExist()
        assertScreen("S14")
        assertFalse(
            "The finished draw must not be on the back stack",
            navController.currentBackStack.value.any { it.destination.hasRoute<DrawingRoute>() },
        )
        tap(DrawR.string.winners_create_certificate)
        assertScreen("S15")
        tap(DrawR.string.certificate_done)
        assertScreen("S4")
    }

    @Test
    @Config(qualifiers = "ar-w390dp-h1400dp")
    fun arabicIsTranslatedAndRightToLeft() {
        var direction: LayoutDirection? = null
        launch {
            direction = LocalLayoutDirection.current
            GiveawayNavHost(navController)
        }
        compose.onNodeWithText(app.getString(OnboardingR.string.welcome_headline)).assertExists()
        assertEquals("سحوبات عادلة، مع إثبات.", app.getString(OnboardingR.string.welcome_headline))
        assertEquals(LayoutDirection.Rtl, direction)
    }
}
