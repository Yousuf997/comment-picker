package app.giveaway.core.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.giveaway.GiveawayRepository
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

/** Schedules the "entries closed" reminder when a giveaway opens (spec: "remind me at the deadline"). */
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
 * Runs when entries close (plan C-15): reminds the user that the comments can be imported. There's no draw code, so
 * nothing on Instagram is read (plan A35) and the reminder comes even offline.
 */
@HiltWorker
class DeadlineWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val giveaways: GiveawayRepository,
    private val notifier: DeadlineNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val giveaway = giveaways.get(inputData.getLong(KEY_GIVEAWAY_ID, NO_ID))
        // Deleted, or already past this step: nothing to announce.
        if (giveaway?.status == GiveawayStatus.COMMITTED) notifier.entriesClosed(giveaway.id)
        return Result.success()
    }

    companion object {
        const val KEY_GIVEAWAY_ID = "giveawayId"
        private const val NO_ID = -1L

        fun workName(giveawayId: Long) = "deadline-$giveawayId"

        /** Replaces any earlier schedule, so a changed deadline (plan A31) moves the reminder. */
        fun schedule(workManager: WorkManager, giveawayId: Long, closesAt: Instant, now: Instant) {
            val request = OneTimeWorkRequestBuilder<DeadlineWorker>()
                .setInitialDelay(Duration.between(now, closesAt).coerceAtLeast(Duration.ZERO))
                .setInputData(workDataOf(KEY_GIVEAWAY_ID to giveawayId))
                .build()
            workManager.enqueueUniqueWork(workName(giveawayId), ExistingWorkPolicy.REPLACE, request)
        }
    }
}
