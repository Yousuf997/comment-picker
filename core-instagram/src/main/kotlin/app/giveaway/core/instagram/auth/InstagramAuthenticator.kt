package app.giveaway.core.instagram.auth

import app.giveaway.core.instagram.network.InstagramProfileClient
import app.giveaway.core.instagram.network.LoginHelperClient
import app.giveaway.core.instagram.network.ProfileResult
import app.giveaway.core.instagram.network.TokenResult
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject

/** Sign-in with Instagram Login (spec: Login flow). */
interface InstagramAuthenticator {
    /** A new attempt with a fresh random state; open [AuthRequest.authorizeUri] in a Custom Tab. */
    fun newAuthRequest(): AuthRequest

    /** Reads the App Link Instagram redirected to. [expectedState] is the pending request's state. */
    fun parseCallback(callbackUri: String, expectedState: String): CallbackResult

    /** Exchanges the code through the login helper and checks the account type. */
    suspend fun complete(code: String): AuthOutcome
}

class DefaultInstagramAuthenticator @Inject constructor(
    private val config: AuthConfig,
    private val helper: LoginHelperClient,
    private val profile: InstagramProfileClient,
) : InstagramAuthenticator {

    private val random = SecureRandom()

    override fun newAuthRequest(): AuthRequest {
        val stateBytes = ByteArray(STATE_BYTES).also(random::nextBytes)
        val state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes)
        val query = listOf(
            "client_id" to config.appId,
            "redirect_uri" to config.redirectUri,
            "response_type" to "code",
            "scope" to config.scopes.joinToString(","),
            "state" to state,
        ).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, UTF_8)}" }
        return AuthRequest(state, "${config.authorizeUrl}?$query")
    }

    override fun parseCallback(callbackUri: String, expectedState: String): CallbackResult {
        val uri = runCatching { URI(callbackUri) }.getOrNull() ?: return CallbackResult.Malformed
        if ("${uri.scheme}://${uri.host}${uri.path}" != config.redirectUri) return CallbackResult.Malformed
        val params = uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.associate { pair ->
            val key = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            URLDecoder.decode(key, UTF_8) to URLDecoder.decode(value, UTF_8)
        }
        // A cancel can't be abused: at worst it stops a sign-in, so it doesn't need a valid state.
        if (params["error"] != null) return CallbackResult.Cancelled
        val state = params["state"] ?: return CallbackResult.StateMismatch
        val matches = MessageDigest.isEqual(state.toByteArray(), expectedState.toByteArray())
        if (!matches) return CallbackResult.StateMismatch
        // Instagram appends "#_" to the redirect; it is a fragment, so it is not part of the code.
        val code = params["code"]?.takeIf { it.isNotBlank() } ?: return CallbackResult.Malformed
        return CallbackResult.Code(code)
    }

    override suspend fun complete(code: String): AuthOutcome {
        val token = when (val result = helper.exchange(code)) {
            is TokenResult.Success -> result.token
            TokenResult.InvalidCode -> return AuthOutcome.InvalidCode
            TokenResult.Network -> return AuthOutcome.Network
            TokenResult.Failed -> return AuthOutcome.Failed
        }
        val account = when (val result = profile.me(token.accessToken)) {
            is ProfileResult.Success -> result.account
            ProfileResult.Network -> return AuthOutcome.Network
            ProfileResult.Failed -> return AuthOutcome.Failed
        }
        if (account.accountType == AccountType.PERSONAL) return AuthOutcome.PersonalAccount
        return AuthOutcome.Success(token, account)
    }

    private companion object {
        const val STATE_BYTES = 32

        // Charset overloads of URLEncoder/URLDecoder need API 33; minSdk is 26.
        const val UTF_8 = "UTF-8"
    }
}
