package app.giveaway.core.data.backup

import android.database.Cursor
import android.util.JsonWriter
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.db.SettingsEntity
import app.giveaway.core.data.giveaway.SeedVault
import app.giveaway.core.security.backup.BackupCipher
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.Clock
import java.util.Base64
import java.util.zip.GZIPOutputStream
import javax.inject.Inject

/**
 * Encrypted backup export (plan M-11, format A17). Every table goes into one JSON document, gzipped and encrypted with
 * a key derived from the user's password, except the Instagram account and token (the user signs in again after a
 * restore; spec: Backup file) and media_file rows, whose videos and certificate files are not in the backup.
 * Committed seeds are written decrypted, protected only by the backup password, so a restore on a new phone can still
 * reveal them; the database is read in one transaction, so the copy is consistent.
 */
class BackupManager @Inject constructor(
    private val db: GiveawayDatabase,
    private val cipher: BackupCipher,
    private val vault: SeedVault,
    private val clock: Clock,
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
        internal const val COMMITMENT = "commitment"
        internal const val ENCRYPTED_SEED = "encryptedSeed"

        /** The commitment column that holds the decrypted seed in the backup. */
        internal const val SEED = "seed"

        /** A blob is written as {"b64": "..."}. */
        internal const val BLOB = "b64"
    }
}
