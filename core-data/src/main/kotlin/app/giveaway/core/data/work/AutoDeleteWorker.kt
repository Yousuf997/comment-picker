package app.giveaway.core.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.giveaway.core.data.cleanup.DataWiper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Daily auto-delete of finished giveaways (spec: Privacy; default 90 days after archiving). Works offline. */
@HiltWorker
class AutoDeleteWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val wiper: DataWiper,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        wiper.deleteExpired()
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "auto-delete"

        /** Safe to call on every launch: an existing schedule is kept. */
        fun schedule(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<AutoDeleteWorker>(1, TimeUnit.DAYS).build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
