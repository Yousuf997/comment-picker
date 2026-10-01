package app.giveaway.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giveaway.core.data.account.AccountRepository
import app.giveaway.core.instagram.auth.AuthCallbacks
import app.giveaway.core.instagram.auth.AuthOutcome
import app.giveaway.core.instagram.auth.CallbackResult
import app.giveaway.core.instagram.auth.InstagramAuthenticator
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ConnectEvent {
    /** Open Instagram's authorization page in a Custom Tab. */
    data class OpenBrowser(val url: String) : ConnectEvent

    /** Signed in with a professional account; go to S3. */
    data object SignedIn : ConnectEvent
}

/**
 * Runs sign-in for S2 (spec: Login flow). The pending request's state lives in [SavedStateHandle], so a callback
 * that arrives after the system killed the app while the browser was open still completes.
 */
@HiltViewModel
class ConnectInstagramViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val authenticator: InstagramAuthenticator,
    private val callbacks: AuthCallbacks,
    // Lazy: the encrypted database opens only when an account is saved, not when S2 appears.
    private val accounts: Lazy<AccountRepository>,
) : ViewModel() {

    private val mutableState = MutableStateFlow<ConnectState>(ConnectState.Idle)
    val state: StateFlow<ConnectState> = mutableState.asStateFlow()

    private val eventChannel = Channel<ConnectEvent>(Channel.BUFFERED)
    val events: Flow<ConnectEvent> = eventChannel.receiveAsFlow()

    private val pendingState: String? get() = savedState[KEY_PENDING_STATE]

    init {
        viewModelScope.launch { callbacks.latest.filterNotNull().collect { handleCallback() } }
    }

    fun onContinue() {
        val request = authenticator.newAuthRequest()
        savedState[KEY_PENDING_STATE] = request.state
        mutableState.value = ConnectState.Idle
        eventChannel.trySend(ConnectEvent.OpenBrowser(request.authorizeUri))
    }

    /** The browser couldn't be opened (no browser installed). */
    fun onBrowserUnavailable() {
        savedState.remove<String>(KEY_PENDING_STATE)
        mutableState.value = ConnectState.NetworkError
    }

    /**
     * Called whenever S2 resumes. If the user came back from the browser and no redirect arrived shortly after,
     * they closed Instagram's page: show the cancelled state.
     */
    fun onResumed() {
        if (pendingState == null) return
        viewModelScope.launch {
            delay(CALLBACK_GRACE_MILLIS)
            if (pendingState != null && callbacks.latest.value == null && mutableState.value != ConnectState.Loading) {
                savedState.remove<String>(KEY_PENDING_STATE)
                mutableState.value = ConnectState.Cancelled
            }
        }
    }

    private suspend fun handleCallback() {
        val expected = pendingState
        val callback = callbacks.consume() ?: return
        // A redirect nobody asked for (no pending request) is ignored.
        if (expected == null) return
        savedState.remove<String>(KEY_PENDING_STATE)
        when (val result = authenticator.parseCallback(callback, expected)) {
            is CallbackResult.Code -> finish(result.code)
            // A mismatched state may be a forged redirect: drop the code and let the user start again.
            CallbackResult.Cancelled, CallbackResult.StateMismatch, CallbackResult.Malformed ->
                mutableState.value = ConnectState.Cancelled
        }
    }

    private suspend fun finish(code: String) {
        mutableState.value = ConnectState.Loading
        mutableState.value = when (val outcome = authenticator.complete(code)) {
            is AuthOutcome.Success -> {
                accounts.get().saveSignIn(outcome.token, outcome.account)
                eventChannel.send(ConnectEvent.SignedIn)
                ConnectState.Idle
            }
            AuthOutcome.PersonalAccount -> ConnectState.PersonalAccount
            AuthOutcome.InvalidCode -> ConnectState.Cancelled
            AuthOutcome.Network, AuthOutcome.Failed -> ConnectState.NetworkError
        }
    }

    internal companion object {
        const val KEY_PENDING_STATE = "pendingAuthState"

        /** Time for the App Link to arrive after the browser closes, before assuming the user cancelled. */
        const val CALLBACK_GRACE_MILLIS = 800L
    }
}
