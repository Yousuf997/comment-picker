package app.giveaway.core.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.giveaway.GiveawayRepository
import app.giveaway.core.instagram.api.IgError
import app.giveaway.core.instagram.api.IgResult
import app.giveaway.core.instagram.api.InstagramRepository
import app.giveaway.draw.Commit
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/** Tells the user a giveaway's entries have closed. The text is generic: no titles or handles (plan A23). */
fun interface DeadlineNotifier {
    fun entriesClosed(giveawayId: Long)
}

/** Schedules the deadline check when a giveaway is committed (spec: S8 "remind me at the deadline"). */
fun interface DeadlineScheduler {
    fun schedule(giveawayId: Long, closesAt: Instant)
}

internal class WorkManagerDeadlineScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) : DeadlineScheduler {
    override fun schedule(giveawayId: Long, closesAt: Instant) =
        DeadlineWorker.schedule(WorkManager.getInstance(context), giveawayId, closesAt, clock.instant())
}

/**
 * Runs when entries close (plan C-15, A15): re-reads the caption and, when it contains the commit hash, records
 * `captionVerifiedAt` as evidence the code was public by the deadline. Then posts the reminder. Network trouble is
 * retried with backoff; a deleted post or expired sign-in leaves the caption unverified, and S11 checks it again
 * before the draw.
 */
@HiltWorker
class DeadlineWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val giveaways: GiveawayRepository,
    private val instagram: InstagramRepository,
    private val notifier: DeadlineNotifier,
    private val clock: Clock,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_GIVEAWAY_ID, NO_ID)
        val giveaway = giveaways.get(id)
        val commitment = giveaways.commitment(id)
        // Deleted, not committed, or already past this step: nothing to check or announce.
        if (giveaway == null || commitment == null || giveaway.status != GiveawayStatus.COMMITTED) {
            return Result.success()
        }
        if (commitment.captionVerifiedAt == null) {
            when (val caption = instagram.mediaById(giveaway.igMediaId)) {
                is IgResult.Ok ->
                    if (Commit.captionContains(caption.value.caption, commitment.commitHash)) {
                        giveaways.markCaptionVerified(id, clock.instant())
                    }
                is IgResult.Err ->
                    if (caption.error.isTemporary() && runAttemptCount < MAX_RETRIES) return Result.retry()
            }
        }
        notifier.entriesClosed(id)
        return Result.success()
    }

    private fun IgError.isTemporary() = this is IgError.Offline || this is IgError.RateLimited || this is IgError.Server

    companion object {
        const val KEY_GIVEAWAY_ID = "giveawayId"
        private const val NO_ID = -1L
        private const val MAX_RETRIES = 5

        fun workName(giveawayId: Long) = "deadline-$giveawayId"

        /** Replaces any earlier schedule, so a changed deadline (plan A31) moves the check. */
        fun schedule(workManager: WorkManager, giveawayId: Long, closesAt: Instant, now: Instant) {
            val request = OneTimeWorkRequestBuilder<DeadlineWorker>()
                .setInitialDelay(Duration.between(now, closesAt).coerceAtLeast(Duration.ZERO))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInputData(workDataOf(KEY_GIVEAWAY_ID to giveawayId))
                .build()
            workManager.enqueueUniqueWork(workName(giveawayId), ExistingWorkPolicy.REPLACE, request)
        }
    }
}
