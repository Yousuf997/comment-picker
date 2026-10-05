package app.giveaway

import android.Manifest
import android.app.Application
import android.content.Intent
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.test.core.app.ApplicationProvider
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.di.FakeInstagramModule
import app.giveaway.feature.create.ImportCommentsRoute
import app.giveaway.feature.create.LockInDrawRoute
import app.giveaway.feature.create.PickPostRoute
import app.giveaway.feature.create.ReviewEntriesRoute
import app.giveaway.feature.create.SetRulesRoute
import app.giveaway.feature.draw.CertificateRoute
import app.giveaway.feature.draw.DrawRoute
import app.giveaway.feature.draw.DrawingRoute
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
import org.robolectric.Shadows.shadowOf
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import app.giveaway.feature.create.R as CreateR
import app.giveaway.feature.draw.R as DrawR
import app.giveaway.feature.home.R as HomeR
import app.giveaway.feature.onboarding.R as OnboardingR

/** F-08 acceptance: every route S1 to S15 opens its screen, and the spec's flow can be walked end to end. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
// Tall enough that each screen's main action is on screen without scrolling; screen tests cover scrolling.
@Config(application = HiltTestApplication::class, qualifiers = "w390dp-h1400dp")
class NavigationTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    // Screens with ViewModels (S2) need a Hilt activity.
    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<HiltTestActivity>()

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

    private fun awaitScreen(id: String) = compose.waitUntil(WAIT_MS) {
        compose.onAllNodesWithTag("screen:$id").fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun everyRouteOpensItsScreen() {
        launch()
        val routes = listOf<Pair<Any, String>>(
            WelcomeRoute to "S1",
            ConnectInstagramRoute() to "S2",
            AppLockSetupRoute() to "S3",
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
        // Sign-in happens on Instagram's page, opened in the browser; the redirect is covered by ViewModel tests.
        val browser = shadowOf(compose.activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, browser.action)
        assertEquals("www.instagram.com", browser.data?.host)
        compose.runOnUiThread { navController.navigate(AppLockSetupRoute()) }
        assertScreen("S3")
        tap(OnboardingR.string.app_lock_skip)
        assertScreen("S4")
        assertFalse("Back from Home must leave the app", navController.previousBackStackEntry != null)
    }

    private fun awaitEnabled(tag: String) = compose.waitUntil(WAIT_MS) {
        runCatching { compose.onNodeWithTag(tag).assertIsEnabled() }.isSuccess
    }

    /** Home → S6 → S7 → S8: a draft exists once S8 opens. */
    private fun openLockInForANewDraft() {
        launch { GiveawayNavHost(navController, startDestination = HomeRoute) }
        tap(HomeR.string.home_new_giveaway)
        assertScreen("S6")
        // The fake Instagram has one post; Continue stays off until it is picked.
        compose.onNodeWithTag("pick_post:tile:${FakeInstagramModule.POST.id}").performClick()
        tap(CreateR.string.wizard_continue)
        assertScreen("S7")
        // S7 saves the draft through Room, which is asynchronous.
        compose.onNodeWithText(app.getString(CreateR.string.wizard_continue)).performScrollTo().performClick()
        awaitScreen("S8")
    }

    @Test
    fun creationWizardReturnsHomeAfterLockingInTheDraw() {
        openLockInForANewDraft()
        // Done needs the box ticked and the code loaded; granting notifications skips the Android 13 prompt.
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        compose.onNodeWithText(app.getString(CreateR.string.lock_in_confirm)).performScrollTo().performClick()
        val done = compose.onNodeWithText(app.getString(CreateR.string.lock_in_done))
        compose.waitUntil(WAIT_MS) { runCatching { done.assertIsEnabled() }.isSuccess }
        done.performScrollTo().performClick()
        awaitScreen("S4")
        assertFalse(navController.previousBackStackEntry != null)
    }

    @Test
    fun theStepBarMovesBetweenTheSetupSteps() {
        openLockInForANewDraft()
        // A draft has a post, rules and a code; the import isn't reachable before the code is locked in.
        val importStep = compose.onNodeWithTag("wizard:step:4")
        awaitEnabled("wizard:step:2")
        importStep.assertIsNotEnabled()
        compose.onNodeWithTag("wizard:step:2").performClick()
        awaitScreen("S7")
        awaitEnabled("wizard:step:1")
        compose.onNodeWithTag("wizard:step:1").performClick()
        awaitScreen("S6")
        compose.onNodeWithTag("wizard:step:3").performClick()
        awaitScreen("S8")
        // Each step replaces the last, so Back returns Home.
        assertEquals(true, navController.previousBackStackEntry?.destination?.hasRoute<HomeRoute>())
    }

    @Test
    fun screenshotsAreBlockedWhereEntriesOrWinnersShowWhenAsked() {
        var block by mutableStateOf(true)
        lateinit var secure: SecureWindow
        launch {
            secure = remember { SecureWindow(compose.activity.window) }
            GiveawayNavHost(navController, HomeRoute, blockScreenshots = block, onSecureScreen = { secure.screen = it })
        }
        fun flagged() = compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        compose.waitForIdle()
        assertFalse("Home shows no entries", flagged())
        compose.runOnUiThread { navController.navigate(WinnersRoute(giveawayId = 7)) }
        compose.waitUntil(WAIT_MS) { flagged() }
        // Turning the setting off lifts it straight away.
        block = false
        compose.waitUntil(WAIT_MS) { !flagged() }
        block = true
        compose.waitUntil(WAIT_MS) { flagged() }
        compose.runOnUiThread { navController.popBackStack() }
        compose.waitUntil(WAIT_MS) { !flagged() }
    }

    @Test
    fun drawFlowEndsOnTheWinnersAndCannotReturnToTheDraw() {
        launch { GiveawayNavHost(navController, startDestination = HomeRoute) }
        compose.runOnUiThread { navController.navigate(DrawingRoute(giveawayId = 7)) }
        assertScreen("S12")
        // S12 looks for the saved draw through Room first; the button appears once it knows there is none.
        val finish = app.getString(DrawR.string.drawing_finish)
        compose.waitUntil(WAIT_MS) { compose.onAllNodesWithText(finish).fetchSemanticsNodes().isNotEmpty() }
        tap(DrawR.string.drawing_finish)
        assertScreen("S14")
        assertFalse(
            "The finished draw must not be on the back stack",
            navController.currentBackStack.value.any { it.destination.hasRoute<DrawingRoute>() },
        )
        tap(DrawR.string.winners_create_certificate)
        compose.onNodeWithTag("certificate:make_confirm").performClick()
        assertScreen("S15")
        assertFalse(
            "The certificate archives the giveaway, so S14 leaves the back stack",
            navController.currentBackStack.value.any { it.destination.hasRoute<WinnersRoute>() },
        )
        tap(DrawR.string.certificate_done)
        assertScreen("S4")
    }

    @Test
    @Config(application = HiltTestApplication::class, qualifiers = "ar-w390dp-h1400dp")
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

    private companion object {
        const val WAIT_MS = 15_000L
    }
}
