package app.giveaway.core.security

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec

/** F-06 acceptance: Keystore-backed encryption and signing on a real Android Keystore. */
@RunWith(AndroidJUnit4::class)
class KeystoreKeysTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val keys = KeystoreKeys(context)
    private val secret = "long-lived-token".toByteArray()
    private val context1 = "giveaway:1".toByteArray()

    @After
    fun deleteKeys() = keys.deleteAll()

    @Test
    fun everyPurposeRoundTrips() {
        KeyPurpose.entries.forEach { purpose ->
            val box = keys.secretBox(purpose)
            assertArrayEquals(purpose.name, secret, box.decrypt(box.encrypt(secret, context1), context1))
        }
    }

    @Test
    fun encryptionIsRandomizedAndHidesThePlaintext() {
        val box = keys.secretBox(KeyPurpose.TOKEN)
        val first = box.encrypt(secret, context1)
        val second = box.encrypt(secret, context1)
        assertFalse(first.contentEquals(second))
        assertFalse(String(first, Charsets.ISO_8859_1).contains("long-lived-token"))
    }

    @Test
    fun tamperedCiphertextOrWrongContextIsRejected() {
        val box = keys.secretBox(KeyPurpose.SEED)
        val ciphertext = box.encrypt(secret, context1)
        val tampered = ciphertext.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertThrows(GeneralSecurityException::class.java) { box.decrypt(tampered, context1) }
        assertThrows(GeneralSecurityException::class.java) { box.decrypt(ciphertext, "giveaway:2".toByteArray()) }
    }

    @Test
    fun purposesUseSeparateKeys() {
        val token = keys.secretBox(KeyPurpose.TOKEN).encrypt(secret, context1)
        val seedBox = keys.secretBox(KeyPurpose.SEED)
        assertThrows(GeneralSecurityException::class.java) { seedBox.decrypt(token, context1) }
    }

    /**
     * Stands in for a process restart: a fresh KeystoreKeys reloads the Keystore from disk and must decrypt data and
     * expose the same signing key as the first instance.
     */
    @Test
    fun keysSurviveANewInstance() {
        val ciphertext = keys.secretBox(KeyPurpose.DATABASE).encrypt(secret, context1)
        val fingerprint = keys.signer().fingerprint

        val reloaded = KeystoreKeys(context)
        assertArrayEquals(secret, reloaded.secretBox(KeyPurpose.DATABASE).decrypt(ciphertext, context1))
        assertEquals(fingerprint, reloaded.signer().fingerprint)
    }

    @Test
    fun signaturesVerifyWithThePublicKey() {
        val signer = keys.signer()
        val record = "draw record".toByteArray()
        val signature = signer.sign(record)
        assertTrue(DeviceSigner.verify(record, signature, signer.publicKeySpki))
        assertFalse(DeviceSigner.verify("other record".toByteArray(), signature, signer.publicKeySpki))
    }

    @Test
    fun signingKeyCannotBeExported() {
        keys.signer()
        val privateKey = KeyStore.getInstance("AndroidKeyStore").run {
            load(null)
            getKey("giveaway.signing", null) as PrivateKey
        }
        assertNull("Keystore must not return raw key material", privateKey.encoded)
        assertThrows(GeneralSecurityException::class.java) {
            KeyFactory.getInstance(privateKey.algorithm, "AndroidKeyStore")
                .getKeySpec(privateKey, PKCS8EncodedKeySpec::class.java)
        }
    }

    @Test
    fun deleteAllMakesOldCiphertextUnreadable() {
        val ciphertext = keys.secretBox(KeyPurpose.TOKEN).encrypt(secret, context1)
        val oldFingerprint = keys.signer().fingerprint
        keys.deleteAll()
        val tokenBox = keys.secretBox(KeyPurpose.TOKEN)
        assertThrows(GeneralSecurityException::class.java) { tokenBox.decrypt(ciphertext, context1) }
        assertNotEquals(oldFingerprint, keys.signer().fingerprint)
    }

    @Test
    fun reportsWhereKeysLive() {
        // Emulators keep keys in software; real phones report TEE or StrongBox. Logged for the device test report.
        KeyPurpose.entries.forEach { Log.i(TAG, "$it: ${keys.securityLevel(it)}") }
        Log.i(TAG, "signing: ${keys.signingSecurityLevel()}")
        assertNotEquals(KeySecurityLevel.UNKNOWN, keys.signingSecurityLevel())
    }

    private companion object {
        const val TAG = "KeystoreKeysTest"
    }
}
