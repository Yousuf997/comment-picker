package app.giveaway.core.data.backup

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.JsonWriter
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.db.SettingsEntity
import app.giveaway.core.data.giveaway.SeedVault
import app.giveaway.core.data.media.MediaRepository
import app.giveaway.core.data.work.DeadlineScheduler
import app.giveaway.core.security.backup.BackupCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.zip.GZIPOutputStream
import javax.inject.Inject

/** What a backup holds, shown before the user confirms replacing everything (spec: S5 Restore). */
data class BackupSummary(val createdAt: Instant, val giveaways: Int)

/**
 * Encrypted backup export and restore (plans M-11 and M-12, format A17). Every table goes into one JSON document,
 * gzipped and encrypted with a key derived from the user's password, except the Instagram account and token (the user
 * signs in again after a restore; spec: Backup file) and media_file rows, whose videos and certificate files are not
 * in the backup.
 * Committed seeds are written decrypted, protected only by the backup password, so a restore on a new phone can still
 * reveal them; the database is read in one transaction, so the copy is consistent.
 *
 * A restore replaces everything in one transaction: a wrong password, a damaged or truncated file, or a backup from
 * a newer app version changes nothing. Seeds are sealed again with this phone's Keystore key, the Instagram account
 * is cleared so the user signs in again, and this phone's app lock is kept (the PIN is never in a backup).
 */
class BackupManager @Inject constructor(
    private val db: GiveawayDatabase,
    private val cipher: BackupCipher,
    private val vault: SeedVault,
    private val clock: Clock,
    private val media: MediaRepository,
    private val deadlines: DeadlineScheduler,
) {
    /** Writes the backup to [out] and closes it; records when, for the S4 reminder. */
    suspend fun export(out: OutputStream, password: CharArray) {
        db.withTransaction {
            val sql = db.openHelper.writableDatabase
            JsonWriter(OutputStreamWriter(GZIPOutputStream(cipher.encrypt(out, password)), Charsets.UTF_8)).use {
                write(it, sql)
            }
            val settings = db.settingsDao().get() ?: SettingsEntity()
            db.settingsDao().upsert(settings.copy(lastBackupAt = clock.instant()))
        }
    }

    /** Decrypts and reads the whole backup without changing anything, to show what a restore would bring back. */
    suspend fun inspect(input: InputStream, password: CharArray): BackupSummary = withContext(Dispatchers.IO) {
        var giveaways = 0
        val reader = BackupReader(currentSchema()) { table, _ -> if (table == GIVEAWAY) giveaways++ }
        val createdAt = input.use { reader.read(cipher.decrypt(it, password)) }
        BackupSummary(createdAt, giveaways)
    }

    /** Replaces all data with the backup's. Throws, changing nothing, if it can't be read in full. */
    suspend fun restore(input: InputStream, password: CharArray) {
        db.withTransaction {
            val current = db.settingsDao().get() ?: SettingsEntity()
            val sql = db.openHelper.writableDatabase
            CLEARED.forEach { sql.delete(it, null, null) }
            val reader = BackupReader(sql.version) { table, row -> insert(sql, table, row) }
            val createdAt = input.use { reader.read(cipher.decrypt(it, password)) }
            val restored = db.settingsDao().get() ?: SettingsEntity()
            db.settingsDao().upsert(
                restored.copy(
                    appLockEnabled = current.appLockEnabled,
                    appLockMethod = current.appLockMethod,
                    onboardingComplete = current.onboardingComplete,
                    // The restored data is as safe as the backup it came from (S4 reminder).
                    lastBackupAt = createdAt,
                ),
            )
        }
        // Recordings and certificate files belonged to the replaced data; certificates are made again on S15.
        media.deleteAllFiles()
        db.giveawayDao().observeAll().first()
            .filter { it.status == GiveawayStatus.COMMITTED }
            .forEach { deadlines.schedule(it.id, it.closesAt) }
    }

    private fun insert(sql: SupportSQLiteDatabase, table: String, row: ContentValues) {
        if (table == COMMITMENT) {
            val seed = checkNotNull(row.getAsByteArray(SEED)) { "A commitment without its seed" }
            row.remove(SEED)
            row.put(ENCRYPTED_SEED, vault.seal(checkNotNull(row.getAsLong("giveawayId")), seed))
            seed.fill(0)
        }
        sql.insert(table, SQLiteDatabase.CONFLICT_ABORT, row)
    }

    private fun currentSchema() = db.openHelper.readableDatabase.version

    private fun write(json: JsonWriter, sql: SupportSQLiteDatabase) {
        json.beginObject()
        json.name("format").value(FORMAT)
        json.name("formatVersion").value(BackupCipher.FORMAT_VERSION.toLong())
        json.name("schemaVersion").value(sql.version.toLong())
        json.name("createdAt").value(clock.instant().toString())
        json.name("tables").beginObject()
        TABLES.forEach { table ->
            json.name(table)
            sql.query("SELECT * FROM `$table`").use { writeTable(json, table, it) }
        }
        json.endObject()
        json.endObject()
    }

    private fun writeTable(json: JsonWriter, table: String, cursor: Cursor) {
        val seedColumn = if (table == COMMITMENT) cursor.getColumnIndexOrThrow(ENCRYPTED_SEED) else -1
        val giveawayColumn = if (table == COMMITMENT) cursor.getColumnIndexOrThrow("giveawayId") else -1
        json.beginObject()
        json.name("columns").beginArray()
        cursor.columnNames.forEach { json.value(if (it == ENCRYPTED_SEED) SEED else it) }
        json.endArray()
        json.name("rows").beginArray()
        while (cursor.moveToNext()) {
            json.beginArray()
            for (i in 0 until cursor.columnCount) {
                if (i == seedColumn) {
                    val seed = vault.open(cursor.getLong(giveawayColumn), cursor.getBlob(i))
                    blob(json, seed)
                    seed.fill(0)
                } else {
                    value(json, cursor, i)
                }
            }
            json.endArray()
        }
        json.endArray()
        json.endObject()
    }

    private fun value(json: JsonWriter, cursor: Cursor, i: Int) {
        when (cursor.getType(i)) {
            Cursor.FIELD_TYPE_NULL -> json.nullValue()
            Cursor.FIELD_TYPE_INTEGER -> json.value(cursor.getLong(i))
            Cursor.FIELD_TYPE_FLOAT -> json.value(cursor.getDouble(i))
            Cursor.FIELD_TYPE_BLOB -> blob(json, cursor.getBlob(i))
            else -> json.value(cursor.getString(i))
        }
    }

    private fun blob(json: JsonWriter, bytes: ByteArray) {
        json.beginObject().name(BLOB).value(Base64.getEncoder().encodeToString(bytes)).endObject()
    }

    companion object {
        const val FORMAT = "giveaway-backup"

        /** Parents before children, so a restore can insert in this order. */
        val TABLES = listOf(
            "giveaway", "rules", "commitment", "import_state", "comment", "entry", "draw", "draw_result",
            "blocklist", "past_winner", "settings",
        )

        /** Emptied before a restore, children first. The account goes too, so the user signs in again. */
        private val CLEARED = listOf("media_file", "account") + TABLES.reversed()
        private const val GIVEAWAY = "giveaway"
        internal const val COMMITMENT = "commitment"
        internal const val ENCRYPTED_SEED = "encryptedSeed"

        /** The commitment column that holds the decrypted seed in the backup. */
        internal const val SEED = "seed"

        /** A blob is written as {"b64": "..."}. */
        internal const val BLOB = "b64"
    }
}
