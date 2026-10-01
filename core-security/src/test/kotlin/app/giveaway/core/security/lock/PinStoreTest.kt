package app.giveaway.core.security.lock

import app.giveaway.core.security.SecretBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** C-03: PIN storage, verification and the doubling lockout after five wrong PINs (plan A19). */
class PinStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private var now = Instant.parse("2026-10-01T12:00:00Z")
    private val clock = object : Clock() {
        override fun instant() = now
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?) = this
    }

    /** Reversible stand-in for the Keystore-backed box, so the file content can be inspected. */
    private val box = object : SecretBox {
        override fun encrypt(plaintext: ByteArray, associatedData: ByteArray) = plaintext.reversedArray()
        override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray) = ciphertext.reversedArray()
    }

    /** Fast stand-in for Argon2id: salt plus PIN digits, so equal inputs give equal hashes. */
    private val hasher = PinHasher { pin, salt -> salt + pin }

    private val file get() = File(temp.root, "app-lock.pin")
    private val store by lazy { EncryptedPinStore(file, { box }, hasher, clock) }

    @Test
    fun aSetPinVerifies() {
        assertFalse(store.hasPin())
        store.setPin("482913")
        assertTrue(store.hasPin())
        assertEquals(PinCheck.Correct, store.verify("482913"))
    }

    @Test
    fun onlySixDigitPinsAreAccepted() {
        listOf("12345", "1234567", "12a456", "").forEach { pin ->
            assertThrows(pin, IllegalArgumentException::class.java) { store.setPin(pin) }
        }
    }

    @Test
    fun wrongPinsCountDownToALockout() {
        store.setPin("482913")
        assertEquals(PinCheck.Wrong(4), store.verify("000000"))
        assertEquals(PinCheck.Wrong(3), store.verify("000000"))
        assertEquals(PinCheck.Wrong(2), store.verify("000000"))
        assertEquals(PinCheck.Wrong(1), store.verify("000000"))
        assertEquals(PinCheck.LockedOut(now.plusSeconds(30)), store.verify("000000"))
    }

    @Test
    fun duringALockoutEvenTheRightPinIsNotChecked() {
        store.setPin("482913")
        repeat(5) { store.verify("000000") }
        now = now.plusSeconds(10)
        assertTrue(store.verify("482913") is PinCheck.LockedOut)
        now = now.plusSeconds(21)
        assertEquals(PinCheck.Correct, store.verify("482913"))
    }

    @Test
    fun eachFurtherFailureDoublesTheLockoutUpToAnHour() {
        store.setPin("482913")
        repeat(5) { store.verify("000000") }
        val waits = (1..8).map {
            now = (store.verify("000000") as PinCheck.LockedOut).until
            val until = (store.verify("000000") as PinCheck.LockedOut).until
            Duration.between(now, until).seconds
        }
        assertEquals(listOf(60L, 120L, 240L, 480L, 960L, 1920L, 3600L, 3600L), waits)
    }

    @Test
    fun aCorrectPinResetsTheCount() {
        store.setPin("482913")
        repeat(3) { store.verify("000000") }
        assertEquals(PinCheck.Correct, store.verify("482913"))
        assertEquals(PinCheck.Wrong(4), store.verify("000000"))
    }

    @Test
    fun theFailureCountSurvivesANewStoreInstance() {
        store.setPin("482913")
        repeat(4) { store.verify("000000") }
        val reopened = EncryptedPinStore(file, { box }, hasher, clock)
        assertEquals(PinCheck.LockedOut(now.plusSeconds(30)), reopened.verify("000000"))
    }

    @Test
    fun settingThePinAgainUsesANewSalt() {
        store.setPin("482913")
        val first = file.readBytes()
        store.setPin("482913")
        assertFalse(first.contentEquals(file.readBytes()))
        assertEquals(PinCheck.Correct, store.verify("482913"))
    }

    @Test
    fun clearRemovesThePin() {
        store.setPin("482913")
        store.clear()
        assertFalse(store.hasPin())
        assertEquals(PinCheck.Wrong(EncryptedPinStore.FREE_ATTEMPTS), store.verify("482913"))
    }
}
