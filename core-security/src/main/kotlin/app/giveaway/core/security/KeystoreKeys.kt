package app.giveaway.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import com.google.crypto.tink.integration.android.AndroidKeystore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.ECGenParameterSpec
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.inject.Inject
import javax.inject.Singleton

/** Where a key's material lives, for diagnostics and certificate notes. */
enum class KeySecurityLevel { STRONGBOX, TRUSTED_ENVIRONMENT, SOFTWARE, UNKNOWN }

/**
 * Owns the app's Android Keystore keys: one AES-256-GCM key per [KeyPurpose] and one EC P-256 signing key.
 * Keys are created on first use in StrongBox when the device has it, otherwise in the TEE (or software on devices
 * without secure hardware). They are not bound to user authentication, because imports run in the background
 * (build plan assumption A18).
 */
@Singleton
class KeystoreKeys @Inject constructor(@ApplicationContext context: Context) {

    private val hasStrongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    @Synchronized
    fun secretBox(purpose: KeyPurpose): SecretBox {
        if (!AndroidKeystore.hasKey(purpose.alias)) {
            withStrongBoxFallback { strongBox ->
                AndroidKeystore.generateNewKeyWithSpec(aesSpec(purpose.alias, strongBox))
            }
        }
        return TinkSecretBox(AndroidKeystore.getAead(purpose.alias))
    }

    @Synchronized
    fun signer(): DeviceSigner {
        if (!keyStore.containsAlias(SIGNING_ALIAS)) {
            withStrongBoxFallback { strongBox ->
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE).run {
                    initialize(signingSpec(strongBox))
                    generateKeyPair()
                }
            }
        }
        val entry = keyStore.getEntry(SIGNING_ALIAS, null) as KeyStore.PrivateKeyEntry
        return DeviceSigner(entry.privateKey, entry.certificate.publicKey)
    }

    fun securityLevel(purpose: KeyPurpose): KeySecurityLevel {
        secretBox(purpose)
        val key = keyStore.getKey(purpose.alias, null) as SecretKey
        val info = SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE).getKeySpec(key, KeyInfo::class.java)
        return (info as KeyInfo).toLevel()
    }

    fun signingSecurityLevel(): KeySecurityLevel {
        signer()
        val key = keyStore.getKey(SIGNING_ALIAS, null) as PrivateKey
        return KeyFactory.getInstance(key.algorithm, ANDROID_KEYSTORE).getKeySpec(key, KeyInfo::class.java).toLevel()
    }

    /** Removes every key this class owns. Data they protected becomes unreadable ("Delete everything"). */
    @Synchronized
    fun deleteAll() {
        KeyPurpose.entries.forEach { if (keyStore.containsAlias(it.alias)) keyStore.deleteEntry(it.alias) }
        if (keyStore.containsAlias(SIGNING_ALIAS)) keyStore.deleteEntry(SIGNING_ALIAS)
    }

    private fun <T> withStrongBoxFallback(create: (strongBox: Boolean) -> T): T {
        if (!hasStrongBox) return create(false)
        return try {
            create(true)
        } catch (expected: StrongBoxUnavailableException) {
            // Advertised but not usable for this key type; the TEE is still hardware-backed.
            create(false)
        }
    }

    private fun aesSpec(alias: String, strongBox: Boolean) = KeyGenParameterSpec.Builder(
        alias,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
    )
        .setKeySize(AES_KEY_BITS)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .preferStrongBox(strongBox)
        .build()

    private fun signingSpec(strongBox: Boolean) = KeyGenParameterSpec.Builder(
        SIGNING_ALIAS,
        KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
    )
        .setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
        .setDigests(KeyProperties.DIGEST_SHA256)
        .preferStrongBox(strongBox)
        .build()

    private fun KeyGenParameterSpec.Builder.preferStrongBox(strongBox: Boolean) = apply {
        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(true)
    }

    @Suppress("DEPRECATION") // isInsideSecureHardware is the only signal below API 31.
    private fun KeyInfo.toLevel(): KeySecurityLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        when (securityLevel) {
            KeyProperties.SECURITY_LEVEL_STRONGBOX -> KeySecurityLevel.STRONGBOX
            KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> KeySecurityLevel.TRUSTED_ENVIRONMENT
            KeyProperties.SECURITY_LEVEL_SOFTWARE -> KeySecurityLevel.SOFTWARE
            else -> KeySecurityLevel.UNKNOWN
        }
    } else if (isInsideSecureHardware) {
        KeySecurityLevel.TRUSTED_ENVIRONMENT
    } else {
        KeySecurityLevel.SOFTWARE
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val SIGNING_ALIAS = "giveaway.signing"
        const val AES_KEY_BITS = 256
        const val EC_CURVE = "secp256r1"
    }
}
