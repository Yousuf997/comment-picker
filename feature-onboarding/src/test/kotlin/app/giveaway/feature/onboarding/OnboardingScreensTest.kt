package app.giveaway.feature.onboarding

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.giveaway.core.designsystem.GiveawayTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** F-10 acceptance: S1 content and action, and S2 in each of its states. */
@RunWith(RobolectricTestRunner::class)
class OnboardingScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private var continues = 0
    private var helps = 0

    private fun text(id: Int) = compose.onNodeWithText(app.getString(id))

    private fun showConnect(state: ConnectState) = compose.setContent {
        GiveawayTheme { ConnectInstagramScreen(state, onContinue = { continues++ }, onOpenHelp = { helps++ }) }
    }

    @Test
    fun welcomeShowsTrustPointsAndStartsOnboarding() {
        var started = 0
        compose.setContent { GiveawayTheme { WelcomeScreen(onGetStarted = { started++ }) } }
        listOf(
            R.string.welcome_headline,
            R.string.welcome_trust_device,
            R.string.welcome_trust_verify,
            R.string.welcome_trust_official,
        ).forEach { text(it).assertExists() }
        text(R.string.welcome_footnote).performScrollTo().assertIsDisplayed()
        // The illustration is decorative: its "Winner" label must not reach TalkBack.
        text(R.string.welcome_ticket_winner).assertDoesNotExist()
        text(R.string.welcome_get_started).performScrollTo().performClick()
        assertEquals(1, started)
    }

    @Test
    fun idleExplainsRequirementsAndPermissions() {
        showConnect(ConnectState.Idle)
        listOf(
            R.string.connect_requirement_title,
            R.string.connect_can_read_posts,
            R.string.connect_can_read_comments,
            R.string.connect_never_post,
            R.string.connect_never_password,
            R.string.connect_never_upload,
        ).forEach { text(it).assertExists() }
        text(R.string.connect_how_to_switch).performScrollTo().performClick()
        assertEquals(1, helps)
        text(R.string.connect_continue).performScrollTo().performClick()
        assertEquals(1, continues)
    }

    @Test
    fun loadingHidesTheButtonAndSaysWhatIsHappening() {
        showConnect(ConnectState.Loading)
        text(R.string.connect_loading).assertExists()
        text(R.string.connect_continue).assertDoesNotExist()
        text(R.string.connect_retry).assertDoesNotExist()
    }

    @Test
    fun personalAccountExplainsHowToSwitch() {
        showConnect(ConnectState.PersonalAccount)
        text(R.string.connect_personal_title).assertExists()
        text(R.string.connect_personal_body).assertExists()
        // The general requirement card is replaced by the specific warning.
        text(R.string.connect_requirement_title).assertDoesNotExist()
        text(R.string.connect_how_to_switch).performScrollTo().performClick()
        assertEquals(1, helps)
    }

    @Test
    fun cancelledOffersToTryAgain() {
        showConnect(ConnectState.Cancelled)
        text(R.string.connect_cancelled).assertExists()
        text(R.string.connect_retry).performScrollTo().performClick()
        assertEquals(1, continues)
    }

    @Test
    fun networkErrorOffersRetry() {
        showConnect(ConnectState.NetworkError)
        text(R.string.connect_network_title).assertExists()
        // Retry appears in the error card and as the main button; both restart sign-in.
        val retries = compose.onAllNodesWithText(app.getString(R.string.connect_retry))
        retries.assertCountEquals(2)
        retries[0].performClick()
        assertEquals(1, continues)
    }
}
