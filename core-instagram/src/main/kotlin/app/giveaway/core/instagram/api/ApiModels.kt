package app.giveaway.core.instagram.api

import java.time.Duration
import java.time.Instant

/** Supplies the current access token. Implemented by core-data, which stores it encrypted. */
fun interface TokenProvider {
    suspend fun accessToken(): String?
}

/** Result of an Instagram API call. Errors are values, never exceptions. */
sealed interface IgResult<out T> {
    data class Ok<T>(val value: T) : IgResult<T>

    data class Err(val error: IgError) : IgResult<Nothing>
}

/** Why an Instagram call failed, mapped from Instagram's error codes (spec: Constraints). */
sealed interface IgError {
    /** Error 190, or no token stored: the user must sign in again (S4 banner). */
    data object TokenExpired : IgError

    /** HTTP 429 or Instagram's throttling codes (4, 17, 32, 613): back off and resume later. */
    data class RateLimited(val retryAfter: Duration?) : IgError

    /** Error 100 on a media object: the post was deleted or isn't visible to this account. */
    data object MediaNotFound : IgError

    /** No connection or a timeout. */
    data object Offline : IgError

    data class Server(val httpCode: Int, val apiCode: Int?) : IgError
}

enum class MediaKind { IMAGE, VIDEO, CAROUSEL_ALBUM }

/** A post or Reel on the account (spec: Data read). */
data class IgMedia(
    val id: String,
    val kind: MediaKind,
    val isReel: Boolean,
    val thumbnailUrl: String?,
    val caption: String?,
    val timestamp: Instant,
    val commentsCount: Int,
    val permalink: String?,
)

data class MediaPage(val media: List<IgMedia>, val nextCursor: String?)

/** A top-level comment. Replies are counted, never stored (plan A14). */
data class IgComment(
    val id: String,
    val username: String,
    val text: String,
    val timestamp: Instant,
    /** Replies included with the comment. */
    val replyCount: Int,
    /** Set when the comment has more replies than were included; fetch them with `repliesPage`. */
    val moreRepliesCursor: String?,
)

/** A page of comments. [nextCursor] is null on the last page, or when Instagram stopped paging early. */
data class CommentPage(val comments: List<IgComment>, val nextCursor: String?)

data class ReplyCountPage(val count: Int, val nextCursor: String?)
