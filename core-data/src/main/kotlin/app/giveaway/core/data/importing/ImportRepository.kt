package app.giveaway.core.data.importing

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.giveaway.core.data.db.GiveawayDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject

/** Starts the import worker and reports whether it is queued or running. Faked in UI tests. */
interface ImportWork {
    /** Starts the import, or leaves one already queued or running alone (it resumes from the saved cursor). */
    fun start(giveawayId: Long)

    fun observeActive(giveawayId: Long): Flow<Boolean>

    /** Stops a queued or running import, before its giveaway's comments go away (new post, deletion). */
    fun cancel(giveawayId: Long)
}

internal class WorkManagerImportWork @Inject constructor(
    @ApplicationContext private val context: Context,
) : ImportWork {
    private val workManager get() = WorkManager.getInstance(context)

    override fun start(giveawayId: Long) = ImportWorker.enqueue(workManager, giveawayId)

    override fun cancel(giveawayId: Long) {
        workManager.cancelUniqueWork(ImportWorker.workName(giveawayId))
    }

    override fun observeActive(giveawayId: Long): Flow<Boolean> =
        workManager.getWorkInfosForUniqueWorkFlow(ImportWorker.workName(giveawayId))
            .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }
}

/** S9's view of a giveaway's import (plan C-16, C-17). */
class ImportRepository @Inject constructor(
    private val db: GiveawayDatabase,
    private val work: ImportWork,
    private val clock: Clock,
) {
    fun observe(giveawayId: Long): Flow<ImportProgress> =
        combine(db.importStateDao().observe(giveawayId), work.observeActive(giveawayId)) { state, active ->
            ImportProgress.of(state, active)
        }.distinctUntilChanged()

    fun start(giveawayId: Long) = work.start(giveawayId)

    /**
     * "Retry now" on S9: a fresh run straight away, instead of waiting out a retry's back-off (a queued run would be
     * kept), with the automatic retries counted from zero again so a stopped import runs once more.
     */
    suspend fun retry(giveawayId: Long) {
        db.importStateDao().get(giveawayId)?.let { state ->
            db.importStateDao().upsert(state.copy(failedRetries = 0, updatedAt = clock.instant()))
        }
        work.cancel(giveawayId)
        work.start(giveawayId)
    }

    /**
     * The user continues with the comments imported so far, after three failed retries or a deleted post (spec: S9).
     * The certificate states it (plan A16).
     */
    suspend fun acceptPartial(giveawayId: Long) {
        val state = checkNotNull(db.importStateDao().get(giveawayId)) { "No import for giveaway $giveawayId" }
        check(ImportProgress.of(state, workerActive = false).canAcceptPartial) { "A partial import isn't allowed yet" }
        db.importStateDao().upsert(state.copy(acceptedPartial = true, updatedAt = clock.instant()))
        // The worker finishes the job: it sees the accepted import as complete and moves on to filtering.
        work.start(giveawayId)
    }
}
