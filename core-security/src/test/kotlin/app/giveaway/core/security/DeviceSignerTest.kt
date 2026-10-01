package app.giveaway.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec

/** Signing and verification with a plain JCA key; the Keystore-backed path is covered by device tests. */
class DeviceSignerTest {

    private val keyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }
    private val signer = DeviceSigner(keyPair.private, keyPair.public)
    private val record = "draw v1 entryListHash=abc".toByteArray()

    @Test
    fun signatureVerifiesWithThePublishedPublicKey() {
        assertTrue(DeviceSigner.verify(record, signer.sign(record), signer.publicKeySpki))
    }

    @Test
    fun changedRecordFailsVerification() {
        val signature = signer.sign(record)
        assertFalse(DeviceSigner.verify("draw v1 entryListHash=abd".toByteArray(), signature, signer.publicKeySpki))
    }

    @Test
    fun signatureFromAnotherKeyFailsVerification() {
        val other = KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp256r1"))
            generateKeyPair()
        }
        val signature = DeviceSigner(other.private, other.public).sign(record)
        assertFalse(DeviceSigner.verify(record, signature, signer.publicKeySpki))
    }

    @Test
    fun malformedInputReturnsFalseInsteadOfThrowing() {
        assertFalse(DeviceSigner.verify(record, byteArrayOf(1, 2, 3), signer.publicKeySpki))
        assertFalse(DeviceSigner.verify(record, signer.sign(record), byteArrayOf(4, 5, 6)))
    }

    @Test
    fun fingerprintIsSha256OfThePublicKeyInLowercaseHex() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            DeviceSigner.fingerprintOf(ByteArray(0)),
        )
        assertTrue(signer.fingerprint.matches(Regex("[0-9a-f]{64}")))
    }
}
