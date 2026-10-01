package app.giveaway.core.instagram.auth

import app.giveaway.core.instagram.network.InstagramProfileClient
import app.giveaway.core.instagram.network.DrawIntegrityVerdict
import app.giveaway.core.instagram.network.LoginHelperClient
import app.giveaway.core.security.integrity.PlayIntegrity
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

    private companion object {
        const val CALLBACK = "https://auth.example.test/ig/callback"
    }

    private lateinit var config: AuthConfig
    private val nonces = mutableListOf<String>()
    private var integrityToken: String? = "itk"
    private val integrity = object : PlayIntegrity {
        override suspend fun classicToken(nonce: String): String? = integrityToken.also { nonces += nonce }

        override suspend fun standardToken(requestHash: String): String? = null
    }

    @Before
    fun setUp() {
        server.start()
        val base = server.url("/").toString()
        config = AuthConfig(
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
            integrity,
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
        val result = auth.parseCallback("$CALLBACK?code=x", "s1")
        assertEquals(CallbackResult.StateMismatch, result)
    }

    @Test
    fun declineIsReportedAsCancelled() {
        val uri = "https://auth.example.test/ig/callback?error=access_denied&error_reason=user_denied&state=s1"
        assertEquals(CallbackResult.Cancelled, auth.parseCallback(uri, "s1"))
    }

    @Test
    fun otherHostsAndMissingCodesAreMalformed() {
        listOf("https://evil.test/ig/callback?code=x&state=s1", "$CALLBACK?state=s1", "not a uri ::").forEach {
            assertEquals(it, CallbackResult.Malformed, auth.parseCallback(it, "s1"))
        }
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
        // The Play Integrity token travels with the code, bound to it by its nonce (plan A2).
        assertEquals("""{"code":"AQB123","integrityToken":"itk"}""", tokenCall.body?.utf8())
        assertEquals(listOf(DefaultInstagramAuthenticator.nonceFor("AQB123")), nonces)
        assertEquals(43, nonces.single().length)
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
    fun withoutPlayServicesTheCodeGoesWithoutAToken() = runTest {
        integrityToken = null
        json("""{"error":"integrity_failed"}""", code = 403)
        assertEquals(AuthOutcome.Failed, auth.complete("c"))
        assertEquals("""{"code":"c","integrityToken":null}""", server.takeRequest().body?.utf8())
    }

    @Test
    fun theDrawCheckReturnsTheHelpersBooleans() = runTest {
        json("""{"appRecognized":true,"deviceIntegrity":false,"hashMatches":true}""")
        val helper = LoginHelperClient(OkHttpClient(), config, Clock.fixed(now, ZoneOffset.UTC))
        assertEquals(DrawIntegrityVerdict(true, false, true), helper.drawIntegrity("std", "hash"))
        val call = server.takeRequest()
        assertEquals("/v1/integrity", call.url.encodedPath)
        assertEquals("""{"token":"std","requestHash":"hash"}""", call.body?.utf8())
        json("""{"error":"integrity_unavailable"}""", code = 503)
        assertEquals(null, helper.drawIntegrity("std", "hash"))
    }

    @Test
    fun anUnreachableHelperIsANetworkError() = runTest {
        server.close()
        assertEquals(AuthOutcome.Network, auth.complete("c"))
    }
}
