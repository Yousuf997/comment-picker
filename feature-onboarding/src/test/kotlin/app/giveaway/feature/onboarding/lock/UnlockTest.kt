package app.giveaway.feature.onboarding.lock

import android.app.Application
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.giveaway.core.data.db.AppLockMethod
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.security.lock.BiometricResult
import app.giveaway.core.security.lock.PinCheck
import app.giveaway.core.security.lock.PinStore
import app.giveaway.feature.onboarding.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

/** C-04: unlocking with a PIN (including the lockout) or biometrics. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class UnlockTest {

    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val dispatcher = StandardTestDispatcher()
    private val controller = AppLockController { 0L }
    private val pins = object : PinStore {
        var result: PinCheck = PinCheck.Correct
        override fun hasPin() = true
        override fun setPin(pin: String) = Unit
        override fun verify(pin: String) = result
        override fun clear() = Unit
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun lockedWith(method: AppLockMethod): UnlockViewModel {
        controller.onSettings(method, lockAfterSeconds = 60)
        return UnlockViewModel(controller, { pins }, dispatcher)
    }

    @Test
    fun theRightPinUnlocks() = runTest(dispatcher) {
        val vm = lockedWith(AppLockMethod.PIN)
        vm.onPinSubmitted("482913")
        advanceUntilIdle()
        assertEquals(LockStatus.UNLOCKED, controller.status.value)
    }

    @Test
    fun aWrongPinStaysLockedAndCountsDown() = runTest(dispatcher) {
        pins.result = PinCheck.Wrong(attemptsBeforeLockout = 2)
        val vm = lockedWith(AppLockMethod.PIN)
        vm.onPinSubmitted("000000")
        advanceUntilIdle()
        assertEquals(LockStatus.LOCKED, controller.status.value)
        assertEquals(2, vm.state.value.attemptsLeft)
    }

    @Test
    fun aLockoutIsReported() = runTest(dispatcher) {
        val until = Instant.now().plusSeconds(30)
        pins.result = PinCheck.LockedOut(until)
        val vm = lockedWith(AppLockMethod.PIN)
        vm.onPinSubmitted("000000")
        advanceUntilIdle()
        assertEquals(until, vm.state.value.lockedUntil)
    }

    @Test
    fun biometricsUnlockAndFailuresAreShown() {
        val vm = lockedWith(AppLockMethod.BIOMETRIC)
        vm.onBiometricResult(BiometricResult.FAILED)
        assertEquals(true, vm.state.value.biometricFailed)
        vm.onBiometricResult(BiometricResult.SUCCEEDED)
        assertEquals(LockStatus.UNLOCKED, controller.status.value)
    }

    @Test
    fun theScreenSaysHowManyTriesAreLeft() {
        compose.setContent {
            GiveawayTheme { UnlockScreen(UnlockState(AppLockMethod.PIN, attemptsLeft = 1), {}, {}) }
        }
        val text = app.resources.getQuantityString(R.plurals.lock_wrong_pin, 1, 1)
        compose.onNodeWithText(text).assertExists()
    }

    @Test
    fun duringALockoutPinEntryIsDisabledWithACountdown() {
        val now = Instant.parse("2026-10-01T12:00:00Z")
        compose.setContent {
            GiveawayTheme {
                UnlockScreen(UnlockState(AppLockMethod.PIN, lockedUntil = now.plusSeconds(90)), {}, {}, now = { now })
            }
        }
        compose.onNodeWithText(app.getString(R.string.lock_locked_out, "1:30")).assertExists()
        compose.onNodeWithText(app.getString(R.string.lock_unlock)).assertIsNotEnabled()
    }

    @Test
    fun aFullPinIsSubmitted() {
        var submitted: String? = null
        compose.setContent {
            GiveawayTheme { UnlockScreen(UnlockState(AppLockMethod.PIN), {}, { submitted = it }) }
        }
        compose.onNodeWithTag("pin").performTextInput("482913")
        compose.onNodeWithText(app.getString(R.string.lock_unlock)).performClick()
        assertEquals("482913", submitted)
    }
}
