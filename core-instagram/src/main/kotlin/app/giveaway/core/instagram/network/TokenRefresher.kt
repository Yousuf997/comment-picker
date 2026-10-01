package app.giveaway.core.instagram.network

import app.giveaway.core.instagram.auth.AuthConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

sealed interface RefreshResult {
    data class Refreshed(val accessToken: String, val expiresAt: Instant) : RefreshResult {
        override fun toString() = "Refreshed(expiresAt=$expiresAt)" // never print the token
    }

    /** Instagram rejected the token (expired or revoked, error 190): the user must sign in again. */
    data object Rejected : RefreshResult

    /** Offline or a temporary failure: try again later. */
    data object RetryLater : RefreshResult
}

/** Extends a long-lived token before it expires (spec: Login flow, step 5). */
fun interface TokenRefresher {
    suspend fun refresh(accessToken: String): RefreshResult
}

/** Refreshing needs no app secret, so it goes straight to Instagram, not through the login helper (plan A4). */
class InstagramTokenRefresher @Inject constructor(
    private val http: OkHttpClient,
    private val config: AuthConfig,
    private val clock: Clock,
) : TokenRefresher {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun refresh(accessToken: String): RefreshResult = withContext(Dispatchers.IO) {
        val url = config.refreshUrl.toHttpUrl().newBuilder()
            .addQueryParameter("grant_type", "ig_refresh_token")
            .addQueryParameter("access_token", accessToken)
            .build()
        try {
            http.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                val text = response.body.string()
                when {
                    response.isSuccessful -> runCatching {
                        val body = json.decodeFromString(RefreshResponse.serializer(), text)
                        RefreshResult.Refreshed(body.accessToken, clock.instant().plusSeconds(body.expiresIn))
                    }.getOrDefault(RefreshResult.RetryLater)
                    text.contains("\"code\":190") || response.code == HTTP_UNAUTHORIZED -> RefreshResult.Rejected
                    else -> RefreshResult.RetryLater
                }
            }
        } catch (expected: IOException) {
            RefreshResult.RetryLater
        }
    }

    @Serializable
    private data class RefreshResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in") val expiresIn: Long,
    )

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
    }
}
