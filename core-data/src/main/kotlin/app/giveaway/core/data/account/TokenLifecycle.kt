package app.giveaway.core.data.account

import app.giveaway.core.instagram.network.RefreshResult
import app.giveaway.core.instagram.network.TokenRefresher
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

enum class TokenCheck { NO_ACCOUNT, VALID, REFRESHED, EXPIRED, RETRY_LATER }

/**
 * Keeps the long-lived token alive (spec: Login flow, step 5; plan C-01). Run daily: once fewer than
 * [REFRESH_WINDOW] remain, the token is refreshed directly with Instagram. If Instagram rejects it, the account is
 * marked revoked and the user is asked to sign in again; giveaway data is never touched.
 */
class TokenLifecycle @Inject constructor(
    private val accounts: AccountRepository,
    private val refresher: TokenRefresher,
    private val clock: Clock,
) {
    suspend fun refreshIfNeeded(): TokenCheck {
        val token = accounts.token() ?: return TokenCheck.NO_ACCOUNT
        if (accounts.isTokenRevoked()) return TokenCheck.EXPIRED
        val remaining = Duration.between(clock.instant(), token.expiresAt)
        if (remaining > REFRESH_WINDOW) return TokenCheck.VALID
        return when (val result = refresher.refresh(token.accessToken)) {
            is RefreshResult.Refreshed -> {
                accounts.updateToken(result.accessToken, result.expiresAt)
                TokenCheck.REFRESHED
            }
            RefreshResult.Rejected -> {
                accounts.markTokenRevoked()
                TokenCheck.EXPIRED
            }
            RefreshResult.RetryLater -> TokenCheck.RETRY_LATER
        }
    }

    companion object {
        /** Refresh once fewer than 10 days are left (plan A4). Tokens must be at least a day old to refresh. */
        val REFRESH_WINDOW: Duration = Duration.ofDays(10)
    }
}
