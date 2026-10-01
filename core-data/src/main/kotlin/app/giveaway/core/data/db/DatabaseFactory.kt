package app.giveaway.core.data.db

import android.content.Context
import androidx.room.Room
import app.giveaway.core.security.KeyPurpose
import app.giveaway.core.security.KeystoreKeys
import app.giveaway.core.security.SecretBox
import dagger.hilt.android.qualifiers.ApplicationContext
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The database file exists but its passphrase can no longer be unwrapped (the Keystore key was deleted, for example
 * after a factory reset or "Delete everything" interrupted midway). The data is unreadable; the app should offer to
 * restore a backup or start fresh with [DatabaseFactory.deleteDatabase].
 */
class DatabaseKeyLostException(cause: Throwable) : Exception("Database passphrase can't be unwrapped", cause)

/**
 * Opens [GiveawayDatabase] with SQLCipher. The passphrase is 32 random bytes created on first use, stored only
 * wrapped by the DATABASE Keystore key in `noBackupFilesDir` (excluded from backups), and bound to this database
 * name through associated data (spec: Storage and keys).
 */
@Singleton
class DatabaseFactory internal constructor(
    private val context: Context,
    private val keys: KeystoreKeys,
    private val databaseName: String,
) {
    @Inject
    constructor(@ApplicationContext context: Context, keys: KeystoreKeys) : this(context, keys, DATABASE_NAME)

    private val keyFile get() = File(context.noBackupFilesDir, "$databaseName.key")

    fun open(): GiveawayDatabase {
        loadSqlCipher()
        val passphrase = loadOrCreatePassphrase(keys.secretBox(KeyPurpose.DATABASE))
        return Room.databaseBuilder(context, GiveawayDatabase::class.java, databaseName)
            .openHelperFactory(SupportOpenHelperFactory(passphrase, null, true))
            .build()
    }

    /** Removes the database files and the wrapped passphrase. */
    fun deleteDatabase() {
        context.deleteDatabase(databaseName)
        keyFile.delete()
    }

    internal fun loadOrCreatePassphrase(box: SecretBox): ByteArray {
        val associatedData = databaseName.toByteArray()
        val file = keyFile
        if (file.exists()) {
            return try {
                box.decrypt(file.readBytes(), associatedData)
            } catch (e: GeneralSecurityException) {
                throw DatabaseKeyLostException(e)
            }
        }
        if (context.getDatabasePath(databaseName).exists()) {
            // A database without its key can never be opened again.
            throw DatabaseKeyLostException(IllegalStateException("Passphrase file missing"))
        }
        val passphrase = ByteArray(PASSPHRASE_BYTES).also(SecureRandom()::nextBytes)
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeBytes(box.encrypt(passphrase, associatedData))
        check(temp.renameTo(file)) { "Could not store the database passphrase" }
        return passphrase
    }

    internal companion object {
        const val DATABASE_NAME = "giveaway.db"
        private const val PASSPHRASE_BYTES = 32

        @Volatile
        private var sqlCipherLoaded = false

        fun loadSqlCipher() {
            if (!sqlCipherLoaded) {
                System.loadLibrary("sqlcipher")
                sqlCipherLoaded = true
            }
        }
    }
}
