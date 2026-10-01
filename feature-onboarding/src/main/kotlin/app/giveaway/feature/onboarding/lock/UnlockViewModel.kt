package app.giveaway.feature.onboarding.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giveaway.core.data.db.AppLockMethod
import app.giveaway.core.security.lock.BiometricResult
import app.giveaway.core.security.lock.PinCheck
import app.giveaway.core.security.lock.PinStore
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject

data class UnlockState(
    val method: AppLockMethod?,
    val checking: Boolean = false,
    /** Tries left before a lockout, after a wrong PIN. */
    val attemptsLeft: Int? = null,
    /** Entry is blocked until this time after too many wrong PINs. */
    val lockedUntil: Instant? = null,
    val biometricFailed: Boolean = false,
)

/** The lock screen: unlock with biometrics or the PIN (with its lockout), then hand back to [AppLockController]. */
@HiltViewModel
class UnlockViewModel internal constructor(
    private val controller: AppLockController,
    private val pinStore: Lazy<PinStore>,
    private val background: CoroutineDispatcher,
) : ViewModel() {

    @Inject
    constructor(controller: AppLockController, pinStore: Lazy<PinStore>) :
        this(controller, pinStore, Dispatchers.Default)

    private val mutableState = MutableStateFlow(UnlockState(method = controller.method))
    val state: StateFlow<UnlockState> = mutableState.asStateFlow()

    fun onPinSubmitted(pin: String) {
        if (!PinStore.isValidPin(pin) || mutableState.value.checking) return
        mutableState.update { it.copy(checking = true) }
        viewModelScope.launch {
            val result = withContext(background) { pinStore.get().verify(pin) }
            mutableState.update { it.copy(checking = false) }
            when (result) {
                PinCheck.Correct -> controller.onUnlocked()
                is PinCheck.Wrong ->
                    mutableState.update { it.copy(attemptsLeft = result.attemptsBeforeLockout, lockedUntil = null) }
                is PinCheck.LockedOut ->
                    mutableState.update { it.copy(attemptsLeft = null, lockedUntil = result.until) }
            }
        }
    }

    fun onBiometricResult(result: BiometricResult) {
        when (result) {
            BiometricResult.SUCCEEDED -> controller.onUnlocked()
            BiometricResult.CANCELLED -> Unit
            BiometricResult.FAILED -> mutableState.update { it.copy(biometricFailed = true) }
        }
    }
}
