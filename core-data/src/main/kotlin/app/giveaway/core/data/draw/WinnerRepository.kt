package app.giveaway.core.data.draw

import androidx.room.withTransaction
import app.giveaway.core.data.db.ConfirmationStatus
import app.giveaway.core.data.db.DrawResultEntity
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.db.PastWinnerEntity
import app.giveaway.draw.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/** One place on S14: a winner, or the alternate who took over a replaced winner's place. */
data class WinnerPlace(
    /** 1-based rank among the winners. */
    val rank: Int,
    val position: Int,
    val username: String,
    val status: ConfirmationStatus,
    /** True when an alternate stepped in for a replaced winner. */
    val promoted: Boolean,
    /** The person's entry comment, for the card (spec: S14). */
    val comment: String?,
)

data class WinnersView(
    val title: String,
    val drawnAt: Instant,
    val entryCount: Int,
    val winners: List<WinnerPlace>,
    /** Alternates not yet used, in order. */
    val alternates: List<String>,
    /** Every replacement so far, for the certificate: who, why, and who took the place. */
    val replacements: List<Replacement>,
) {
    data class Replacement(val replaced: String, val reason: String, val by: String)

    val alternatesUsedUp: Boolean get() = alternates.isEmpty()
}

/**
 * S14 Winners (plan M-01). The draw result never changes; the organizer confirms each winner after the manual follow
 * check, or replaces them with the next alternate and a reason, which the certificate lists (spec: Edge cases).
 */
class WinnerRepository @Inject constructor(
    private val db: GiveawayDatabase,
    private val clock: Clock,
) {
    private val draws get() = db.drawDao()

    fun observe(giveawayId: Long): Flow<WinnersView?> = draws.observeRealResults(giveawayId).map { results ->
        if (results.isEmpty()) null else view(giveawayId, results)
    }

    /** Confirms a winner and adds them to the past winners every giveaway can exclude (spec: S14). */
    suspend fun confirm(giveawayId: Long, position: Int) = change(giveawayId) { results ->
        val place = results.single { it.position == position }
        check(place.confirmationStatus == ConfirmationStatus.PENDING) { "Only a pending winner can be confirmed" }
        draws.updateResult(place.copy(confirmationStatus = ConfirmationStatus.CONFIRMED))
        db.pastWinnerDao().insert(PastWinnerEntity(place.username, giveawayId, clock.instant()))
    }

    /**
     * Replaces a pending winner with the next unused alternate and records why. Returns the alternate's username,
     * or null when every alternate is used (S14 then explains that the giveaway can finish with fewer winners).
     */
    suspend fun replace(giveawayId: Long, position: Int, reason: String): String? {
        require(reason.isNotBlank()) { "A replacement needs a reason" }
        var promoted: String? = null
        change(giveawayId) { results ->
            val place = results.single { it.position == position }
            check(place.confirmationStatus == ConfirmationStatus.PENDING) { "Only a pending winner can be replaced" }
            val used = results.mapNotNull { it.replacedByPosition }.toSet()
            val next = results.firstOrNull { it.role == Role.ALTERNATE && it.position !in used }
            if (next != null) {
                draws.updateResult(
                    place.copy(
                        confirmationStatus = ConfirmationStatus.REPLACED,
                        replacedReason = reason.trim(),
                        replacedByPosition = next.position,
                    ),
                )
                promoted = next.username
            }
        }
        return promoted
    }

    private suspend fun change(giveawayId: Long, block: suspend (List<DrawResultEntity>) -> Unit) {
        db.withTransaction {
            val giveaway = checkNotNull(db.giveawayDao().get(giveawayId)) { "No giveaway $giveawayId" }
            check(giveaway.status == GiveawayStatus.DRAWN) { "Winners change only after the draw, before archiving" }
            val draw = checkNotNull(draws.realDraw(giveawayId)) { "No draw for $giveawayId" }
            block(draws.results(draw.id))
        }
    }

    private suspend fun view(giveawayId: Long, results: List<DrawResultEntity>): WinnersView {
        val giveaway = checkNotNull(db.giveawayDao().get(giveawayId))
        val draw = checkNotNull(draws.realDraw(giveawayId))
        val byPosition = results.associateBy { it.position }
        val used = results.mapNotNull { it.replacedByPosition }.toSet()
        // Follow each winner's place to whoever holds it now: a replaced winner hands it to their alternate.
        val places = results.filter { it.role == Role.WINNER }.mapIndexed { index, winner ->
            var holder = winner
            while (holder.confirmationStatus == ConfirmationStatus.REPLACED) {
                holder = byPosition.getValue(checkNotNull(holder.replacedByPosition))
            }
            WinnerPlace(
                rank = index + 1,
                position = holder.position,
                username = holder.username,
                status = holder.confirmationStatus,
                promoted = holder.role == Role.ALTERNATE,
                comment = draws.entryComment(giveawayId, holder.username),
            )
        }
        val replacements = results.filter { it.confirmationStatus == ConfirmationStatus.REPLACED }.map {
            val by = byPosition.getValue(checkNotNull(it.replacedByPosition)).username
            WinnersView.Replacement(it.username, it.replacedReason.orEmpty(), by)
        }
        return WinnersView(
            title = giveaway.title,
            drawnAt = draw.drawnAt,
            entryCount = draw.entryCount,
            winners = places,
            alternates = results.filter { it.role == Role.ALTERNATE && it.position !in used }.map { it.username },
            replacements = replacements,
        )
    }
}
