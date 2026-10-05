package app.giveaway.core.data.giveaway

import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.db.GiveawayStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Which setup steps of an existing giveaway can open from the step bar (plan X-2): S6 post (1), S7 rules (2),
 * S8 draw code (3), S9 import (4), S10 review (5), S11 draw (6). The post and the rules can change at any stage
 * (plan A31, A32); the import needs the code locked in, review and the draw need the entries.
 */
class WizardProgress @Inject constructor(private val db: GiveawayDatabase) {

    fun steps(giveawayId: Long): Flow<Set<Int>> =
        db.giveawayDao().observeStatus(giveawayId).map { reachable(it) }.distinctUntilChanged()

    fun status(giveawayId: Long): Flow<GiveawayStatus?> = db.giveawayDao().observeStatus(giveawayId)

    companion object {
        const val POST = 1
        const val RULES = 2
        const val CODE = 3
        const val IMPORT = 4
        const val REVIEW = 5
        const val DRAW = 6

        fun reachable(status: GiveawayStatus?): Set<Int> = when (status) {
            null -> emptySet()
            GiveawayStatus.DRAFT -> setOf(POST, RULES, CODE)
            GiveawayStatus.COMMITTED, GiveawayStatus.IMPORTING -> setOf(POST, RULES, CODE, IMPORT)
            GiveawayStatus.REVIEW, GiveawayStatus.DRAWN, GiveawayStatus.ARCHIVED ->
                setOf(POST, RULES, CODE, IMPORT, REVIEW, DRAW)
        }
    }
}
