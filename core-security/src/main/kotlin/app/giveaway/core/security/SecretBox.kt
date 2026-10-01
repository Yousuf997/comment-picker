package app.giveaway.core.security

import com.google.crypto.tink.Aead

/**
 * Authenticated encryption for small secrets. [associatedData] binds a ciphertext to its context (for example the
 * giveaway ID for a seed), so a ciphertext copied to another record fails to decrypt.
 * Failures throw [java.security.GeneralSecurityException].
 */
interface SecretBox {
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray

    fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray
}

internal class TinkSecretBox(private val aead: Aead) : SecretBox {
    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray =
        aead.encrypt(plaintext, associatedData)

    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray =
        aead.decrypt(ciphertext, associatedData)
}
