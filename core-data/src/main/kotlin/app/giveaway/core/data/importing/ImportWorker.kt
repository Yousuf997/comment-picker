package app.giveaway.core.data.importing

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.giveaway.GiveawayRepository
import app.giveaway.core.instagram.api.IgError
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import java.time.Duration
import kotlin.random.Random

/** The import's progress notification; the text is generic (plan A23). Implemented by the app module. */
fun interface ImportNotifications {
    fun foregroundInfo(giveawayId: Long, imported: Int, expected: Int): ForegroundInfo
}

/**
 * Runs the import as a foreground `dataSync` worker with a progress notification, so it keeps going when the user
 * leaves the app (spec: Battery). Network trouble and rate limits back off exponentially, with jitter so retries
 * don't line up; a short import (a mismatch) is retried until [MAX_FAILED_RETRIES]. A deleted post or an expired
 * sign-in stops until the user acts on S9.
 */
@HiltWorker
class ImportWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val importer: CommentImporter,
    private val entries: EntryBuilder,
    private val giveaways: GiveawayRepository,
    private val notifications: ImportNotifications,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_GIVEAWAY_ID, NO_ID)
        if (runAttemptCount > 0) delay(Random.nextLong(MAX_JITTER_MS))
        setForeground(notifications.foregroundInfo(id, imported = 0, expected = 0))
        return when (val run = importer.run(id, onProgress = { imported, expected ->
            setForeground(notifications.foregroundInfo(id, imported, expected))
        })) {
            ImportRun.Complete -> {
                // Checking hashtags and mentions, removing duplicates, applying exclusions (S9 checklist).
                if (giveaways.get(id)?.status == GiveawayStatus.IMPORTING) entries.rebuild(id)
                Result.success()
            }
            ImportRun.Mismatch -> if (importer.canRetry(id)) Result.retry() else Result.success()
            is ImportRun.Stopped -> when (run.error) {
                IgError.Offline, is IgError.RateLimited, is IgError.Server -> Result.retry()
                IgError.MediaNotFound, IgError.TokenExpired -> Result.success()
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        notifications.foregroundInfo(inputData.getLong(KEY_GIVEAWAY_ID, NO_ID), imported = 0, expected = 0)

    companion object {
        const val KEY_GIVEAWAY_ID = "giveawayId"
        private const val NO_ID = -1L
        private const val MAX_JITTER_MS = 5_000L
        private val INITIAL_BACKOFF = Duration.ofSeconds(30)

        fun workName(giveawayId: Long) = "import-$giveawayId"

        /** Starts the import, or leaves a running one alone; it resumes from the saved cursor either way. */
        fun enqueue(workManager: WorkManager, giveawayId: Long) {
            val request = OneTimeWorkRequestBuilder<ImportWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, INITIAL_BACKOFF)
                .setInputData(workDataOf(KEY_GIVEAWAY_ID to giveawayId))
                .build()
            workManager.enqueueUniqueWork(workName(giveawayId), ExistingWorkPolicy.KEEP, request)
        }
    }
}
