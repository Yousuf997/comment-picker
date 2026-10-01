package app.giveaway.core.data.importing

import androidx.room.withTransaction
import app.giveaway.core.data.db.CommentEntity
import app.giveaway.core.data.db.EntryEntity
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.giveaway.GiveawayStateMachine
import app.giveaway.draw.EntryDecision
import app.giveaway.draw.EntryFilter
import app.giveaway.draw.ExclusionReason
import app.giveaway.draw.FilterContext
import app.giveaway.draw.RawComment
import app.giveaway.draw.Rules
import app.giveaway.draw.RulesFilterV1
import javax.inject.Inject

/**
 * Turns imported comments into entries (plan C-19, spec: Build the entry list). Comments stream from the database in
 * (timestamp, id) order a page at a time, so 50,000 of them never sit in memory at once. The organizer's manual
 * choices survive a rebuild: a MANUAL exclusion keeps its note, and a manual inclusion is a valid entry with a note.
 */
class EntryBuilder internal constructor(
    private val db: GiveawayDatabase,
    private val filter: EntryFilter,
) {
    @Inject
    constructor(db: GiveawayDatabase) : this(db, RulesFilterV1)

    /** Rebuilds every entry and moves IMPORTING -> REVIEW. Calling it again in REVIEW re-applies the rules. */
    suspend fun rebuild(giveawayId: Long) = db.withTransaction {
        val giveaway = checkNotNull(db.giveawayDao().get(giveawayId)) { "No giveaway $giveawayId" }
        val rules = checkNotNull(db.rulesDao().get(giveawayId)) { "No rules for giveaway $giveawayId" }.let {
            Rules(
                minMentions = it.minMentions,
                requiredHashtag = it.requiredHashtag,
                keyword = it.keyword,
                onePerPerson = it.onePerPerson,
                excludePastWinners = it.excludePastWinners,
                excludeBlocklist = it.excludeBlocklist,
                closesAt = giveaway.closesAt,
                winnersCount = it.winnersCount,
                alternatesCount = it.alternatesCount,
            )
        }
        val entries = db.entryDao()
        val manual = entries.manualDecisions(giveawayId)
        val context = FilterContext(
            ownerUsername = giveaway.ownerUsername,
            blocklist = db.blocklistDao().usernames().toSet(),
            pastWinners = db.pastWinnerDao().usernamesExcept(giveawayId).toSet(),
            manualExclusions = manual.filter { it.exclusionReason == ExclusionReason.MANUAL }
                .associate { it.commentId to it.manualNote.orEmpty() },
            manualInclusions = manual.filter { it.isValid }.map { it.commentId }.toSet(),
        )
        val inclusionNotes = manual.filter { it.isValid }.associate { it.commentId to it.manualNote }
        entries.deleteAll(giveawayId)
        filter.evaluate(comments(giveawayId), rules, context)
            .map { it.toEntity(giveawayId, inclusionNotes) }
            .chunked(BATCH)
            .forEach { entries.insertAllBlocking(it) }
        if (giveaway.status == GiveawayStatus.IMPORTING) {
            GiveawayStateMachine.requireMove(giveaway.status, GiveawayStatus.REVIEW)
            db.giveawayDao().updateStatus(giveawayId, GiveawayStatus.REVIEW)
        }
    }

    /** Keyset paging over (timestamp, id): runs inside the rebuild transaction, on its thread. */
    private fun comments(giveawayId: Long): Sequence<RawComment> = sequence {
        var page = db.commentDao().firstPage(giveawayId, BATCH)
        while (page.isNotEmpty()) {
            page.forEach { yield(RawComment(it.id, it.username, it.text, it.timestamp)) }
            val last: CommentEntity = page.last()
            page = db.commentDao().pageAfter(giveawayId, last.timestamp, last.id, BATCH)
        }
    }

    private fun EntryDecision.toEntity(giveawayId: Long, inclusionNotes: Map<String, String?>) = EntryEntity(
        giveawayId = giveawayId,
        commentId = commentId,
        username = username,
        isValid = isValid,
        exclusionReason = reason,
        manualNote = manualNote ?: inclusionNotes[commentId].takeIf { isValid },
    )

    private companion object {
        const val BATCH = 500
    }
}
