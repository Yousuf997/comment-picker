package app.giveaway.core.data.review

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.withTransaction
import app.giveaway.core.data.db.BlocklistEntity
import app.giveaway.core.data.db.EntryCounts
import app.giveaway.core.data.db.EntryRow
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.importing.EntryBuilder
import app.giveaway.draw.CanonicalEntryList
import app.giveaway.draw.ExclusionReason
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.util.Locale
import javax.inject.Inject

/** S10's filter chips. */
enum class EntryListFilter { ALL, VALID, EXCLUDED }

/**
 * S10 Review entries (plan C-20). Manual changes are allowed only before the real draw, each exclusion needs a
 * reason, and every change re-applies the rules so duplicates and counts stay right (spec: Build the entry list).
 */
class EntryRepository @Inject constructor(
    private val db: GiveawayDatabase,
    private val entries: EntryBuilder,
    private val clock: Clock,
) {
    private val dao get() = db.entryDao()

    fun entries(giveawayId: Long, filter: EntryListFilter, query: String): Flow<PagingData<EntryRow>> {
        val valid = when (filter) {
            EntryListFilter.ALL -> null
            EntryListFilter.VALID -> true
            EntryListFilter.EXCLUDED -> false
        }
        val pattern = query.trim().escapeLike()
        return Pager(PagingConfig(pageSize = PAGE_SIZE)) { dao.rows(giveawayId, valid, pattern) }.flow
    }

    fun counts(giveawayId: Long): Flow<EntryCounts> = dao.observeCounts(giveawayId)

    /** Excludes one comment with the organizer's reason (printed on the certificate). */
    suspend fun exclude(giveawayId: Long, commentId: String, reason: String) {
        require(reason.isNotBlank()) { "An exclusion needs a reason" }
        change(giveawayId) {
            val row = checkNotNull(dao.get(giveawayId, commentId)) { "No entry $commentId" }
            val excluded = row.copy(isValid = false, exclusionReason = ExclusionReason.MANUAL)
            dao.upsertAll(listOf(excluded.copy(manualNote = reason.trim())))
        }
    }

    /**
     * Undoes a manual exclusion, or accepts a comment that failed a content check (plan A26). Late, own-account,
     * blocklisted, past-winner and duplicate comments can't be included.
     */
    suspend fun include(giveawayId: Long, commentId: String, note: String = "") {
        change(giveawayId) {
            val row = checkNotNull(dao.get(giveawayId, commentId)) { "No entry $commentId" }
            val updated = when (row.exclusionReason) {
                // Back to the rules' own decision.
                ExclusionReason.MANUAL -> row.copy(isValid = true, exclusionReason = null, manualNote = null)
                in INCLUDABLE -> row.copy(isValid = true, exclusionReason = null, manualNote = note.trim())
                else -> error("${row.exclusionReason} can't be overridden")
            }
            dao.upsertAll(listOf(updated))
        }
    }

    /** Adds the person to the blocklist shared by every giveaway (spec: blocklist). */
    suspend fun addToBlocklist(giveawayId: Long, username: String, note: String? = null) {
        change(giveawayId) {
            db.blocklistDao().upsert(BlocklistEntity(username.lowercase(Locale.ROOT), clock.instant(), note))
        }
    }

    /** The exact list the draw uses: lowercase usernames, code-point order, joined with "\n" (spec). */
    suspend fun canonicalList(giveawayId: Long): CanonicalEntryList =
        CanonicalEntryList.of(dao.validUsernames(giveawayId))

    private suspend fun change(giveawayId: Long, block: suspend () -> Unit) {
        db.withTransaction {
            val status = db.giveawayDao().get(giveawayId)?.status
            check(status == GiveawayStatus.REVIEW) { "Entries can change only before the draw" }
            block()
        }
        entries.rebuild(giveawayId)
    }

    private fun String.escapeLike() = replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    companion object {
        private const val PAGE_SIZE = 50

        /** Content checks a manual include may override (plan A26). */
        val INCLUDABLE = setOf(
            ExclusionReason.TOO_FEW_MENTIONS,
            ExclusionReason.MISSING_HASHTAG,
            ExclusionReason.MISSING_KEYWORD,
        )
    }
}
