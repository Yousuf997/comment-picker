package app.giveaway.core.data.giveaway

import androidx.room.withTransaction
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.work.DeadlineScheduler
import app.giveaway.core.instagram.api.IgMedia
import app.giveaway.draw.Rules
import java.time.Clock
import javax.inject.Inject

/**
 * Opens a giveaway for entries (plan A35). There's no draw code to post, so finishing S7 seals the giveaway's seed,
 * commits it and schedules the "entries closed" reminder in one go.
 */
interface GiveawayOpener {
    /** The end of S7 for a new giveaway; returns its ID. No DRAFT is left behind. */
    suspend fun create(media: IgMedia, title: String, ownerUsername: String, rules: Rules): Long

    /** Opens a DRAFT left by an older version; does nothing for any other status. */
    suspend fun open(giveawayId: Long)
}

internal class DefaultGiveawayOpener @Inject constructor(
    private val db: GiveawayDatabase,
    private val giveaways: GiveawayRepository,
    private val commitments: DrawCommitments,
    private val clock: Clock,
    private val deadlines: DeadlineScheduler,
) : GiveawayOpener {

    override suspend fun create(media: IgMedia, title: String, ownerUsername: String, rules: Rules): Long {
        val id = db.withTransaction { giveaways.createDraft(media, title, ownerUsername, rules).also { commit(it) } }
        // Entries that closed "Now" need no reminder: the user goes straight on to import the comments.
        if (rules.closesAt.isAfter(clock.instant())) deadlines.schedule(id, rules.closesAt)
        return id
    }

    override suspend fun open(giveawayId: Long) {
        val closesAt = db.withTransaction {
            val giveaway = giveaways.get(giveawayId)
            if (giveaway?.status != GiveawayStatus.DRAFT) return@withTransaction null
            commit(giveawayId)
            giveaway.closesAt
        } ?: return
        deadlines.schedule(giveawayId, closesAt)
    }

    /** Seals the seed the draw will use (made on the first call) and moves DRAFT -> COMMITTED. */
    private suspend fun commit(giveawayId: Long) {
        commitments.commitHashFor(giveawayId)
        giveaways.commit(giveawayId)
    }
}
