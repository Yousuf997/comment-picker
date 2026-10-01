package app.giveaway.core.instagram.di

import app.giveaway.core.instagram.BuildConfig
import app.giveaway.core.instagram.api.DefaultInstagramRepository
import app.giveaway.core.instagram.api.GraphService
import app.giveaway.core.instagram.api.InstagramRepository
import app.giveaway.core.instagram.auth.AuthConfig
import app.giveaway.core.instagram.auth.DefaultInstagramAuthenticator
import app.giveaway.core.instagram.auth.InstagramAuthenticator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.CertificatePinner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Clock
import java.time.Duration
import javax.inject.Singleton

/** Instagram Graph API version, pinned so Meta changes arrive only when we choose (plan R6). Confirm at build time. */
const val GRAPH_API_VERSION = "v24.0"

@Module
@InstallIn(SingletonComponent::class)
internal abstract class InstagramModule {

    @Binds
    abstract fun authenticator(impl: DefaultInstagramAuthenticator): InstagramAuthenticator

    @Binds
    abstract fun instagramRepository(impl: DefaultInstagramRepository): InstagramRepository

    companion object {
        @Provides
        fun authConfig() = AuthConfig(
            appId = BuildConfig.IG_APP_ID,
            redirectUri = "https://${BuildConfig.AUTH_HOST}/ig/callback",
            helperBaseUrl = "https://${BuildConfig.AUTH_HOST}/",
            graphBaseUrl = "https://graph.instagram.com/$GRAPH_API_VERSION/",
        )

        @Provides
        fun clock(): Clock = Clock.systemUTC()

        /** HTTPS only (cleartext is off app-wide); the login helper is pinned when pins are configured (F-13). */
        @Provides
        @Singleton
        fun okHttp(): OkHttpClient {
            val pins = BuildConfig.AUTH_HOST_PINS.split(',').map(String::trim).filter(String::isNotEmpty)
            return OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(CALL_TIMEOUT_SECONDS))
                .apply {
                    if (pins.isNotEmpty()) {
                        val pinner = CertificatePinner.Builder()
                            .add(BuildConfig.AUTH_HOST, *pins.toTypedArray())
                            .build()
                        certificatePinner(pinner)
                    }
                }
                .build()
        }

        @Provides
        @Singleton
        internal fun graphService(http: OkHttpClient, config: AuthConfig): GraphService = Retrofit.Builder()
            .baseUrl(config.graphBaseUrl)
            .client(http)
            .addConverterFactory(graphJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GraphService::class.java)

        private const val CALL_TIMEOUT_SECONDS = 30L
        private val graphJson = Json { ignoreUnknownKeys = true }
    }
}
