package app.giveaway.core.security.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlin.random.Random

/** M-11/M-12: the backup container round-trips and refuses anything it can't authenticate. */
class BackupCipherTest {

    /** Argon2 needs its native library; a hash of password and salt keeps the container logic under test. */
    private val kdf = PasswordKdf { password, salt, _ ->
        MessageDigest.getInstance("SHA-256").digest(password + salt)
    }
    private val cipher = BackupCipher(kdf, BackupCipher.DEFAULT_PARAMS)
    private val password = "correct horse battery".toCharArray()

    private fun encrypt(plain: ByteArray, with: CharArray = password): ByteArray {
        val out = ByteArrayOutputStream()
        cipher.encrypt(out, with).use { it.write(plain) }
        return out.toByteArray()
    }

    private fun decrypt(file: ByteArray, with: CharArray = password) =
        cipher.decrypt(ByteArrayInputStream(file), with).use { it.readBytes() }

    private inline fun <reified T : Throwable> fails(block: () -> Unit): T =
        runCatching(block).exceptionOrNull() as? T ?: throw AssertionError("expected ${T::class.simpleName}")

    @Test
    fun roundTripsAcrossSeveralSegments() {
        val plain = Random(7).nextBytes(3 * 1024 * 1024 + 123)
        val file = encrypt(plain)
        assertEquals("GWBK", String(file, 0, 4, Charsets.US_ASCII))
        assertArrayEquals(plain, decrypt(file))
    }

    @Test
    fun theFileHidesThePlaintext() {
        val plain = "{\"username\":\"maya.k\"}".repeat(100).toByteArray()
        val file = String(encrypt(plain), Charsets.ISO_8859_1)
        assertFalse(file.contains("maya.k"))
    }

    @Test
    fun aWrongPasswordFails() {
        fails<IOException> { decrypt(encrypt(byteArrayOf(1, 2, 3)), "wrong".toCharArray()) }
    }

    @Test
    fun aTruncatedFileFails() {
        val file = encrypt(Random(1).nextBytes(2 * 1024 * 1024))
        fails<IOException> { decrypt(file.copyOf(file.size - 10)) }
        fails<IOException> { decrypt(file.copyOf(1024 * 1024)) }
    }

    @Test
    fun aChangedHeaderFails() {
        val file = encrypt(byteArrayOf(9, 9, 9))
        // The salt is part of the associated data: flip one of its bits.
        file[30] = (file[30].toInt() xor 1).toByte()
        fails<IOException> { decrypt(file) }
    }

    @Test
    fun otherFilesAndNewerVersionsAreNamed() {
        val notBackup = fails<BackupFormatException> { decrypt("hello, not a backup".toByteArray()) }
        assertEquals(BackupFormatException.Reason.NOT_A_BACKUP, notBackup.reason)
        val tooShort = fails<BackupFormatException> { decrypt(ByteArray(2)) }
        assertEquals(BackupFormatException.Reason.NOT_A_BACKUP, tooShort.reason)
        val newer = encrypt(byteArrayOf(1)).also { it[5] = (BackupCipher.FORMAT_VERSION + 1).toByte() }
        assertEquals(BackupFormatException.Reason.NEWER_VERSION, fails<BackupFormatException> { decrypt(newer) }.reason)
    }

    @Test
    fun aCraftedCostIsRefused() {
        val file = encrypt(byteArrayOf(1))
        // memoryKib is the int after the 4-byte magic and 2-byte version.
        file[6] = 0x7f
        assertEquals(BackupFormatException.Reason.NOT_A_BACKUP, fails<BackupFormatException> { decrypt(file) }.reason)
    }

    @Test
    fun eachFileHasItsOwnSalt() {
        val a = encrypt(byteArrayOf(1))
        val b = encrypt(byteArrayOf(1))
        assertTrue(!a.copyOfRange(18, 34).contentEquals(b.copyOfRange(18, 34)))
    }
}
