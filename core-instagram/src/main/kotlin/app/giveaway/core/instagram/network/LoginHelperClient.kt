package app.giveaway.core.instagram.network

import app.giveaway.core.instagram.auth.AuthConfig
import app.giveaway.core.instagram.auth.AuthToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Clock
import javax.inject.Inject

sealed interface TokenResult {
    data class Success(val token: AuthToken) : TokenResult
    data object InvalidCode : TokenResult
    data object Network : TokenResult
    data object Failed : TokenResult
}

/** What the helper's integrity check said about a draw (plan A6): booleans only. */
@Serializable
data class DrawIntegrityVerdict(val appRecognized: Boolean, val deviceIntegrity: Boolean, val hashMatches: Boolean)

/** Calls the login helper, the only server of ours the app talks to (spec: Architecture). */
class LoginHelperClient @Inject constructor(
    private val http: OkHttpClient,
    private val config: AuthConfig,
    private val clock: Clock,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun exchange(code: String, integrityToken: String? = null): TokenResult = withContext(Dispatchers.IO) {
        val body = json.encodeToString(TokenRequest.serializer(), TokenRequest(code, integrityToken))
        val request = Request.Builder()
            .url("${config.helperBaseUrl}v1/token")
            .post(body.toRequestBody(JSON))
            .build()
        try {
            http.newCall(request).execute().use { response ->
                val text = response.body.string()
                when {
                    response.isSuccessful -> parse(text)
                    response.code == HTTP_BAD_REQUEST && text.contains("invalid_code") -> TokenResult.InvalidCode
                    else -> TokenResult.Failed
                }
            }
        } catch (expected: IOException) {
            TokenResult.Network
        }
    }

    /** `POST /v1/integrity`: has a Standard integrity token decoded; null if the check couldn't run. */
    suspend fun drawIntegrity(token: String, requestHash: String): DrawIntegrityVerdict? = withContext(Dispatchers.IO) {
        val body = json.encodeToString(IntegrityRequest.serializer(), IntegrityRequest(token, requestHash))
        val request = Request.Builder()
            .url("${config.helperBaseUrl}v1/integrity")
            .post(body.toRequestBody(JSON))
            .build()
        try {
            http.newCall(request).execute().use { response ->
                val text = response.body.string()
                if (!response.isSuccessful) return@use null
                runCatching { json.decodeFromString(DrawIntegrityVerdict.serializer(), text) }.getOrNull()
            }
        } catch (expected: IOException) {
            null
        }
    }

    private fun parse(text: String): TokenResult = runCatching {
        val response = json.decodeFromString(TokenResponse.serializer(), text)
        TokenResult.Success(
            AuthToken(
                accessToken = response.accessToken,
                igUserId = response.userId,
                expiresAt = clock.instant().plusSeconds(response.expiresIn),
            ),
        )
    }.getOrDefault(TokenResult.Failed)

    @Serializable
    private data class TokenRequest(val code: String, val integrityToken: String?)

    @Serializable
    private data class IntegrityRequest(val token: String, val requestHash: String)

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in") val expiresIn: Long,
        @SerialName("user_id") val userId: String,
    )

    private companion object {
        val JSON = "application/json".toMediaType()
        const val HTTP_BAD_REQUEST = 400
    }
}
