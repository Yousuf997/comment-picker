package app.giveaway.core.data.draw

import androidx.room.withTransaction
import app.giveaway.core.data.db.CaptionCheck
import app.giveaway.core.data.db.ConfirmationStatus
import app.giveaway.core.data.db.DrawEntity
import app.giveaway.core.data.db.DrawResultEntity
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.db.GiveawayEntity
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.giveaway.GiveawayStateMachine
import app.giveaway.core.data.giveaway.SeedVault
import app.giveaway.core.data.importing.isComplete
import app.giveaway.draw.CanonicalEntryList
import app.giveaway.draw.Commit
import app.giveaway.draw.DrawOutcome
import app.giveaway.draw.DrawRecord
import app.giveaway.draw.DrawV1
import app.giveaway.draw.ExclusionReason
import app.giveaway.draw.Pick
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

/** Signs draw records with the device key (spec: Storage and keys). Faked in tests. */
interface RecordSigner {
    fun sign(record: ByteArray): Signature

    class Signature(val bytes: ByteArray, val publicKeySpki: ByteArray, val fingerprint: String)
}

/** A saved real draw, for S12 to replay and S14 to show. */
data class SavedDraw(
    val title: String,
    val picks: List<Pick>,
    /** A sample of entrants for the reel to scroll past. */
    val entrants: List<String>,
    val drawnAt: Instant,
    val entryCount: Int,
)

/** What S11 knows before the draw: the caption re-read and the integrity check (spec: S11 checks). */
data class DrawChecks(val captionCheck: CaptionCheck, val integrityVerified: Boolean)

/**
 * Runs draws (plan C-23). The real draw is computed, signed and saved in one transaction before any animation, so a
 * killed app reopens S14 with the same result (spec: Edge cases). A giveaway has one real draw; test draws use a fresh
 * seed, never the committed one, and never count (spec: Test draws).
 */
class DrawService @Inject constructor(
    private val db: GiveawayDatabase,
    private val vault: SeedVault,
    private val signer: RecordSigner,
    private val clock: Clock,
) {
    /** The real draw: reveals the committed seed, selects, signs and stores. Throws if the giveaway was drawn. */
    suspend fun realDraw(giveawayId: Long, checks: DrawChecks): Long {
        val inputs = inputs(giveawayId)
        check(db.drawDao().realDraw(giveawayId) == null) { "Giveaway $giveawayId already has its real draw" }
        val commitment = checkNotNull(db.commitmentDao().get(giveawayId)) { "No commitment for $giveawayId" }
        val seed = vault.open(giveawayId, commitment.encryptedSeed)
        try {
            check(Commit.matches(seed, commitment.commitHash)) { "The stored seed doesn't match the commitment" }
            val outcome = DrawV1.select(seed, inputs.list, inputs.winners, inputs.alternates)
            val drawnAt = clock.instant()
            val record = record(inputs, commitment.commitHash, seed, outcome, drawnAt, checks)
            val signature = signer.sign(record.canonicalBytes())
            val draw = DrawEntity(
                giveawayId = giveawayId,
                isTest = false,
                realGiveawayId = giveawayId,
                seed = seed.copyOf(),
                entryListHash = inputs.list.hashHex,
                drawnAt = drawnAt,
                algorithmVersion = outcome.algorithmVersion,
                deviceSignature = signature.bytes,
                signerPublicKey = signature.publicKeySpki,
                signerFingerprint = signature.fingerprint,
                captionCheck = checks.captionCheck,
                integrityVerified = checks.integrityVerified,
                partialImport = inputs.partialImport,
                entryCount = inputs.list.size,
            )
            return db.withTransaction {
                check(db.drawDao().realDraw(giveawayId) == null) { "Giveaway $giveawayId already has its real draw" }
                val drawId = db.drawDao().insert(draw)
                db.drawDao().insertResults(results(drawId, outcome))
                GiveawayStateMachine.requireMove(inputs.giveaway.status, GiveawayStatus.DRAWN)
                db.giveawayDao().updateStatus(giveawayId, GiveawayStatus.DRAWN)
                drawId
            }
        } finally {
            seed.fill(0)
        }
    }

    /** The saved real draw for S12 to replay and S14 to show, or null before the draw. */
    suspend fun savedDraw(giveawayId: Long): SavedDraw? {
        val draw = db.drawDao().realDraw(giveawayId) ?: return null
        val giveaway = db.giveawayDao().get(giveawayId) ?: return null
        val picks = db.drawDao().results(draw.id).map { Pick(it.position, it.username, it.role) }
        // The reel only needs a sample of names to scroll past (spec: S12); the result is already fixed.
        val entrants = CanonicalEntryList.of(db.entryDao().validUsernames(giveawayId)).usernames.distinct()
            .take(REEL_SAMPLE)
        return SavedDraw(giveaway.title, picks, entrants, draw.drawnAt, draw.entryCount)
    }

    /** A practice run with a fresh random seed: labelled TEST, unsigned, never the committed seed (spec). */
    suspend fun testDraw(giveawayId: Long, random: SecureRandom = SecureRandom()): DrawOutcome {
        val inputs = inputs(giveawayId)
        val seed = Commit.newSeed(random)
        val outcome = DrawV1.select(seed, inputs.list, inputs.winners, inputs.alternates)
        db.withTransaction {
            val drawId = db.drawDao().insert(
                DrawEntity(
                    giveawayId = giveawayId,
                    isTest = true,
                    realGiveawayId = null,
                    seed = seed,
                    entryListHash = inputs.list.hashHex,
                    drawnAt = clock.instant(),
                    algorithmVersion = outcome.algorithmVersion,
                    deviceSignature = ByteArray(0),
                    signerPublicKey = ByteArray(0),
                    signerFingerprint = "",
                    captionCheck = CaptionCheck.NOT_CHECKED,
                    integrityVerified = false,
                    partialImport = inputs.partialImport,
                    entryCount = inputs.list.size,
                ),
            )
            db.drawDao().insertResults(results(drawId, outcome))
        }
        return outcome
    }

    private class Inputs(
        val giveaway: GiveawayEntity,
        val list: CanonicalEntryList,
        val winners: Int,
        val alternates: Int,
        val partialImport: Boolean,
        val manualExclusions: List<DrawRecord.ManualExclusion>,
    )

    private suspend fun inputs(giveawayId: Long): Inputs {
        val giveaway = checkNotNull(db.giveawayDao().get(giveawayId)) { "No giveaway $giveawayId" }
        check(giveaway.status == GiveawayStatus.REVIEW) { "Draws run from review (S10/S11), not ${giveaway.status}" }
        val rules = checkNotNull(db.rulesDao().get(giveawayId)) { "No rules for $giveawayId" }
        val list = CanonicalEntryList.of(db.entryDao().validUsernames(giveawayId))
        check(list.size > 0) { "No valid entries" }
        val importState = db.importStateDao().get(giveawayId)
        val manual = db.entryDao().manualDecisions(giveawayId)
            .filter { it.exclusionReason == ExclusionReason.MANUAL }
            .map { DrawRecord.ManualExclusion(it.username, it.manualNote.orEmpty()) }
            // A fixed order, so the signed record can be rebuilt from the database exactly.
            .sortedWith(compareBy({ it.username }, { it.reason }))
        return Inputs(
            giveaway = giveaway,
            list = list,
            winners = rules.winnersCount,
            alternates = rules.alternatesCount,
            partialImport = importState?.let { it.acceptedPartial && !it.isComplete() } ?: false,
            manualExclusions = manual,
        )
    }

    private fun record(
        inputs: Inputs,
        commitHash: String,
        seed: ByteArray,
        outcome: DrawOutcome,
        drawnAt: Instant,
        checks: DrawChecks,
    ) = DrawRecord(
        algorithmVersion = outcome.algorithmVersion,
        account = inputs.giveaway.ownerUsername,
        postId = inputs.giveaway.igMediaId,
        title = inputs.giveaway.title,
        entriesClosedAt = inputs.giveaway.closesAt,
        commitHash = commitHash,
        seedHex = seed.toHex(),
        entryListHash = inputs.list.hashHex,
        entryCount = inputs.list.size,
        winnersRequested = inputs.winners,
        alternatesRequested = inputs.alternates,
        picks = outcome.picks,
        drawnAt = drawnAt,
        captionCheck = checks.captionCheck.name,
        integrityVerified = checks.integrityVerified,
        partialImport = inputs.partialImport,
        manualExclusions = inputs.manualExclusions,
    )

    private fun results(drawId: Long, outcome: DrawOutcome) = outcome.picks.map {
        DrawResultEntity(
            drawId = drawId,
            position = it.position,
            username = it.username,
            role = it.role,
            confirmationStatus = ConfirmationStatus.PENDING,
            replacedReason = null,
            replacedByPosition = null,
        )
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(Locale.ROOT, it) }

    private companion object {
        const val REEL_SAMPLE = 200
    }
}
