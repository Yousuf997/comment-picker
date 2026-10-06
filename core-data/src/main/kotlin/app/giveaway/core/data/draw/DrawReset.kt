package app.giveaway.core.data.draw

import androidx.room.withTransaction
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.db.MediaKind
import app.giveaway.core.data.giveaway.GiveawayStateMachine
import app.giveaway.core.data.giveaway.SeedVault
import app.giveaway.draw.Commit
import java.io.File
import java.time.Clock
import javax.inject.Inject

/**
 * Takes a drawn giveaway back to S11 (plan A30, A31). The real draw and its results go, as do the past winners it
 * confirmed and its certificate and unsaved video; videos already saved to the gallery belong to the user and stay.
 * Nothing records the earlier result.
 */
class DrawReset(
    private val db: GiveawayDatabase,
    private val vault: SeedVault,
    private val clock: Clock,
    /** 32 bytes from SecureRandom; tests pass a fixed source. */
    private val newSeed: () -> ByteArray,
) {
    @Inject
    constructor(db: GiveawayDatabase, vault: SeedVault, clock: Clock) : this(db, vault, clock, { Commit.newSeed() })

    /**
     * Redraw: clears the result and seals a fresh seed, because the same seed and entries would pick the same people.
     */
    suspend fun redraw(giveawayId: Long) {
        val seed = newSeed()
        try {
            val sealed = vault.seal(giveawayId, seed)
            val hash = Commit.commitHash(seed)
            val files = db.withTransaction {
                val stale = db.reopenForReview(giveawayId)
                db.commitmentDao().replace(giveawayId, sealed, hash, clock.instant())
                stale
            }
            files.forEach { it.delete() }
        } finally {
            seed.fill(0)
        }
    }
}

/**
 * Clears a drawn giveaway's result and moves it back to REVIEW, inside the caller's transaction. Returns the private
 * files to delete once the transaction has committed.
 */
internal suspend fun GiveawayDatabase.reopenForReview(giveawayId: Long): List<File> {
    val giveaway = checkNotNull(giveawayDao().get(giveawayId)) { "No giveaway $giveawayId" }
    check(GiveawayStateMachine.hasResult(giveaway.status)) { "Giveaway $giveawayId has no result to clear" }
    val files = clearResult(giveawayId)
    GiveawayStateMachine.requireMove(giveaway.status, GiveawayStatus.REVIEW)
    giveawayDao().updateStatus(giveawayId, GiveawayStatus.REVIEW)
    return files
}

/** Removes the real draw, its past winners, certificates and unsaved video; inside the caller's transaction. */
internal suspend fun GiveawayDatabase.clearResult(giveawayId: Long): List<File> {
    drawDao().deleteRealDraw(giveawayId)
    pastWinnerDao().deleteForGiveaway(giveawayId)
    val stale = mediaFileDao().forGiveaway(giveawayId).filter { it.kind != MediaKind.VIDEO || it.pendingDecision }
    stale.forEach { mediaFileDao().delete(it) }
    giveawayDao().setAutoDeleteAt(giveawayId, null)
    return stale.map { File(it.uri) }.filter { it.isAbsolute }
}
