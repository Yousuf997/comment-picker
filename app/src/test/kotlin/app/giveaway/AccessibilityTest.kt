package app.giveaway

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import app.giveaway.core.designsystem.GiveawayTheme
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
import app.giveaway.feature.settings.BackupRoute
import app.giveaway.feature.settings.RestoreRoute
import app.giveaway.feature.settings.SettingsRoute
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * H-03: on every screen, in English and Arabic at 200% font size, each control TalkBack can activate has a label and
 * a touch target of at least 44 dp (spec: Accessibility). Color-only signals and focus order are covered by the screen
 * tests and the manual TalkBack pass.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w390dp-h2400dp", fontScale = 2.0f)
class AccessibilityTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<HiltTestActivity>()

    private lateinit var navController: NavHostController

    private val routes = listOf<Pair<Any, String>>(
        WelcomeRoute to "S1",
        ConnectInstagramRoute() to "S2",
        AppLockSetupRoute() to "S3",
        HomeRoute to "S4",
        SettingsRoute to "S5",
        BackupRoute to "backup",
        RestoreRoute to "restore",
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

    private fun launch(content: @Composable () -> Unit = { GiveawayNavHost(navController) }) {
        compose.setContent {
            navController = rememberNavController()
            GiveawayTheme { content() }
        }
    }

    private fun SemanticsNode.label(): String? {
        val config = config
        val text = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        val description = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
        return listOfNotNull(text, description).joinToString(" ").takeIf { it.isNotBlank() }
    }

    private var checked = 0

    private fun problemsOn(screen: String): List<String> = compose.onAllNodes(hasClickAction())
        .fetchSemanticsNodes()
        .also { checked += it.size }
        .flatMap { node ->
            val label = node.label()
            val bounds = node.touchBoundsInRoot
            val (width, height) = with(compose.density) { bounds.width.toDp().value to bounds.height.toDp().value }
            buildList {
                if (label == null) add("$screen: a control has no TalkBack label (${node.config})")
                if (width < MIN_TOUCH_DP || height < MIN_TOUCH_DP) {
                    add("$screen: \"$label\" is ${width.toInt()} x ${height.toInt()} dp, under $MIN_TOUCH_DP")
                }
            }
        }

    private fun checkEveryScreen() {
        launch()
        val problems = routes.flatMap { (route, screen) ->
            compose.runOnUiThread { navController.navigate(route) }
            compose.waitForIdle()
            problemsOn(screen)
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
        // Guards the check itself: the screens have dozens of controls between them.
        assertTrue("only $checked controls found", checked > MIN_CONTROLS)
    }

    @Test
    fun everyControlIsLabelledAndLargeEnough() = checkEveryScreen()

    @Test
    @Config(qualifiers = "ar-w390dp-h2400dp")
    fun everyControlIsLabelledAndLargeEnoughInArabic() = checkEveryScreen()

    private companion object {
        /** Never under 44 dp (spec: Accessibility); 48 dp where possible. */
        const val MIN_TOUCH_DP = 44f
        const val MIN_CONTROLS = 30
    }
}
