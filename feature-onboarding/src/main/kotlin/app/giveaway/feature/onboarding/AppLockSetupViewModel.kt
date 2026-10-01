package app.giveaway.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giveaway.core.data.db.AppLockMethod
import app.giveaway.core.data.settings.SettingsRepository
import app.giveaway.core.security.lock.BiometricAvailability
import app.giveaway.core.security.lock.BiometricResult
import app.giveaway.core.security.lock.PinStore
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface AppLockStep {
    data object Choose : AppLockStep

    /** Entering a new PIN, or entering it again to confirm. [mismatch] after a failed confirmation. */
    data class EnterPin(val confirming: Boolean, val mismatch: Boolean = false) : AppLockStep

    data object Saving : AppLockStep
}

data class AppLockSetupState(
    val biometricsAvailable: Boolean,
    val step: AppLockStep = AppLockStep.Choose,
    val biometricFailed: Boolean = false,
)

/**
 * S3 (spec: App lock setup). Offers biometrics when enrolled, otherwise a PIN, or skipping. The PIN is held in memory
 * only between the two entries. Storage is injected lazily so S3 can appear before anything is unlocked.
 */
@HiltViewModel
class AppLockSetupViewModel internal constructor(
    biometrics: BiometricAvailability,
    private val pinStore: Lazy<PinStore>,
    private val settings: Lazy<SettingsRepository>,
    private val background: CoroutineDispatcher,
) : ViewModel() {

    @Inject
    constructor(biometrics: BiometricAvailability, pinStore: Lazy<PinStore>, settings: Lazy<SettingsRepository>) :
        this(biometrics, pinStore, settings, Dispatchers.Default)

    private val mutableState = MutableStateFlow(AppLockSetupState(biometricsAvailable = biometrics.canUseBiometrics()))
    val state: StateFlow<AppLockSetupState> = mutableState.asStateFlow()

    private val doneChannel = Channel<Unit>(Channel.BUFFERED)

    /** Emits once setup is finished (lock on, or skipped); go to S4. */
    val done: Flow<Unit> = doneChannel.receiveAsFlow()

    private var firstPin: String? = null

    fun onChoosePin() =
        mutableState.update { it.copy(step = AppLockStep.EnterPin(confirming = false), biometricFailed = false) }

    fun onBack() {
        firstPin = null
        mutableState.update { it.copy(step = AppLockStep.Choose) }
    }

    fun onPinSubmitted(pin: String) {
        if (!PinStore.isValidPin(pin)) return
        val first = firstPin
        when {
            first == null -> {
                firstPin = pin
                mutableState.update { it.copy(step = AppLockStep.EnterPin(confirming = true)) }
            }
            first != pin -> {
                firstPin = null
                mutableState.update { it.copy(step = AppLockStep.EnterPin(confirming = false, mismatch = true)) }
            }
            else -> savePin(pin)
        }
    }

    fun onBiometricResult(result: BiometricResult) {
        when (result) {
            BiometricResult.SUCCEEDED -> finish(AppLockMethod.BIOMETRIC)
            BiometricResult.CANCELLED -> Unit
            BiometricResult.FAILED -> mutableState.update { it.copy(biometricFailed = true) }
        }
    }

    fun onSkip() {
        // App lock is off by default, so skipping stores nothing.
        doneChannel.trySend(Unit)
    }

    private fun savePin(pin: String) {
        firstPin = null
        mutableState.update { it.copy(step = AppLockStep.Saving) }
        viewModelScope.launch {
            withContext(background) { pinStore.get().setPin(pin) }
            finish(AppLockMethod.PIN)
        }
    }

    private fun finish(method: AppLockMethod) {
        viewModelScope.launch {
            withContext(background) { settings.get().setAppLock(method) }
            doneChannel.send(Unit)
        }
    }
}
