package app.giveaway.core.security.lock

import android.content.Context
import app.giveaway.core.security.KeyPurpose
import app.giveaway.core.security.KeystoreKeys
import app.giveaway.core.security.SecretBox
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Turns a PIN into a slow, salted hash. */
fun interface PinHasher {
    fun hash(pin: ByteArray, salt: ByteArray): ByteArray
}

/** Argon2id (plan A19). Slow on purpose: about a quarter second on a mid-range phone. */
class Argon2PinHasher @Inject constructor() : PinHasher {
    override fun hash(pin: ByteArray, salt: ByteArray): ByteArray = Argon2Kt().hash(
        mode = Argon2Mode.ARGON2_ID,
        password = pin,
        salt = salt,
        tCostInIterations = ITERATIONS,
        mCostInKibibyte = MEMORY_KIB,
        parallelism = 1,
        hashLengthInBytes = HASH_BYTES,
    ).rawHashAsByteArray()

    private companion object {
        const val ITERATIONS = 3
        const val MEMORY_KIB = 32 * 1024
        const val HASH_BYTES = 32
    }
}

sealed interface PinCheck {
    data object Correct : PinCheck

    data class Wrong(val attemptsBeforeLockout: Int) : PinCheck

    /** Too many wrong PINs: no attempt is checked until [until]. */
    data class LockedOut(val until: Instant) : PinCheck
}

/** The app-lock PIN: set, verify with lockout, clear. */
interface PinStore {
    fun hasPin(): Boolean

    fun setPin(pin: String)

    fun verify(pin: String): PinCheck

    fun clear()

    companion object {
        const val PIN_LENGTH = 6

        fun isValidPin(pin: String) = pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }
    }
}

/**
 * The app-lock PIN (spec: App access; plan A19). Only an Argon2id verifier is kept, with the failure count, in a file
 * encrypted with the PIN Keystore key in `noBackupFilesDir`. After [FREE_ATTEMPTS] wrong PINs each further failure
 * locks entry for a period that doubles, from [FIRST_LOCKOUT] up to [MAX_LOCKOUT].
 */
@Singleton
class EncryptedPinStore internal constructor(
    private val file: File,
    private val box: () -> SecretBox,
    private val hasher: PinHasher,
    private val clock: Clock,
) : PinStore {
    @Inject
    constructor(@ApplicationContext context: Context, keys: KeystoreKeys, hasher: Argon2PinHasher, clock: Clock) :
        this(File(context.noBackupFilesDir, "app-lock.pin"), { keys.secretBox(KeyPurpose.PIN) }, hasher, clock)

    private val random = SecureRandom()

    override fun hasPin(): Boolean = file.exists()

    @Synchronized
    override fun setPin(pin: String) {
        require(PinStore.isValidPin(pin)) { "PIN must be ${PinStore.PIN_LENGTH} digits" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        write(Record(salt, hasher.hash(pin.toByteArray(), salt), failures = 0, lockedUntil = Instant.EPOCH))
    }

    @Synchronized
    override fun verify(pin: String): PinCheck {
        val record = read() ?: return PinCheck.Wrong(FREE_ATTEMPTS)
        val now = clock.instant()
        if (now.isBefore(record.lockedUntil)) return PinCheck.LockedOut(record.lockedUntil)

        if (MessageDigest.isEqual(hasher.hash(pin.toByteArray(), record.salt), record.hash)) {
            write(record.copy(failures = 0, lockedUntil = Instant.EPOCH))
            return PinCheck.Correct
        }
        val failures = record.failures + 1
        if (failures < FREE_ATTEMPTS) {
            write(record.copy(failures = failures))
            return PinCheck.Wrong(FREE_ATTEMPTS - failures)
        }
        val lockout = FIRST_LOCKOUT.multipliedBy(1L shl (failures - FREE_ATTEMPTS).coerceAtMost(MAX_DOUBLINGS))
            .coerceAtMost(MAX_LOCKOUT)
        val until = now.plus(lockout)
        write(record.copy(failures = failures, lockedUntil = until))
        return PinCheck.LockedOut(until)
    }

    /** Removes the PIN, for turning app lock off or "Delete everything". */
    @Synchronized
    override fun clear() {
        file.delete()
    }

    private data class Record(val salt: ByteArray, val hash: ByteArray, val failures: Int, val lockedUntil: Instant)

    private fun write(record: Record) {
        val bytes = ByteArrayOutputStream().also { out ->
            DataOutputStream(out).use {
                it.writeByte(FORMAT_VERSION)
                it.write(record.salt)
                it.write(record.hash)
                it.writeInt(record.failures)
                it.writeLong(record.lockedUntil.toEpochMilli())
            }
        }.toByteArray()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeBytes(box().encrypt(bytes, ASSOCIATED_DATA))
        // Atomic replace, so a crash mid-write never leaves a half-written PIN file.
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    private fun read(): Record? {
        if (!file.exists()) return null
        val bytes = box().decrypt(file.readBytes(), ASSOCIATED_DATA)
        DataInputStream(bytes.inputStream()).use {
            check(it.readByte().toInt() == FORMAT_VERSION) { "Unknown PIN format" }
            val salt = ByteArray(SALT_BYTES).also(it::readFully)
            val hash = ByteArray(bytes.size - 1 - SALT_BYTES - Int.SIZE_BYTES - Long.SIZE_BYTES).also(it::readFully)
            return Record(salt, hash, it.readInt(), Instant.ofEpochMilli(it.readLong()))
        }
    }

    companion object {
        const val FREE_ATTEMPTS = 5
        val FIRST_LOCKOUT: Duration = Duration.ofSeconds(30)
        val MAX_LOCKOUT: Duration = Duration.ofHours(1)

        private const val MAX_DOUBLINGS = 10
        private const val SALT_BYTES = 16
        private const val FORMAT_VERSION = 1
        private val ASSOCIATED_DATA = "app-lock-pin".toByteArray()
    }
}
