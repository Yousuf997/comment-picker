package app.giveaway.core.data.giveaway

import androidx.room.withTransaction
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.draw.clearResult
import app.giveaway.core.data.draw.reopenForReview
import app.giveaway.core.data.importing.EntryBuilder
import app.giveaway.core.data.importing.ImportWork
import app.giveaway.core.data.work.DeadlineScheduler
import app.giveaway.core.instagram.api.IgMedia
import app.giveaway.draw.Rules
import java.io.File
import javax.inject.Inject

/**
 * Changes to a giveaway after it was created (plan A31, A32): its rules and its post can change at any stage. Each
 * change brings the rest along: a moved deadline is rescheduled, entries are filtered again, and a drawn result is
 * cleared so the giveaway is drawn again. No record of the change is kept.
 */
class GiveawayEditor @Inject constructor(
    private val db: GiveawayDatabase,
    private val giveaways: GiveawayRepository,
    private val entries: EntryBuilder,
    private val deadlines: DeadlineScheduler,
    private val imports: ImportWork,
    private val opener: GiveawayOpener,
) {
    /** True when saving now clears a drawn result, so the screen can warn first. */
    suspend fun clearsResult(id: Long): Boolean =
        giveaways.get(id)?.let { GiveawayStateMachine.hasResult(it.status) } ?: false

    suspend fun saveRules(id: Long, rules: Rules) {
        // A draft left by an older version opens first, so it gets its seed and reminder (plan A35).
        opener.open(id)
        val before = checkNotNull(giveaways.get(id)) { "No giveaway $id" }
        val stale = db.withTransaction {
            val files = if (GiveawayStateMachine.hasResult(before.status)) db.reopenForReview(id) else emptyList()
            giveaways.saveRules(id, rules)
            files
        }
        stale.forEach { it.delete() }
        when (checkNotNull(giveaways.get(id)).status) {
            GiveawayStatus.COMMITTED -> if (rules.closesAt != before.closesAt) deadlines.schedule(id, rules.closesAt)
            // While importing, the worker filters with the new rules when it finishes.
            GiveawayStatus.REVIEW -> entries.rebuild(id)
            else -> Unit
        }
    }

    /**
     * A new post. Before the import nothing else changes. After it, the comments, entries, draws and certificates of
     * the old post go and the giveaway waits to import the new post's comments.
     */
    suspend fun changePost(id: Long, media: IgMedia) {
        opener.open(id)
        imports.cancel(id)
        var reimport = false
        val stale = db.withTransaction {
            val giveaway = checkNotNull(db.giveawayDao().get(id)) { "No giveaway $id" }
            db.giveawayDao().update(
                giveaway.copy(
                    igMediaId = media.id,
                    mediaType = if (media.isReel) "REEL" else media.kind.name,
                    thumbnailUrl = media.thumbnailUrl,
                ),
            )
            if (giveaway.status == GiveawayStatus.DRAFT || giveaway.status == GiveawayStatus.COMMITTED) {
                return@withTransaction emptyList<File>()
            }
            val files = db.clearResult(id)
            db.drawDao().deleteAll(id)
            db.entryDao().deleteAll(id)
            db.commentDao().deleteAll(id)
            db.importStateDao().delete(id)
            GiveawayStateMachine.requireMove(giveaway.status, GiveawayStatus.COMMITTED)
            db.giveawayDao().updateStatus(id, GiveawayStatus.COMMITTED)
            reimport = true
            files
        }
        stale.forEach { it.delete() }
        // The reminder comes again for the new post (straight away when entries have already closed).
        if (reimport) deadlines.schedule(id, checkNotNull(giveaways.get(id)).closesAt)
    }
}
