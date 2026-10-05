package app.giveaway.core.data.cleanup

import androidx.room.withTransaction
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.importing.ImportWork
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject

/**
 * Deletes giveaways on request (plan A33): one from Home, or all of them from Settings. Everything that belongs to a
 * giveaway goes with it (rules, commitment, comments, entries, draws, private files). The account, settings, keys,
 * blocklist and past winners stay, as with auto-delete; "Delete everything" is the way to remove those too.
 */
class GiveawayDeleter @Inject constructor(
    private val db: GiveawayDatabase,
    private val imports: ImportWork,
) {
    suspend fun delete(giveawayId: Long) {
        imports.cancel(giveawayId)
        db.withTransaction { db.deleteGiveaway(giveawayId) }.forEach { it.delete() }
        // A scheduled deadline check finds no giveaway and does nothing.
    }

    /** Returns how many were deleted. */
    suspend fun deleteAll(): Int {
        val ids = db.giveawayDao().observeAll().first().map { it.id }
        ids.forEach { delete(it) }
        return ids.size
    }
}

/** Deletes one giveaway's rows (the rest cascade) inside the caller's transaction; returns its private files. */
internal suspend fun GiveawayDatabase.deleteGiveaway(giveawayId: Long): List<File> {
    val files = mediaFileDao().forGiveaway(giveawayId).map { File(it.uri) }.filter { it.isAbsolute }
    giveawayDao().delete(giveawayId)
    return files
}
