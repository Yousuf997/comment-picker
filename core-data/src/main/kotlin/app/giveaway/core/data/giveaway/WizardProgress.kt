package app.giveaway.core.data.giveaway

import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.db.GiveawayStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Which setup steps of an existing giveaway can open from the step bar (plan X-2, A35): S6 post (1), S7 rules (2),
 * S9 import (3), S10 review (4), S11 draw (5). The post and the rules can change at any stage (plan A31, A32); the
 * import needs the giveaway open for entries, review and the draw need the entries.
 */
class WizardProgress @Inject constructor(private val db: GiveawayDatabase) {

    fun steps(giveawayId: Long): Flow<Set<Int>> =
        db.giveawayDao().observeStatus(giveawayId).map { reachable(it) }.distinctUntilChanged()

    fun status(giveawayId: Long): Flow<GiveawayStatus?> = db.giveawayDao().observeStatus(giveawayId)

    companion object {
        const val POST = 1
        const val RULES = 2
        const val IMPORT = 3
        const val REVIEW = 4
        const val DRAW = 5

        fun reachable(status: GiveawayStatus?): Set<Int> = when (status) {
            null -> emptySet()
            GiveawayStatus.DRAFT -> setOf(POST, RULES)
            GiveawayStatus.COMMITTED, GiveawayStatus.IMPORTING -> setOf(POST, RULES, IMPORT)
            GiveawayStatus.REVIEW, GiveawayStatus.DRAWN, GiveawayStatus.ARCHIVED ->
                setOf(POST, RULES, IMPORT, REVIEW, DRAW)
        }
    }
}
