package app.giveaway.core.instagram.auth

import app.giveaway.core.instagram.network.InstagramProfileClient
import app.giveaway.core.instagram.network.LoginHelperClient
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URI
import java.net.URLDecoder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** F-12 acceptance: authorize request, callback validation (state mismatch, cancel) and helper errors. */
class InstagramAuthenticatorTest {

    private val server = MockWebServer()
    private lateinit var auth: DefaultInstagramAuthenticator
    private val now = Instant.parse("2026-10-01T12:00:00Z")

    @Before
    fun setUp() {
        server.start()
        val base = server.url("/").toString()
        val config = AuthConfig(
            appId = "1234",
            redirectUri = "https://auth.example.test/ig/callback",
            helperBaseUrl = base,
            graphBaseUrl = "${base}v24.0/",
        )
        val http = OkHttpClient()
        auth = DefaultInstagramAuthenticator(
            config,
            LoginHelperClient(http, config, Clock.fixed(now, ZoneOffset.UTC)),
            InstagramProfileClient(http, config),
        )
    }

    @After
    fun tearDown() = server.close()

    private fun json(body: String, code: Int = 200) =
        server.enqueue(MockResponse.Builder().code(code).body(body).build())

    // --- Authorize request ---

    @Test
    fun authorizeUrlCarriesClientRedirectReadOnlyScopesAndState() {
        val request = auth.newAuthRequest()
        val uri = URI(request.authorizeUri)
        val params = uri.rawQuery.split('&').associate {
            it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8")
        }
        assertEquals("www.instagram.com", uri.host)
        assertEquals("/oauth/authorize", uri.path)
        assertEquals("1234", params["client_id"])
        assertEquals("https://auth.example.test/ig/callback", params["redirect_uri"])
        assertEquals("code", params["response_type"])
        assertEquals("instagram_business_basic,instagram_business_manage_comments", params["scope"])
        assertEquals(request.state, params["state"])
        assertTrue("State should be 32 random bytes", request.state.length >= 43)
    }

    @Test
    fun everyRequestHasAFreshState() {
        assertNotEquals(auth.newAuthRequest().state, auth.newAuthRequest().state)
    }

    // --- Callback ---

    @Test
    fun aMatchingCallbackYieldsTheCodeWithoutTheTrailingFragment() {
        val result = auth.parseCallback("https://auth.example.test/ig/callback?code=AQB123&state=s1#_", "s1")
        assertEquals(CallbackResult.Code("AQB123"), result)
    }

    @Test
    fun aDifferentStateIsRejected() {
        val result = auth.parseCallback("https://auth.example.test/ig/callback?code=AQB123&state=forged", "s1")
        assertEquals(CallbackResult.StateMismatch, result)
    }

    @Test
    fun aMissingStateIsRejected() {
        assertEquals(CallbackResult.StateMismatch, auth.parseCallback("https://auth.example.test/ig/callback?code=x", "s1"))
    }

    @Test
    fun declineIsReportedAsCancelled() {
        val uri = "https://auth.example.test/ig/callback?error=access_denied&error_reason=user_denied&state=s1"
        assertEquals(CallbackResult.Cancelled, auth.parseCallback(uri, "s1"))
    }

    @Test
    fun otherHostsAndMissingCodesAreMalformed() {
        assertEquals(CallbackResult.Malformed, auth.parseCallback("https://evil.test/ig/callback?code=x&state=s1", "s1"))
        assertEquals(CallbackResult.Malformed, auth.parseCallback("https://auth.example.test/ig/callback?state=s1", "s1"))
        assertEquals(CallbackResult.Malformed, auth.parseCallback("not a uri ::", "s1"))
    }

    @Test
    fun codesAreRedactedFromLogs() {
        assertFalse(CallbackResult.Code("secret-code").toString().contains("secret-code"))
        assertFalse(AuthToken("secret-token", "1", now).toString().contains("secret-token"))
    }

    // --- Exchange ---

    @Test
    fun successExchangesTheCodeAndChecksTheAccount() = runTest {
        json("""{"access_token":"long","expires_in":5184000,"user_id":"1789"}""")
        json("""{"user_id":"1789","username":"shop","account_type":"BUSINESS"}""")

        val outcome = auth.complete("AQB123")

        assertEquals(
            AuthOutcome.Success(
                AuthToken("long", "1789", now.plusSeconds(5184000)),
                IgAccount("1789", "shop", AccountType.BUSINESS),
            ),
            outcome,
        )
        val tokenCall = server.takeRequest()
        assertEquals("/v1/token", tokenCall.url.encodedPath)
        assertEquals("""{"code":"AQB123","integrityToken":null}""", tokenCall.body?.utf8())
        val meCall = server.takeRequest()
        assertEquals("/v24.0/me", meCall.url.encodedPath)
        assertEquals("long", meCall.url.queryParameter("access_token"))
    }

    @Test
    fun creatorAccountsAreAccepted() = runTest {
        json("""{"access_token":"long","expires_in":60,"user_id":"1"}""")
        json("""{"user_id":"1","username":"maker","account_type":"MEDIA_CREATOR"}""")
        val outcome = auth.complete("c") as AuthOutcome.Success
        assertEquals(AccountType.CREATOR, outcome.account.accountType)
    }

    @Test
    fun personalAccountsAreReported() = runTest {
        json("""{"access_token":"long","expires_in":60,"user_id":"1"}""")
        json("""{"user_id":"1","username":"me","account_type":"PERSONAL"}""")
        assertEquals(AuthOutcome.PersonalAccount, auth.complete("c"))
    }

    @Test
    fun aRejectedCodeIsInvalidCode() = runTest {
        json("""{"error":"invalid_code"}""", code = 400)
        assertEquals(AuthOutcome.InvalidCode, auth.complete("expired"))
    }

    @Test
    fun helperErrorsAreFailures() = runTest {
        json("""{"error":"exchange_failed"}""", code = 502)
        assertEquals(AuthOutcome.Failed, auth.complete("c"))
        json("""{"error":"integrity_unavailable"}""", code = 503)
        assertEquals(AuthOutcome.Failed, auth.complete("c"))
        json("not json")
        assertEquals(AuthOutcome.Failed, auth.complete("c"))
    }

    @Test
    fun anUnreachableHelperIsANetworkError() = runTest {
        server.close()
        assertEquals(AuthOutcome.Network, auth.complete("c"))
    }
}
