package app.giveaway.feature.onboarding

import androidx.compose.runtime.Composable
import app.giveaway.core.designsystem.GiveawayTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** S1 and S2 screenshots. The height fits the whole scrollable content. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h1100dp-xhdpi")
class OnboardingScreenshotTest {

    private fun capture(name: String, dark: Boolean = false, content: @Composable () -> Unit) =
        captureRoboImage("src/test/screenshots/$name.png") { GiveawayTheme(darkTheme = dark) { content() } }

    @Test
    fun welcomeLight() = capture("s1_welcome_light") { WelcomeScreen(onGetStarted = {}) }

    @Test
    fun welcomeDark() = capture("s1_welcome_dark", dark = true) { WelcomeScreen(onGetStarted = {}) }

    @Test
    @Config(qualifiers = "ar-w390dp-h1100dp-xhdpi")
    fun welcomeArabic() = capture("s1_welcome_arabic") { WelcomeScreen(onGetStarted = {}) }

    @Test
    fun connectIdleLight() = capture("s2_connect_idle_light") { Connect(ConnectState.Idle) }

    @Test
    @Config(qualifiers = "ar-w390dp-h1100dp-xhdpi")
    fun connectIdleArabic() = capture("s2_connect_idle_arabic") { Connect(ConnectState.Idle) }

    @Test
    fun connectNetworkErrorLight() = capture("s2_connect_network_error_light") { Connect(ConnectState.NetworkError) }

    @Test
    fun connectPersonalAccountDark() =
        capture("s2_connect_personal_account_dark", dark = true) { Connect(ConnectState.PersonalAccount) }

    @Composable
    private fun Connect(state: ConnectState) = ConnectInstagramScreen(state, onContinue = {}, onOpenHelp = {})
}
