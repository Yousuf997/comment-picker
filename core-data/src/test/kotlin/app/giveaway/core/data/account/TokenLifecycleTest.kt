package app.giveaway.core.data.account

import app.giveaway.core.instagram.auth.AuthToken
import app.giveaway.core.instagram.auth.IgAccount
import app.giveaway.core.instagram.network.RefreshResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** C-01 acceptance: refresh timing with a fixed clock, and error 190 leading to the expired state. */
class TokenLifecycleTest {

    private val now = Instant.parse("2026-10-01T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val accounts = FakeAccounts()
    private val refreshCalls = mutableListOf<String>()
    private var refreshResult: RefreshResult = RefreshResult.Refreshed("new-token", now.plus(Duration.ofDays(60)))
    private val lifecycle = TokenLifecycle(accounts, { token -> refreshCalls += token; refreshResult }, clock)

    private fun signedIn(expiresIn: Duration) {
        accounts.token = AuthToken("old-token", "1", now.plus(expiresIn))
    }

    @Test
    fun noAccountMeansNothingToDo() = runTest {
        assertEquals(TokenCheck.NO_ACCOUNT, lifecycle.refreshIfNeeded())
        assertTrue(refreshCalls.isEmpty())
    }

    @Test
    fun aTokenWithMoreThanTenDaysLeftIsLeftAlone() = runTest {
        signedIn(Duration.ofDays(10).plusMinutes(1))
        assertEquals(TokenCheck.VALID, lifecycle.refreshIfNeeded())
        assertTrue(refreshCalls.isEmpty())
    }

    @Test
    fun aTokenInsideTheWindowIsRefreshedAndStored() = runTest {
        signedIn(Duration.ofDays(9))
        assertEquals(TokenCheck.REFRESHED, lifecycle.refreshIfNeeded())
        assertEquals(listOf("old-token"), refreshCalls)
        assertEquals("new-token", accounts.token?.accessToken)
        assertEquals(now.plus(Duration.ofDays(60)), accounts.token?.expiresAt)
    }

    @Test
    fun exactlyTenDaysLeftTriggersARefresh() = runTest {
        signedIn(Duration.ofDays(10))
        assertEquals(TokenCheck.REFRESHED, lifecycle.refreshIfNeeded())
    }

    @Test
    fun aRejectedTokenIsMarkedRevokedAndReportedExpired() = runTest {
        signedIn(Duration.ofDays(2))
        refreshResult = RefreshResult.Rejected
        assertEquals(TokenCheck.EXPIRED, lifecycle.refreshIfNeeded())
        assertTrue(accounts.revoked)
        assertEquals("old-token", accounts.token?.accessToken)
    }

    @Test
    fun aRevokedTokenIsNotSentToInstagramAgain() = runTest {
        signedIn(Duration.ofDays(2))
        accounts.revoked = true
        assertEquals(TokenCheck.EXPIRED, lifecycle.refreshIfNeeded())
        assertTrue(refreshCalls.isEmpty())
    }

    @Test
    fun temporaryFailuresAreRetriedLater() = runTest {
        signedIn(Duration.ofDays(2))
        refreshResult = RefreshResult.RetryLater
        assertEquals(TokenCheck.RETRY_LATER, lifecycle.refreshIfNeeded())
        assertFalse(accounts.revoked)
    }

    private class FakeAccounts : AccountRepository {
        var token: AuthToken? = null
        var revoked = false

        override suspend fun saveSignIn(token: AuthToken, account: IgAccount) {
            this.token = token
        }

        override suspend fun token() = token

        override suspend fun updateToken(accessToken: String, expiresAt: Instant) {
            token = token?.copy(accessToken = accessToken, expiresAt = expiresAt)
            revoked = false
        }

        override suspend fun markTokenRevoked() {
            revoked = true
        }

        override suspend fun isTokenRevoked() = revoked

        override fun observeSignInState(): Flow<SignInState> = emptyFlow()

        override fun observeUsername(): Flow<String?> = emptyFlow()

        override suspend fun signOut() {
            token = null
        }
    }
}
