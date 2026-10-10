package app.giveaway.core.data.importing

import androidx.room.withTransaction
import app.giveaway.core.data.db.CommentEntity
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.db.GiveawayEntity
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.db.ImportStateEntity
import app.giveaway.core.instagram.api.IgComment
import app.giveaway.core.instagram.api.IgError
import app.giveaway.core.instagram.api.IgResult
import app.giveaway.core.instagram.api.InstagramRepository
import java.time.Clock
import javax.inject.Inject

/** How a run of the importer ended. */
sealed interface ImportRun {
    /**
     * Every page was fetched and the totals add up, or reading every page again brought nothing new (the rest of the
     * post's count is comments Instagram doesn't return), or the user accepted less.
     */
    data object Complete : ImportRun

    /**
     * Paging finished short of the post's count, and reading it again found comments the last read missed: Instagram
     * stopped early, so it's retried up to [MAX_FAILED_RETRIES].
     */
    data object Mismatch : ImportRun

    /** Stopped on an Instagram error; the next run resumes from the saved cursor. */
    data class Stopped(val error: IgError) : ImportRun
}

/** Consecutive failed attempts before S9 offers to accept a partial import (spec: S9). */
const val MAX_FAILED_RETRIES = 3

/**
 * Imports a giveaway's top-level comments page by page (plan C-16). Each page and the import state are saved in one
 * transaction, so a run killed at any point resumes from the last saved cursor without duplicates, and memory stays
 * flat however many comments there are. Replies are counted but never stored (plan A14).
 */
class CommentImporter @Inject constructor(
    private val db: GiveawayDatabase,
    private val instagram: InstagramRepository,
    private val clock: Clock,
) {
    private val states get() = db.importStateDao()

    /** Imports until done or stopped. [onProgress] gets (comments and replies so far, the post's count) per page. */
    suspend fun run(
        giveawayId: Long,
        onProgress: suspend (imported: Int, expected: Int) -> Unit = { _, _ -> },
    ): ImportRun {
        val giveaway = checkNotNull(db.giveawayDao().get(giveawayId)) { "No giveaway $giveawayId" }
        if (giveaway.status == GiveawayStatus.COMMITTED) {
            db.giveawayDao().updateStatus(giveawayId, GiveawayStatus.IMPORTING)
        }
        var state = when (val point = resumePoint(giveaway)) {
            is ResumePoint.From -> point.state
            is ResumePoint.Finished -> return point.run
        }
        // Comments stored before this read of every page began, when it began in this run at the first page with
        // comments already stored: a read again, whose result can be compared.
        var storedBeforeRead = state.commentsFetched.takeIf { state.pagesFetched == 0 && it > 0 }
        while (true) {
            val page = when (val result = fetchPage(giveaway.igMediaId, state.nextCursor)) {
                is IgResult.Ok -> result.value
                is IgResult.Err -> return stopped(giveawayId, result.error)
            }
            state = savePage(state, page.comments, page.nextCursor, page.replies)
            onProgress(state.commentsFetched + state.repliesCounted, state.expectedCount)
            if (page.nextCursor != null) continue
            val before = storedBeforeRead
            when {
                state.isComplete() -> return finish(state)
                // A short read: read every page once more straight away, rather than after a retry's wait.
                before == null -> {
                    storedBeforeRead = state.commentsFetched
                    state = restart(state)
                }
                // Nothing new the second time: Instagram has given every comment it will.
                state.commentsFetched == before -> return settle(state)
                else -> return finish(state)
            }
        }
    }

    private sealed interface ResumePoint {
        data class From(val state: ImportStateEntity) : ResumePoint

        data class Finished(val run: ImportRun) : ResumePoint
    }

    /** Where this run starts: the saved cursor, the first page again after a short import, or nowhere. */
    private suspend fun resumePoint(giveaway: GiveawayEntity): ResumePoint {
        val state = states.get(giveaway.id) ?: when (val media = instagram.mediaById(giveaway.igMediaId)) {
            is IgResult.Ok -> start(giveaway, media.value.commentsCount)
            // Nothing saved yet: the next run starts over.
            is IgResult.Err -> return ResumePoint.Finished(ImportRun.Stopped(media.error))
        }
        return when {
            state.acceptedPartial || state.isComplete() -> ResumePoint.Finished(ImportRun.Complete)
            !state.isFinished() -> ResumePoint.From(state)
            state.failedRetries >= MAX_FAILED_RETRIES -> ResumePoint.Finished(ImportRun.Mismatch)
            else -> ResumePoint.From(restart(state))
        }
    }

    /** Whether a short import should be tried again automatically (spec: S9, retry up to three times). */
    suspend fun canRetry(giveawayId: Long): Boolean =
        (states.get(giveawayId)?.failedRetries ?: 0) < MAX_FAILED_RETRIES

    /** First run: the post's comment count is what the import is checked against (spec: Constraints). */
    private suspend fun start(giveaway: GiveawayEntity, expectedCount: Int): ImportStateEntity {
        val state = ImportStateEntity(
            giveawayId = giveaway.id,
            nextCursor = null,
            pagesFetched = 0,
            commentsFetched = 0,
            expectedCount = expectedCount,
            lastError = null,
            updatedAt = clock.instant(),
            failedRetries = 0,
            acceptedPartial = false,
            repliesCounted = 0,
        )
        states.upsert(state)
        return state
    }

    /** A page of top-level comments, with their replies counted. */
    private class FetchedPage(val comments: List<IgComment>, val nextCursor: String?, val replies: Int)

    private suspend fun fetchPage(mediaId: String, cursor: String?): IgResult<FetchedPage> {
        val page = when (val result = instagram.commentsPage(mediaId, cursor)) {
            is IgResult.Ok -> result.value
            is IgResult.Err -> return result
        }
        return when (val counted = countReplies(page.comments)) {
            is IgResult.Ok -> IgResult.Ok(FetchedPage(page.comments, page.nextCursor, counted.value))
            is IgResult.Err -> counted
        }
    }

    private suspend fun countReplies(comments: List<IgComment>): IgResult<Int> {
        var total = 0
        for (comment in comments) {
            total += comment.replyCount
            var cursor = comment.moreRepliesCursor
            while (cursor != null) {
                when (val page = instagram.repliesPage(comment.id, cursor)) {
                    is IgResult.Ok -> {
                        total += page.value.count
                        cursor = page.value.nextCursor
                    }
                    is IgResult.Err -> return page
                }
            }
        }
        return IgResult.Ok(total)
    }

    private suspend fun savePage(
        state: ImportStateEntity,
        comments: List<IgComment>,
        nextCursor: String?,
        replies: Int,
    ): ImportStateEntity = db.withTransaction {
        db.commentDao().insertAll(
            comments.map { CommentEntity(state.giveawayId, it.id, it.username, it.text, it.timestamp) },
        )
        val saved = state.copy(
            nextCursor = nextCursor,
            pagesFetched = state.pagesFetched + 1,
            commentsFetched = db.commentDao().count(state.giveawayId),
            repliesCounted = state.repliesCounted + replies,
            lastError = null,
            updatedAt = clock.instant(),
        )
        states.upsert(saved)
        saved
    }

    private suspend fun finish(state: ImportStateEntity): ImportRun {
        if (state.isComplete()) {
            states.upsert(state.copy(failedRetries = 0))
            return ImportRun.Complete
        }
        states.upsert(state.copy(failedRetries = state.failedRetries + 1, lastError = MISMATCH))
        return ImportRun.Mismatch
    }

    /**
     * Two reads of every page ended with the same comments, short of the post's count. The rest are comments Instagram
     * counts but never returns to apps (hidden, filtered or deleted), so the import is complete; S9 says how many.
     * Only top-level comments are entries, so new replies alone don't count as something the first read missed.
     */
    private suspend fun settle(state: ImportStateEntity): ImportRun {
        states.upsert(state.copy(failedRetries = 0, lastError = SETTLED, updatedAt = clock.instant()))
        return ImportRun.Complete
    }

    /**
     * Paging reached the end with too few comments: page again from the first page. Stored comments are kept and
     * duplicates ignored, so only the missing ones are added.
     */
    private suspend fun restart(state: ImportStateEntity): ImportStateEntity {
        val restarted = state.copy(nextCursor = null, pagesFetched = 0, repliesCounted = 0, updatedAt = clock.instant())
        states.upsert(restarted)
        return restarted
    }

    private suspend fun stopped(giveawayId: Long, error: IgError): ImportRun {
        val state = states.get(giveawayId)
        if (state != null) {
            val failed = state.copy(
                lastError = error.code(),
                failedRetries = state.failedRetries + 1,
                updatedAt = clock.instant(),
            )
            states.upsert(failed)
        }
        return ImportRun.Stopped(error)
    }

    companion object {
        const val MISMATCH = "MISMATCH"

        /** Not an error: the import settled short of the post's count; see [settle]. */
        const val SETTLED = "SETTLED"
        const val CODE_TOKEN_EXPIRED = "TOKEN_EXPIRED"
        const val CODE_MEDIA_NOT_FOUND = "MEDIA_NOT_FOUND"
        const val CODE_OFFLINE = "OFFLINE"
        const val CODE_RATE_LIMITED = "RATE_LIMITED"
        const val CODE_SERVER = "SERVER"

        /** Stored in import_state.lastError: a stable code, never Instagram's message. */
        fun IgError.code(): String = when (this) {
            IgError.TokenExpired -> CODE_TOKEN_EXPIRED
            IgError.MediaNotFound -> CODE_MEDIA_NOT_FOUND
            IgError.Offline -> CODE_OFFLINE
            is IgError.RateLimited -> CODE_RATE_LIMITED
            is IgError.Server -> CODE_SERVER
        }
    }
}

/** Paging reached the last page at least once. */
fun ImportStateEntity.isFinished(): Boolean = pagesFetched > 0 && nextCursor == null

/**
 * Everything the post reports is accounted for: top-level comments stored plus replies counted (plan A14). Or a second
 * read of every page brought nothing new, so the rest is comments Instagram doesn't return.
 */
fun ImportStateEntity.isComplete(): Boolean =
    isFinished() && (commentsFetched + repliesCounted >= expectedCount || lastError == CommentImporter.SETTLED)
