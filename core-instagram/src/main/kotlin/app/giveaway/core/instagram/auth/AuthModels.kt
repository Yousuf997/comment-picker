package app.giveaway.core.instagram.auth

import java.time.Instant

/** Where sign-in goes. Built from the non-secret F-01 settings in gradle.properties. */
data class AuthConfig(
    val appId: String,
    /** The verified App Link Instagram redirects to, e.g. `https://auth.example.com/ig/callback`. */
    val redirectUri: String,
    /** Login helper base URL, ending in a slash. */
    val helperBaseUrl: String,
    /** Instagram Graph base URL including the pinned API version, ending in a slash. */
    val graphBaseUrl: String,
    val authorizeUrl: String = "https://www.instagram.com/oauth/authorize",
    /** Read-only scopes (spec: Permissions requested). Confirm names at build time (plan A3). */
    val scopes: List<String> = listOf("instagram_business_basic", "instagram_business_manage_comments"),
)

/** One sign-in attempt. [state] must come back unchanged in the callback (CSRF protection, plan A2). */
data class AuthRequest(val state: String, val authorizeUri: String)

/** A long-lived Instagram access token. Store it only encrypted with the TOKEN Keystore key. */
data class AuthToken(val accessToken: String, val igUserId: String, val expiresAt: Instant) {
    override fun toString() = "AuthToken(igUserId=$igUserId, expiresAt=$expiresAt)" // never print the token
}

enum class AccountType { BUSINESS, CREATOR, PERSONAL, UNKNOWN }

data class IgAccount(val igUserId: String, val username: String, val accountType: AccountType)

/** What the redirect back from Instagram contained. */
sealed interface CallbackResult {
    data class Code(val code: String) : CallbackResult {
        override fun toString() = "Code(<redacted>)"
    }

    /** The user declined or closed the authorization page. */
    data object Cancelled : CallbackResult

    /** The state didn't match the pending request: possibly a forged redirect. Never exchange the code. */
    data object StateMismatch : CallbackResult

    data object Malformed : CallbackResult
}

/** Outcome of exchanging a code and checking the account. */
sealed interface AuthOutcome {
    data class Success(val token: AuthToken, val account: IgAccount) : AuthOutcome

    /** Instagram's API only works with Business or Creator accounts (spec: Constraints). */
    data object PersonalAccount : AuthOutcome

    /** The code was rejected (expired or already used). Starting over fixes it. */
    data object InvalidCode : AuthOutcome

    /** Instagram or the login helper couldn't be reached. */
    data object Network : AuthOutcome

    /** The helper or Instagram answered with an unexpected error. */
    data object Failed : AuthOutcome
}
