package app.giveaway.core.data.giveaway

import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.db.GiveawayStatus.ARCHIVED
import app.giveaway.core.data.db.GiveawayStatus.COMMITTED
import app.giveaway.core.data.db.GiveawayStatus.DRAFT
import app.giveaway.core.data.db.GiveawayStatus.DRAWN
import app.giveaway.core.data.db.GiveawayStatus.IMPORTING
import app.giveaway.core.data.db.GiveawayStatus.REVIEW

/**
 * The six giveaway states and the moves between them (spec: User flows). Only review can loop back (to re-import),
 * and nothing leaves DRAWN except archiving: once the real draw ran, the result can't be redone.
 */
object GiveawayStateMachine {

    private val allowed: Map<GiveawayStatus, Set<GiveawayStatus>> = mapOf(
        DRAFT to setOf(COMMITTED),
        COMMITTED to setOf(IMPORTING),
        IMPORTING to setOf(REVIEW),
        REVIEW to setOf(IMPORTING, DRAWN),
        DRAWN to setOf(ARCHIVED),
        ARCHIVED to emptySet(),
    )

    fun canMove(from: GiveawayStatus, to: GiveawayStatus): Boolean = to in allowed.getValue(from)

    /** Throws [IllegalStateException] for a move the spec doesn't allow. */
    fun requireMove(from: GiveawayStatus, to: GiveawayStatus) =
        check(canMove(from, to)) { "A giveaway can't move from $from to $to" }

    /** Rules are frozen once the draw code is committed (plan A9). */
    fun rulesEditable(status: GiveawayStatus): Boolean = status == DRAFT
}
