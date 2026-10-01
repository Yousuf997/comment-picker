package app.giveaway.core.security.lock

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.giveaway.core.security.KeystoreKeys
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock

/** C-03 on a device: the native Argon2id hasher works, and the PIN store persists through the Keystore. */
@RunWith(AndroidJUnit4::class)
class Argon2PinHasherTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val keys = KeystoreKeys(context)
    private val hasher = Argon2PinHasher()

    @After
    fun tearDown() {
        EncryptedPinStore(context, keys, hasher, Clock.systemUTC()).clear()
        keys.deleteAll()
    }

    @Test
    fun argon2idIsDeterministicPerSaltAndDiffersAcrossSalts() {
        val salt = ByteArray(16) { 1 }
        val first = hasher.hash("482913".toByteArray(), salt)
        assertEquals(32, first.size)
        assertArrayEquals(first, hasher.hash("482913".toByteArray(), salt))
        assertFalse(first.contentEquals(hasher.hash("482913".toByteArray(), ByteArray(16) { 2 })))
        assertFalse(first.contentEquals(hasher.hash("482914".toByteArray(), salt)))
    }

    @Test
    fun pinStoreRoundTripsWithTheRealKeystoreAndHasher() {
        val store = EncryptedPinStore(context, keys, hasher, Clock.systemUTC())
        store.setPin("482913")
        assertEquals(PinCheck.Wrong(4), store.verify("000000"))
        val reopened = EncryptedPinStore(context, keys, hasher, Clock.systemUTC())
        assertEquals(PinCheck.Correct, reopened.verify("482913"))
    }
}
