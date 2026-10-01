package app.giveaway.core.instagram.network

import app.giveaway.core.instagram.auth.AccountType
import app.giveaway.core.instagram.auth.AuthConfig
import app.giveaway.core.instagram.auth.IgAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject

sealed interface ProfileResult {
    data class Success(val account: IgAccount) : ProfileResult
    data object Network : ProfileResult
    data object Failed : ProfileResult
}

/**
 * Reads the signed-in account (`/me`) to get the username and check it is a professional account.
 * The full Instagram API client arrives with C-02 and will absorb this call.
 */
class InstagramProfileClient @Inject constructor(
    private val http: OkHttpClient,
    private val config: AuthConfig,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun me(accessToken: String): ProfileResult = withContext(Dispatchers.IO) {
        val url = "${config.graphBaseUrl}me".toHttpUrl().newBuilder()
            .addQueryParameter("fields", "user_id,username,account_type")
            .addQueryParameter("access_token", accessToken)
            .build()
        try {
            http.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) return@use ProfileResult.Failed
                runCatching {
                    val me = json.decodeFromString(MeResponse.serializer(), response.body.string())
                    ProfileResult.Success(IgAccount(me.userId, me.username, accountType(me.accountType)))
                }.getOrDefault(ProfileResult.Failed)
            }
        } catch (expected: IOException) {
            ProfileResult.Network
        }
    }

    private fun accountType(value: String?) = when (value?.uppercase()) {
        "BUSINESS" -> AccountType.BUSINESS
        "MEDIA_CREATOR", "CREATOR" -> AccountType.CREATOR
        "PERSONAL" -> AccountType.PERSONAL
        else -> AccountType.UNKNOWN
    }

    @Serializable
    private data class MeResponse(
        @SerialName("user_id") val userId: String,
        val username: String,
        @SerialName("account_type") val accountType: String? = null,
    )
}
