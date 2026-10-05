package app.giveaway.core.data.giveaway

import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.db.GiveawayStatus.ARCHIVED
import app.giveaway.core.data.db.GiveawayStatus.COMMITTED
import app.giveaway.core.data.db.GiveawayStatus.DRAFT
import app.giveaway.core.data.db.GiveawayStatus.DRAWN
import app.giveaway.core.data.db.GiveawayStatus.IMPORTING
import app.giveaway.core.data.db.GiveawayStatus.REVIEW

/**
 * The six giveaway states and the moves between them (spec: User flows, changed by plan A30–A32). Review can loop
 * back to re-import; a drawn or archived giveaway goes back to review for a redraw or after its rules or entries
 * change; and changing the post after commit returns to COMMITTED so the comments are imported again.
 */
object GiveawayStateMachine {

    private val allowed: Map<GiveawayStatus, Set<GiveawayStatus>> = mapOf(
        DRAFT to setOf(COMMITTED),
        COMMITTED to setOf(IMPORTING),
        IMPORTING to setOf(REVIEW, COMMITTED),
        REVIEW to setOf(IMPORTING, DRAWN, COMMITTED),
        DRAWN to setOf(ARCHIVED, REVIEW, COMMITTED),
        ARCHIVED to setOf(REVIEW, COMMITTED),
    )

    fun canMove(from: GiveawayStatus, to: GiveawayStatus): Boolean = to in allowed.getValue(from)

    /** Throws [IllegalStateException] for a move the state machine doesn't allow. */
    fun requireMove(from: GiveawayStatus, to: GiveawayStatus) =
        check(canMove(from, to)) { "A giveaway can't move from $from to $to" }

    /** A real draw exists for giveaways in these states; editing their rules or entries clears it (plan A31). */
    fun hasResult(status: GiveawayStatus): Boolean = status == DRAWN || status == ARCHIVED
}
