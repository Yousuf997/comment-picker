package app.giveaway.core.security.integrity

import android.content.Context
import app.giveaway.core.security.BuildConfig
import com.google.android.gms.tasks.Task
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Play Integrity tokens (spec: App hardening, "Play Integrity check at login and before a real draw"). The tokens are
 * opaque to the app; the login helper has them decoded. Null means Play services couldn't provide one (no Play Store,
 * offline, or not set up), which the caller treats as "not verified".
 */
interface PlayIntegrity {
    /** A classic token bound to [nonce] (unpadded base64url, 16 to 500 characters). */
    suspend fun classicToken(nonce: String): String?

    /** A Standard API token bound to [requestHash]. */
    suspend fun standardToken(requestHash: String): String?
}

internal class GooglePlayIntegrity @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlayIntegrity {

    override suspend fun classicToken(nonce: String): String? = runCatching {
        val request = IntegrityTokenRequest.builder()
            .setNonce(nonce)
            .apply { if (PROJECT != 0L) setCloudProjectNumber(PROJECT) }
            .build()
        IntegrityManagerFactory.create(context).requestIntegrityToken(request).await().token()
    }.getOrNull()

    override suspend fun standardToken(requestHash: String): String? {
        // The Standard API needs the Cloud project (F-01); without it the check is simply "not verified".
        if (PROJECT == 0L) return null
        return runCatching {
            val prepare = PrepareIntegrityTokenRequest.builder().setCloudProjectNumber(PROJECT).build()
            val provider = IntegrityManagerFactory.createStandard(context).prepareIntegrityToken(prepare).await()
            val request = StandardIntegrityTokenRequest.builder().setRequestHash(requestHash).build()
            provider.request(request).await().token()
        }.getOrNull()
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
    }

    private companion object {
        val PROJECT: Long = BuildConfig.CLOUD_PROJECT_NUMBER
    }
}

/** Replaced in UI tests, where Play services don't exist. */
@Module
@InstallIn(SingletonComponent::class)
abstract class IntegrityModule {
    @Binds
    internal abstract fun playIntegrity(impl: GooglePlayIntegrity): PlayIntegrity
}
