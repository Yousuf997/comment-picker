package app.giveaway.feature.onboarding

import androidx.lifecycle.SavedStateHandle
import app.giveaway.core.data.account.AccountRepository
import app.giveaway.core.instagram.auth.AccountType
import app.giveaway.core.instagram.auth.AuthCallbacks
import app.giveaway.core.instagram.auth.AuthOutcome
import app.giveaway.core.instagram.auth.AuthRequest
import app.giveaway.core.instagram.auth.AuthToken
import app.giveaway.core.instagram.auth.CallbackResult
import app.giveaway.core.instagram.auth.IgAccount
import app.giveaway.core.instagram.auth.InstagramAuthenticator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectInstagramViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val callbacks = AuthCallbacks()
    private val auth = FakeAuthenticator()
    private val accounts = FakeAccounts()
    private val savedState = SavedStateHandle()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(state: SavedStateHandle = savedState) =
        ConnectInstagramViewModel(state, auth, callbacks, { accounts })

    private fun TestScope.startSignIn(vm: ConnectInstagramViewModel): String {
        vm.onContinue()
        advanceUntilIdle()
        return requireNotNull(savedState.get<String>(ConnectInstagramViewModel.KEY_PENDING_STATE))
    }

    @Test
    fun continueOpensInstagramsPageAndRemembersTheState() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onContinue()
        assertEquals(ConnectEvent.OpenBrowser("https://instagram.test/authorize?state=state-1"), vm.events.first())
        assertEquals("state-1", savedState.get<String>(ConnectInstagramViewModel.KEY_PENDING_STATE))
    }

    @Test
    fun aValidCallbackSignsInAndStoresTheAccount() = runTest(dispatcher) {
        val vm = viewModel()
        startSignIn(vm)
        callbacks.deliver("code-ok")
        advanceUntilIdle()
        assertEquals(ConnectEvent.SignedIn, vm.events.first { it is ConnectEvent.SignedIn })
        assertEquals("shop", accounts.saved?.second?.username)
        assertNull(savedState.get<String>(ConnectInstagramViewModel.KEY_PENDING_STATE))
    }

    @Test
    fun declineShowsCancelled() = runTest(dispatcher) {
        val vm = viewModel()
        startSignIn(vm)
        callbacks.deliver("denied")
        advanceUntilIdle()
        assertEquals(ConnectState.Cancelled, vm.state.value)
    }

    @Test
    fun aMismatchedStateNeverExchangesTheCode() = runTest(dispatcher) {
        val vm = viewModel()
        startSignIn(vm)
        callbacks.deliver("forged")
        advanceUntilIdle()
        assertEquals(ConnectState.Cancelled, vm.state.value)
        assertTrue(auth.completedCodes.isEmpty())
    }

    @Test
    fun personalAccountsAreExplainedAndNotStored() = runTest(dispatcher) {
        val vm = viewModel()
        startSignIn(vm)
        callbacks.deliver("code-personal")
        advanceUntilIdle()
        assertEquals(ConnectState.PersonalAccount, vm.state.value)
        assertNull(accounts.saved)
    }

    @Test
    fun helperFailuresShowTheNetworkError() = runTest(dispatcher) {
        val vm = viewModel()
        startSignIn(vm)
        callbacks.deliver("code-network")
        advanceUntilIdle()
        assertEquals(ConnectState.NetworkError, vm.state.value)
    }

    @Test
    fun returningWithoutARedirectMeansTheUserClosedThePage() = runTest(dispatcher) {
        val vm = viewModel()
        startSignIn(vm)
        vm.onResumed()
        advanceTimeBy(ConnectInstagramViewModel.CALLBACK_GRACE_MILLIS + 1)
        assertEquals(ConnectState.Cancelled, vm.state.value)
    }

    @Test
    fun aRedirectArrivingAfterTheProcessWasKilledStillCompletes() = runTest(dispatcher) {
        startSignIn(viewModel())
        // The system recreates the ViewModel with the saved state; the callback comes in afterwards.
        val restored = viewModel(SavedStateHandle(mapOf(ConnectInstagramViewModel.KEY_PENDING_STATE to "state-1")))
        callbacks.deliver("code-ok")
        advanceUntilIdle()
        assertEquals(ConnectEvent.SignedIn, restored.events.first { it is ConnectEvent.SignedIn })
    }

    @Test
    fun aRedirectNobodyAskedForIsIgnored() = runTest(dispatcher) {
        val vm = viewModel()
        callbacks.deliver("code-ok")
        advanceUntilIdle()
        assertEquals(ConnectState.Idle, vm.state.value)
        assertTrue(auth.completedCodes.isEmpty())
        assertNull(callbacks.latest.value)
    }

    @Test
    fun noBrowserShowsTheNetworkError() = runTest(dispatcher) {
        val vm = viewModel()
        startSignIn(vm)
        vm.onBrowserUnavailable()
        assertEquals(ConnectState.NetworkError, vm.state.value)
    }

    /** Callback "URIs" are short labels; the fake maps them to results. */
    private class FakeAuthenticator : InstagramAuthenticator {
        val completedCodes = mutableListOf<String>()

        override fun newAuthRequest() = AuthRequest("state-1", "https://instagram.test/authorize?state=state-1")

        override fun parseCallback(callbackUri: String, expectedState: String) = when {
            expectedState != "state-1" -> CallbackResult.StateMismatch
            callbackUri == "denied" -> CallbackResult.Cancelled
            callbackUri == "forged" -> CallbackResult.StateMismatch
            else -> CallbackResult.Code(callbackUri)
        }

        override suspend fun complete(code: String): AuthOutcome {
            completedCodes += code
            return when (code) {
                "code-ok" -> AuthOutcome.Success(TOKEN, IgAccount("1789", "shop", AccountType.BUSINESS))
                "code-personal" -> AuthOutcome.PersonalAccount
                else -> AuthOutcome.Network
            }
        }
    }

    private class FakeAccounts : AccountRepository {
        var saved: Pair<AuthToken, IgAccount>? = null

        override suspend fun saveSignIn(token: AuthToken, account: IgAccount) {
            saved = token to account
        }

        override suspend fun token() = saved?.first

        override fun observeUsername(): Flow<String?> = flowOf(saved?.second?.username)

        override suspend fun signOut() {
            saved = null
        }
    }

    private companion object {
        val TOKEN = AuthToken("long", "1789", Instant.parse("2026-12-01T00:00:00Z"))
    }
}
