package app.giveaway.core.data.importing

import app.giveaway.core.data.db.ImportStateEntity

/** The S9 states (spec: S9), from the saved import state and whether the worker is running. */
enum class ImportPhase {
    NOT_STARTED,
    RUNNING,

    /** Instagram asked us to slow down; the worker backs off and resumes. */
    RATE_LIMITED,

    /** No connection; the worker waits for one and resumes. */
    OFFLINE,

    /** A server error or a short import, being retried automatically. */
    RETRYING,

    /** The post was deleted on Instagram; the draw can use what was imported (spec: Edge cases). */
    POST_DELETED,

    /** The Instagram sign-in expired; import resumes after signing in again. */
    SIGNED_OUT,

    /** Paging finished short of the post's count, and each read found comments the last one missed, every retry. */
    MISMATCH,
    COMPLETE,
}

data class ImportProgress(
    val phase: ImportPhase,
    /** Top-level comments stored plus replies counted, compared with [expected] (plan A14). */
    val imported: Int = 0,
    val expected: Int = 0,
    val pages: Int = 0,
    val failedRetries: Int = 0,
    val acceptedPartial: Boolean = false,
) {
    /** After three failed retries, or when the post is gone, the user may continue with what was imported (S9). */
    val canAcceptPartial: Boolean
        get() = !acceptedPartial && phase != ImportPhase.COMPLETE &&
            (failedRetries >= MAX_FAILED_RETRIES || phase == ImportPhase.POST_DELETED)

    /** Import is done: complete, or partial and accepted. */
    val done: Boolean get() = phase == ImportPhase.COMPLETE

    /** Comments in the post's count that Instagram never returned, once a complete import settled short of it. */
    val unavailable: Int
        get() = if (done && !acceptedPartial) (expected - imported).coerceAtLeast(0) else 0

    companion object {
        fun of(state: ImportStateEntity?, workerActive: Boolean): ImportProgress {
            if (state == null) return ImportProgress(if (workerActive) ImportPhase.RUNNING else ImportPhase.NOT_STARTED)
            val phase = when {
                state.acceptedPartial || state.isComplete() -> ImportPhase.COMPLETE
                state.lastError == CommentImporter.CODE_MEDIA_NOT_FOUND -> ImportPhase.POST_DELETED
                state.lastError == CommentImporter.CODE_TOKEN_EXPIRED -> ImportPhase.SIGNED_OUT
                state.lastError == CommentImporter.CODE_OFFLINE -> ImportPhase.OFFLINE
                state.lastError == CommentImporter.CODE_RATE_LIMITED -> ImportPhase.RATE_LIMITED
                state.lastError == CommentImporter.MISMATCH && state.failedRetries >= MAX_FAILED_RETRIES ->
                    ImportPhase.MISMATCH
                state.lastError != null -> ImportPhase.RETRYING
                else -> ImportPhase.RUNNING
            }
            return ImportProgress(
                phase = phase,
                imported = state.commentsFetched + state.repliesCounted,
                expected = state.expectedCount,
                pages = state.pagesFetched,
                failedRetries = state.failedRetries,
                acceptedPartial = state.acceptedPartial,
            )
        }
    }
}
