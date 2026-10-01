package app.giveaway.core.security.backup

import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.CharBuffer
import java.security.SecureRandom
import javax.inject.Inject

/** Turns the backup password into key material. Argon2id in the app; a fast stand-in in JVM tests. */
fun interface PasswordKdf {
    fun derive(password: ByteArray, salt: ByteArray, params: KdfParams): ByteArray
}

/** Argon2id cost, written in the backup header so a later version can raise it and still read old files. */
data class KdfParams(val memoryKib: Int, val iterations: Int, val parallelism: Int)

/** Argon2id (plan A17): 64 MiB, 3 passes, 1 lane. */
class Argon2PasswordKdf @Inject constructor() : PasswordKdf {
    override fun derive(password: ByteArray, salt: ByteArray, params: KdfParams): ByteArray = Argon2Kt().hash(
        mode = Argon2Mode.ARGON2_ID,
        password = password,
        salt = salt,
        tCostInIterations = params.iterations,
        mCostInKibibyte = params.memoryKib,
        parallelism = params.parallelism,
        hashLengthInBytes = BackupCipher.KEY_BYTES,
    ).rawHashAsByteArray()
}

/** Why a file can't be read as a backup. */
class BackupFormatException(val reason: Reason, cause: Throwable? = null) : IOException(reason.name, cause) {
    enum class Reason { NOT_A_BACKUP, NEWER_VERSION }
}

/**
 * The encrypted backup container (plan A17): "GWBK", the format version, the Argon2id parameters and the salt in the
 * clear, then AES-256-GCM streaming ciphertext in 1 MB segments, keyed from the password. The header is the
 * associated data, so changing any of it breaks decryption. A wrong password or a damaged or truncated file fails
 * with an IOException while reading; nothing unauthenticated is ever returned.
 */
class BackupCipher(private val kdf: PasswordKdf, private val params: KdfParams) {
    @Inject
    constructor(kdf: Argon2PasswordKdf) : this(kdf, DEFAULT_PARAMS)

    private val random = SecureRandom()

    /** Writes the header to [out] and returns the stream to write the plaintext to; closing it finishes the file. */
    fun encrypt(out: OutputStream, password: CharArray): OutputStream {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val header = header(FORMAT_VERSION, params, salt)
        out.write(header)
        return streaming(password, salt, params).newEncryptingStream(out, header)
    }

    /** Reads and checks the header from [input] and returns the decrypted plaintext stream. */
    fun decrypt(input: InputStream, password: CharArray): InputStream {
        val data = DataInputStream(input)
        val (header, params, salt) = try {
            readHeader(data)
        } catch (e: EOFException) {
            throw BackupFormatException(BackupFormatException.Reason.NOT_A_BACKUP, e)
        }
        return streaming(password, salt, params).newDecryptingStream(data, header)
    }

    private fun streaming(password: CharArray, salt: ByteArray, params: KdfParams): AesGcmHkdfStreaming {
        val bytes = Charsets.UTF_8.encode(CharBuffer.wrap(password))
        val secret = ByteArray(bytes.remaining()).also { bytes.get(it) }
        bytes.array().fill(0)
        val ikm = try {
            kdf.derive(secret, salt, params)
        } finally {
            secret.fill(0)
        }
        try {
            return AesGcmHkdfStreaming(ikm, HKDF, KEY_BYTES, SEGMENT_BYTES, 0)
        } finally {
            ikm.fill(0)
        }
    }

    private fun readHeader(data: DataInputStream): Triple<ByteArray, KdfParams, ByteArray> {
        val magic = ByteArray(MAGIC.size).also(data::readFully)
        if (!magic.contentEquals(MAGIC)) notABackup()
        val version = data.readUnsignedShort()
        if (version > FORMAT_VERSION) throw BackupFormatException(BackupFormatException.Reason.NEWER_VERSION)
        val params = KdfParams(data.readInt(), data.readInt(), data.readInt())
        // A crafted header must not make the phone spend minutes or gigabytes on the key.
        val sane = params.memoryKib in 1..MAX_MEMORY_KIB && params.iterations in 1..MAX_ITERATIONS &&
            params.parallelism in 1..MAX_PARALLELISM
        if (version < 1 || !sane) notABackup()
        val salt = ByteArray(SALT_BYTES).also(data::readFully)
        return Triple(header(version, params, salt), params, salt)
    }

    private fun notABackup(): Nothing = throw BackupFormatException(BackupFormatException.Reason.NOT_A_BACKUP)

    companion object {
        const val FORMAT_VERSION = 1
        internal const val KEY_BYTES = 32
        private val MAGIC = "GWBK".toByteArray(Charsets.US_ASCII)
        private const val SALT_BYTES = 16
        private const val SEGMENT_BYTES = 1 shl 20
        private const val HKDF = "HmacSha256"
        private const val MAX_MEMORY_KIB = 256 * 1024
        private const val MAX_ITERATIONS = 10
        private const val MAX_PARALLELISM = 4
        val DEFAULT_PARAMS = KdfParams(memoryKib = 64 * 1024, iterations = 3, parallelism = 1)

        private fun header(version: Int, params: KdfParams, salt: ByteArray): ByteArray {
            val bytes = ByteArrayOutputStream()
            DataOutputStream(bytes).run {
                write(MAGIC)
                writeShort(version)
                writeInt(params.memoryKib)
                writeInt(params.iterations)
                writeInt(params.parallelism)
                write(salt)
            }
            return bytes.toByteArray()
        }
    }
}
