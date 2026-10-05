package app.giveaway.core.data.cleanup

import androidx.room.withTransaction
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.importing.ImportWork
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.map

/**
 * Deletes giveaways on request (plan A33): one from Home, or all of them from Settings. Everything that belongs to a
 * giveaway goes with it (rules, commitment, comments, entries, draws, private files). The account, settings, keys,
 * blocklist and past winners stay, as with auto-delete; "Delete everything" is the way to remove those too.
 */
interface GiveawayRemoval {
    /** How many giveaways there are, for the "Delete all" confirmation. */
    fun observeCount(): Flow<Int>

    suspend fun delete(giveawayId: Long)

    /** Returns how many were deleted. */
    suspend fun deleteAll(): Int
}

class GiveawayDeleter @Inject constructor(
    private val db: GiveawayDatabase,
    private val imports: ImportWork,
) : GiveawayRemoval {
    override fun observeCount(): Flow<Int> = db.giveawayDao().observeAll().map { it.size }.distinctUntilChanged()

    override suspend fun delete(giveawayId: Long) {
        imports.cancel(giveawayId)
        db.withTransaction { db.deleteGiveaway(giveawayId) }.forEach { it.delete() }
        // A scheduled deadline check finds no giveaway and does nothing.
    }

    override suspend fun deleteAll(): Int {
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
