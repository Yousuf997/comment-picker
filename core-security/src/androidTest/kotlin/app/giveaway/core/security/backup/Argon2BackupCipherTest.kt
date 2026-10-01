package app.giveaway.core.security.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** M-11 on a device: the real Argon2id key derivation drives the backup container. */
@RunWith(AndroidJUnit4::class)
class Argon2BackupCipherTest {

    private val kdf = Argon2PasswordKdf()

    @Test
    fun argon2idIsDeterministicPerSalt() {
        val salt = ByteArray(16) { it.toByte() }
        val params = BackupCipher.DEFAULT_PARAMS
        val a = kdf.derive("password".toByteArray(), salt, params)
        assertEquals(32, a.size)
        assertArrayEquals(a, kdf.derive("password".toByteArray(), salt, params))
        assertFalse(a.contentEquals(kdf.derive("password".toByteArray(), ByteArray(16), params)))
    }

    @Test
    fun aBackupRoundTripsWithTheRealKdf() {
        val cipher = BackupCipher(kdf)
        val plain = "{\"tables\":{}}".toByteArray()
        val out = ByteArrayOutputStream()
        cipher.encrypt(out, "secret pass".toCharArray()).use { it.write(plain) }
        val input = ByteArrayInputStream(out.toByteArray())
        val back = cipher.decrypt(input, "secret pass".toCharArray()).use { it.readBytes() }
        assertArrayEquals(plain, back)
    }
}
