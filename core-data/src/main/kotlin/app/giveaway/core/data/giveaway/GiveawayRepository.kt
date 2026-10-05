package app.giveaway.core.data.giveaway

import androidx.room.withTransaction
import app.giveaway.core.data.db.CommitmentEntity
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.db.GiveawayEntity
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.db.GiveawaySummary
import app.giveaway.core.data.db.RulesEntity
import app.giveaway.core.data.settings.SettingsRepository
import app.giveaway.core.instagram.api.IgMedia
import app.giveaway.draw.Rules
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/** Giveaways and their rules, moving only along [GiveawayStateMachine]. */
interface GiveawayRepository {
    fun observeSummaries(): Flow<List<GiveawaySummary>>

    suspend fun get(id: Long): GiveawayEntity?

    /** Creates a DRAFT for the chosen post with its rules (end of S7), in one transaction. */
    suspend fun createDraft(media: IgMedia, title: String, ownerUsername: String, rules: Rules): Long

    /**
     * Replaces the rules at any stage (plan A31). This only stores them; [GiveawayEditor] applies what follows (a new
     * deadline, filtering again, clearing a result).
     */
    suspend fun saveRules(id: Long, rules: Rules)

    suspend fun rules(id: Long): Rules?

    suspend fun commitment(id: Long): CommitmentEntity?

    /**
     * Stores the sealed seed and its hash when S8 first shows the code, while the giveaway is still a DRAFT, so
     * going back to S7 keeps the same code. Throws [IllegalStateException] if it isn't a draft or already has one.
     */
    suspend fun saveCommitment(id: Long, commitHash: String, encryptedSeed: ByteArray)

    /** S8 "Done": moves DRAFT -> COMMITTED. Needs a stored commitment. */
    suspend fun commit(id: Long)

    /** The deadline check found the code in the caption (plan A15). */
    suspend fun markCaptionVerified(id: Long, at: Instant)

    /** Moves along the state machine; throws [IllegalStateException] for a move the spec doesn't allow. */
    suspend fun transition(id: Long, to: GiveawayStatus)
}

@Suppress("TooManyFunctions") // The giveaway aggregate: status, rules and commitment change together.
class DefaultGiveawayRepository @Inject constructor(
    private val db: GiveawayDatabase,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : GiveawayRepository {

    private val giveaways get() = db.giveawayDao()

    override fun observeSummaries(): Flow<List<GiveawaySummary>> = giveaways.observeSummaries()

    override suspend fun get(id: Long): GiveawayEntity? = giveaways.get(id)

    override suspend fun createDraft(media: IgMedia, title: String, ownerUsername: String, rules: Rules): Long =
        db.withTransaction {
            val id = giveaways.insert(
                GiveawayEntity(
                    title = title,
                    igMediaId = media.id,
                    mediaType = if (media.isReel) "REEL" else media.kind.name,
                    thumbnailUrl = media.thumbnailUrl,
                    status = GiveawayStatus.DRAFT,
                    createdAt = clock.instant(),
                    closesAt = rules.closesAt,
                    autoDeleteAt = null,
                    ownerUsername = ownerUsername,
                ),
            )
            db.rulesDao().upsert(rules.toEntity(id))
            id
        }

    override suspend fun saveRules(id: Long, rules: Rules) = db.withTransaction {
        val giveaway = requireGiveaway(id)
        giveaways.update(giveaway.copy(closesAt = rules.closesAt))
        db.rulesDao().upsert(rules.toEntity(id))
    }

    override suspend fun rules(id: Long): Rules? {
        val giveaway = giveaways.get(id) ?: return null
        return db.rulesDao().get(id)?.toModel(giveaway)
    }

    override suspend fun commitment(id: Long): CommitmentEntity? = db.commitmentDao().get(id)

    override suspend fun saveCommitment(id: Long, commitHash: String, encryptedSeed: ByteArray) = db.withTransaction {
        val giveaway = requireGiveaway(id)
        check(giveaway.status == GiveawayStatus.DRAFT) { "A commitment is made only while drafting" }
        check(db.commitmentDao().get(id) == null) { "Giveaway $id already has a commitment" }
        db.commitmentDao().insert(CommitmentEntity(id, encryptedSeed, commitHash, clock.instant(), null))
    }

    override suspend fun commit(id: Long) = db.withTransaction {
        val giveaway = requireGiveaway(id)
        GiveawayStateMachine.requireMove(giveaway.status, GiveawayStatus.COMMITTED)
        checkNotNull(db.commitmentDao().get(id)) { "Show the draw code (S8) before committing" }
        giveaways.updateStatus(id, GiveawayStatus.COMMITTED)
    }

    override suspend fun markCaptionVerified(id: Long, at: Instant) = db.commitmentDao().setCaptionVerified(id, at)

    override suspend fun transition(id: Long, to: GiveawayStatus) {
        val autoDeleteDays = if (to == GiveawayStatus.ARCHIVED) settings.get().autoDeleteDays else null
        db.withTransaction {
            GiveawayStateMachine.requireMove(requireGiveaway(id).status, to)
            giveaways.updateStatus(id, to)
            // Auto-delete counts from when the giveaway is finished (spec: Privacy; default 90 days).
            if (autoDeleteDays != null) {
                giveaways.setAutoDeleteAt(id, clock.instant().plus(Duration.ofDays(autoDeleteDays.toLong())))
            }
        }
    }

    private suspend fun requireGiveaway(id: Long) = checkNotNull(giveaways.get(id)) { "No giveaway $id" }

    private fun Rules.toEntity(giveawayId: Long) = RulesEntity(
        giveawayId = giveawayId,
        minMentions = minMentions,
        requiredHashtag = requiredHashtag,
        keyword = keyword,
        onePerPerson = onePerPerson,
        excludePastWinners = excludePastWinners,
        excludeBlocklist = excludeBlocklist,
        winnersCount = winnersCount,
        alternatesCount = alternatesCount,
    )

    private fun RulesEntity.toModel(giveaway: GiveawayEntity) = Rules(
        minMentions = minMentions,
        requiredHashtag = requiredHashtag,
        keyword = keyword,
        onePerPerson = onePerPerson,
        excludePastWinners = excludePastWinners,
        excludeBlocklist = excludeBlocklist,
        closesAt = giveaway.closesAt,
        winnersCount = winnersCount,
        alternatesCount = alternatesCount,
    )
}
