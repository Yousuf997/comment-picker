package app.giveaway.core.data.db

import app.giveaway.draw.ExclusionReason
import java.time.Instant

/** One S10 row: the entry decision with its comment. */
data class EntryRow(
    val commentId: String,
    val username: String,
    val isValid: Boolean,
    val exclusionReason: ExclusionReason?,
    val manualNote: String?,
    val text: String,
    val timestamp: Instant,
)

/** The S10 tiles. Every imported comment has one entry decision, so total is the comment count. */
data class EntryCounts(val total: Int, val valid: Int) {
    val excluded: Int get() = total - valid
}
