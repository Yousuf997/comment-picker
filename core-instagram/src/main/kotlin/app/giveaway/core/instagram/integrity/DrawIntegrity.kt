package app.giveaway.core.instagram.integrity

import app.giveaway.core.instagram.network.LoginHelperClient
import app.giveaway.core.security.integrity.PlayIntegrity
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest
import javax.inject.Inject

/** The integrity check before a real draw (spec: S11 checks; plan A6). False means "device integrity not verified". */
fun interface DrawIntegrity {
    suspend fun verify(entryListHash: String, commitHash: String): Boolean
}

/**
 * Asks Play for a Standard token bound to this draw (request hash = SHA-256 of the entry list hash and the commit hash)
 * and has the login helper decode it. Only booleans come back. Any failure, or more than [TIMEOUT_MS], is "not
 * verified": the draw is still allowed and the certificate says so (spec: App hardening).
 */
class PlayDrawIntegrity @Inject constructor(
    private val integrity: PlayIntegrity,
    private val helper: LoginHelperClient,
) : DrawIntegrity {
    override suspend fun verify(entryListHash: String, commitHash: String): Boolean = withTimeoutOrNull(TIMEOUT_MS) {
        val requestHash = requestHash(entryListHash, commitHash)
        val token = integrity.standardToken(requestHash) ?: return@withTimeoutOrNull false
        val verdict = helper.drawIntegrity(token, requestHash) ?: return@withTimeoutOrNull false
        verdict.appRecognized && verdict.deviceIntegrity && verdict.hashMatches
    } ?: false

    companion object {
        private const val TIMEOUT_MS = 15_000L

        fun requestHash(entryListHash: String, commitHash: String): String =
            MessageDigest.getInstance("SHA-256").digest((entryListHash + commitHash).toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}

/** Replaced in UI tests. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DrawIntegrityModule {
    @Binds
    internal abstract fun drawIntegrity(impl: PlayDrawIntegrity): DrawIntegrity
}
