package app.giveaway.core.security

import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Signs draw records and certificates with a non-exportable device key. The public key travels with each signed
 * record, so certificates still verify after a restore on another phone (spec: Edge cases).
 */
class DeviceSigner internal constructor(
    private val privateKey: PrivateKey,
    publicKey: PublicKey,
) {
    /** X.509 SubjectPublicKeyInfo encoding of the public key. */
    val publicKeySpki: ByteArray = publicKey.encoded

    /** SHA-256 of [publicKeySpki] as lowercase hex, shown on certificates as "Signed on device". */
    val fingerprint: String = fingerprintOf(publicKeySpki)

    /** ECDSA P-256 signature over SHA-256 of [data], DER encoded. */
    fun sign(data: ByteArray): ByteArray = Signature.getInstance(ALGORITHM).run {
        initSign(privateKey)
        update(data)
        sign()
    }

    companion object {
        internal const val ALGORITHM = "SHA256withECDSA"

        /** Verifies a signature against a public key from any device; never throws on malformed input. */
        fun verify(data: ByteArray, signature: ByteArray, publicKeySpki: ByteArray): Boolean = runCatching {
            val key = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(publicKeySpki))
            Signature.getInstance(ALGORITHM).run {
                initVerify(key)
                update(data)
                verify(signature)
            }
        }.getOrDefault(false)

        fun fingerprintOf(publicKeySpki: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(publicKeySpki).joinToString("") { "%02x".format(it) }
    }
}
