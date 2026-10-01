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
import javax.inject.Inject

/** Giveaways and their rules, moving only along [GiveawayStateMachine]. */
interface GiveawayRepository {
    fun observeSummaries(): Flow<List<GiveawaySummary>>

    suspend fun get(id: Long): GiveawayEntity?

    /** Creates a DRAFT for the chosen post with its rules (end of S7), in one transaction. */
    suspend fun createDraft(media: IgMedia, title: String, ownerUsername: String, rules: Rules): Long

    /** Replaces the rules. Throws [IllegalStateException] once the giveaway is committed (plan A9). */
    suspend fun saveRules(id: Long, rules: Rules)

    suspend fun rules(id: Long): Rules?

    /** Stores the commitment and moves DRAFT -> COMMITTED (S8 "Done"). */
    suspend fun commit(id: Long, commitHash: String, encryptedSeed: ByteArray)

    /** Moves along the state machine; throws [IllegalStateException] for a move the spec doesn't allow. */
    suspend fun transition(id: Long, to: GiveawayStatus)
}

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
        check(GiveawayStateMachine.rulesEditable(giveaway.status)) { "Rules are frozen once the draw is committed" }
        giveaways.update(giveaway.copy(closesAt = rules.closesAt))
        db.rulesDao().upsert(rules.toEntity(id))
    }

    override suspend fun rules(id: Long): Rules? {
        val giveaway = giveaways.get(id) ?: return null
        return db.rulesDao().get(id)?.toModel(giveaway)
    }

    override suspend fun commit(id: Long, commitHash: String, encryptedSeed: ByteArray) = db.withTransaction {
        val giveaway = requireGiveaway(id)
        GiveawayStateMachine.requireMove(giveaway.status, GiveawayStatus.COMMITTED)
        db.commitmentDao().insert(CommitmentEntity(id, encryptedSeed, commitHash, clock.instant(), null))
        giveaways.updateStatus(id, GiveawayStatus.COMMITTED)
    }

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
