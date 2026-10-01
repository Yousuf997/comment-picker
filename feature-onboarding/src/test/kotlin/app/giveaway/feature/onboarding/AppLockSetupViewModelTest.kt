package app.giveaway.feature.onboarding

import app.giveaway.core.data.db.AppLockMethod
import app.giveaway.core.data.db.SettingsEntity
import app.giveaway.core.data.settings.SettingsRepository
import app.giveaway.core.security.lock.BiometricResult
import app.giveaway.core.security.lock.PinCheck
import app.giveaway.core.security.lock.PinStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppLockSetupViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val settings = FakeSettings()
    private val pinStore = FakePinStore()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(biometrics: Boolean = true) =
        AppLockSetupViewModel({ biometrics }, { pinStore }, { settings }, dispatcher)

    @Test
    fun biometricsAreOfferedOnlyWhenEnrolled() {
        assertTrue(viewModel(biometrics = true).state.value.biometricsAvailable)
        assertFalse(viewModel(biometrics = false).state.value.biometricsAvailable)
    }

    @Test
    fun matchingPinsTurnOnPinLock() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onChoosePin()
        vm.onPinSubmitted("482913")
        assertEquals(AppLockStep.EnterPin(confirming = true), vm.state.value.step)
        vm.onPinSubmitted("482913")
        advanceUntilIdle()
        vm.done.first()
        assertEquals(PinCheck.Correct, pinStore.verify("482913"))
        assertEquals(AppLockMethod.PIN, settings.value.appLockMethod)
        assertTrue(settings.value.appLockEnabled)
    }

    @Test
    fun aMismatchStartsOverWithoutStoringAnything() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onChoosePin()
        vm.onPinSubmitted("482913")
        vm.onPinSubmitted("111111")
        assertEquals(AppLockStep.EnterPin(confirming = false, mismatch = true), vm.state.value.step)
        assertFalse(pinStore.hasPin())
        assertNull(settings.value.appLockMethod)
    }

    @Test
    fun incompletePinsAreIgnored() {
        val vm = viewModel()
        vm.onChoosePin()
        vm.onPinSubmitted("123")
        assertEquals(AppLockStep.EnterPin(confirming = false), vm.state.value.step)
    }

    @Test
    fun backReturnsToTheChoiceAndForgetsTheFirstEntry() {
        val vm = viewModel()
        vm.onChoosePin()
        vm.onPinSubmitted("482913")
        vm.onBack()
        assertEquals(AppLockStep.Choose, vm.state.value.step)
        vm.onChoosePin()
        assertEquals(AppLockStep.EnterPin(confirming = false), vm.state.value.step)
    }

    @Test
    fun aSuccessfulBiometricCheckTurnsOnBiometricLock() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onBiometricResult(BiometricResult.SUCCEEDED)
        advanceUntilIdle()
        vm.done.first()
        assertEquals(AppLockMethod.BIOMETRIC, settings.value.appLockMethod)
    }

    @Test
    fun aFailedBiometricCheckSaysSoAndCancellingDoesNothing() {
        val vm = viewModel()
        vm.onBiometricResult(BiometricResult.CANCELLED)
        assertFalse(vm.state.value.biometricFailed)
        vm.onBiometricResult(BiometricResult.FAILED)
        assertTrue(vm.state.value.biometricFailed)
        assertNull(settings.value.appLockMethod)
    }

    @Test
    fun skippingFinishesWithLockOff() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onSkip()
        vm.done.first()
        assertNull(settings.value.appLockMethod)
        assertFalse(settings.value.appLockEnabled)
    }

    private class FakePinStore : PinStore {
        private var pin: String? = null

        override fun hasPin() = pin != null

        override fun setPin(pin: String) {
            this.pin = pin
        }

        override fun verify(pin: String) = if (pin == this.pin) PinCheck.Correct else PinCheck.Wrong(4)

        override fun clear() {
            pin = null
        }
    }

    private class FakeSettings : SettingsRepository {
        var value = SettingsEntity()

        override fun observe(): Flow<SettingsEntity> = flowOf(value)

        override suspend fun get() = value

        override suspend fun setAppLock(method: AppLockMethod?) {
            value = value.copy(appLockEnabled = method != null, appLockMethod = method)
        }

        override suspend fun update(transform: (SettingsEntity) -> SettingsEntity) {
            value = transform(value)
        }
    }
}
