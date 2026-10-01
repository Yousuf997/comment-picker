package app.giveaway.core.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.giveaway.core.data.account.TokenCheck
import app.giveaway.core.data.account.TokenLifecycle
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Daily token check (plan C-01). Retries with backoff when offline or Instagram is unavailable. */
@HiltWorker
class TokenRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val lifecycle: TokenLifecycle,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        if (lifecycle.refreshIfNeeded() == TokenCheck.RETRY_LATER) Result.retry() else Result.success()

    companion object {
        private const val WORK_NAME = "token-refresh"

        /** Safe to call on every launch: an existing schedule is kept. */
        fun schedule(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<TokenRefreshWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
