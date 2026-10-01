package app.giveaway.feature.onboarding

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.giveaway.core.designsystem.GiveawayTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** C-03 acceptance: S3 offers biometrics only when enrolled, and the PIN entry behaves. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class AppLockSetupScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val calls = mutableListOf<String>()

    private fun text(id: Int) = compose.onNodeWithText(app.getString(id))

    private fun show(state: AppLockSetupState) = compose.setContent {
        GiveawayTheme {
            AppLockSetupScreen(
                state = state,
                onUseBiometrics = { calls += "biometrics" },
                onChoosePin = { calls += "pin" },
                onPinSubmitted = { calls += "submit:$it" },
                onBack = { calls += "back" },
                onSkip = { calls += "skip" },
            )
        }
    }

    @Test
    fun withBiometricsAllThreeChoicesAreOffered() {
        show(AppLockSetupState(biometricsAvailable = true))
        text(R.string.app_lock_use_biometrics).performClick()
        text(R.string.app_lock_set_pin).performClick()
        text(R.string.app_lock_skip).performClick()
        assertEquals(listOf("biometrics", "pin", "skip"), calls)
    }

    @Test
    fun withoutBiometricsOnlyAPinIsOffered() {
        show(AppLockSetupState(biometricsAvailable = false))
        text(R.string.app_lock_use_biometrics).assertDoesNotExist()
        text(R.string.app_lock_no_biometrics).assertExists()
        text(R.string.app_lock_set_pin).performClick()
        assertEquals(listOf("pin"), calls)
    }

    @Test
    fun pinEntryAcceptsOnlySixDigits() {
        show(AppLockSetupState(biometricsAvailable = true, step = AppLockStep.EnterPin(confirming = false)))
        val pin = compose.onNodeWithTag("pin")
        val next = text(R.string.app_lock_continue)
        next.assertIsNotEnabled()
        pin.performTextInput("12a34")
        next.assertIsNotEnabled()
        pin.performTextInput("5678")
        next.assertIsEnabled().performClick()
        assertEquals(listOf("submit:123456"), calls)
    }

    @Test
    fun aMismatchIsExplained() {
        val mismatch = AppLockStep.EnterPin(confirming = false, mismatch = true)
        show(AppLockSetupState(biometricsAvailable = true, step = mismatch))
        text(R.string.app_lock_pin_mismatch).assertExists()
        text(R.string.app_lock_back).performClick()
        assertEquals(listOf("back"), calls)
    }

    @Test
    fun confirmingAsksForThePinAgain() {
        show(AppLockSetupState(biometricsAvailable = true, step = AppLockStep.EnterPin(confirming = true)))
        text(R.string.app_lock_pin_confirm_title).assertExists()
    }

    @Test
    fun screenshotChoose() = captureRoboImage("src/test/screenshots/s3_app_lock_choose.png") {
        GiveawayTheme { AppLockSetupScreen(AppLockSetupState(biometricsAvailable = true), {}, {}, {}, {}, {}) }
    }

    @Test
    @Config(qualifiers = "ar-w390dp-h844dp-xhdpi")
    fun screenshotPinArabic() = captureRoboImage("src/test/screenshots/s3_app_lock_pin_arabic.png") {
        GiveawayTheme {
            val step = AppLockStep.EnterPin(confirming = false, mismatch = true)
            val state = AppLockSetupState(biometricsAvailable = false, step = step)
            AppLockSetupScreen(state, {}, {}, {}, {}, {})
        }
    }
}
