package app.giveaway.core.data.backup

import android.content.ContentValues
import android.util.JsonReader
import android.util.JsonToken
import app.giveaway.core.security.backup.BackupFormatException
import app.giveaway.core.security.backup.BackupFormatException.Reason
import java.io.InputStream
import java.io.InputStreamReader
import java.time.Instant
import java.util.Base64
import java.util.zip.GZIPInputStream

/**
 * Reads a decrypted backup document and hands each row to [sink] as column values, table by table in the order they
 * were written (parents first). Reads to the very end, so the last ciphertext segment is authenticated before the
 * caller commits anything.
 */
internal class BackupReader(
    private val currentSchema: Int,
    private val sink: (table: String, row: ContentValues) -> Unit,
) {

    /** Returns when the backup was made. */
    fun read(plain: InputStream): Instant {
        val stream = GZIPInputStream(plain)
        var createdAt = Instant.EPOCH
        var format: String? = null
        JsonReader(InputStreamReader(stream, Charsets.UTF_8)).run {
            beginObject()
            while (hasNext()) {
                when (nextName()) {
                    "format" -> format = nextString()
                    "schemaVersion" -> if (nextInt() > currentSchema) throw BackupFormatException(Reason.NEWER_VERSION)
                    "createdAt" -> createdAt = Instant.parse(nextString())
                    "tables" -> {
                        if (format != BackupManager.FORMAT) throw BackupFormatException(Reason.NOT_A_BACKUP)
                        tables(this)
                    }
                    else -> skipValue()
                }
            }
            endObject()
        }
        val rest = ByteArray(DRAIN_BYTES)
        while (stream.read(rest) != -1) Unit
        return createdAt
    }

    private fun tables(json: JsonReader) {
        json.beginObject()
        while (json.hasNext()) {
            val table = json.nextName()
            if (table in BackupManager.TABLES) table(json, table) else json.skipValue()
        }
        json.endObject()
    }

    private fun table(json: JsonReader, table: String) {
        var columns = emptyList<String>()
        json.beginObject()
        while (json.hasNext()) {
            when (json.nextName()) {
                "columns" -> columns = buildList {
                    json.beginArray()
                    while (json.hasNext()) add(json.nextString())
                    json.endArray()
                }
                "rows" -> {
                    json.beginArray()
                    while (json.hasNext()) sink(table, row(json, columns))
                    json.endArray()
                }
                else -> json.skipValue()
            }
        }
        json.endObject()
    }

    private fun row(json: JsonReader, columns: List<String>): ContentValues {
        val values = ContentValues(columns.size)
        json.beginArray()
        columns.forEach { name ->
            when (json.peek()) {
                JsonToken.NULL -> json.nextNull().also { values.putNull(name) }
                JsonToken.NUMBER -> json.nextString().let { n ->
                    n.toLongOrNull()?.let { values.put(name, it) } ?: values.put(name, n.toDouble())
                }
                JsonToken.BOOLEAN -> values.put(name, json.nextBoolean())
                JsonToken.BEGIN_OBJECT -> values.put(name, blob(json))
                else -> values.put(name, json.nextString())
            }
        }
        json.endArray()
        return values
    }

    private fun blob(json: JsonReader): ByteArray {
        var bytes = ByteArray(0)
        json.beginObject()
        while (json.hasNext()) {
            if (json.nextName() == BackupManager.BLOB) {
                bytes = Base64.getDecoder().decode(json.nextString())
            } else {
                json.skipValue()
            }
        }
        json.endObject()
        return bytes
    }

    private companion object {
        const val DRAIN_BYTES = 8 * 1024
    }
}
