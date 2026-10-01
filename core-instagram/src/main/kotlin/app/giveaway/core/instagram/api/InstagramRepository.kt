package app.giveaway.core.instagram.api

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject

/** Every Instagram data call in one place (spec: Modules, core-instagram). */
interface InstagramRepository {
    /** One page of the account's posts and Reels, newest first. */
    suspend fun mediaPage(cursor: String?, limit: Int = MEDIA_PAGE_SIZE): IgResult<MediaPage>

    /** A post by ID, for re-reading the caption before the draw and the comment count before import. */
    suspend fun mediaById(mediaId: String): IgResult<IgMedia>

    /** One page of top-level comments (about 50 per page; spec: Constraints). */
    suspend fun commentsPage(mediaId: String, cursor: String?, limit: Int = COMMENTS_PAGE_SIZE): IgResult<CommentPage>

    /** Counts a comment's further replies, page by page, for the completeness check (plan A14). */
    suspend fun repliesPage(commentId: String, cursor: String?): IgResult<ReplyCountPage>

    companion object {
        const val MEDIA_PAGE_SIZE = 24
        const val COMMENTS_PAGE_SIZE = 50
    }
}

internal class DefaultInstagramRepository @Inject constructor(
    private val service: GraphService,
    private val tokens: TokenProvider,
) : InstagramRepository {

    override suspend fun mediaPage(cursor: String?, limit: Int): IgResult<MediaPage> = call(
        { token -> service.media(MEDIA_FIELDS, limit, cursor, token) },
        { page -> MediaPage(page.data.map { it.toModel() }, page.nextCursor) },
    )

    override suspend fun mediaById(mediaId: String): IgResult<IgMedia> = call(
        { token -> service.mediaById(mediaId, MEDIA_FIELDS, token) },
        { it.toModel() },
        mediaRequest = true,
    )

    override suspend fun commentsPage(mediaId: String, cursor: String?, limit: Int): IgResult<CommentPage> = call(
        { token -> service.comments(mediaId, COMMENT_FIELDS, limit, cursor, token) },
        { page -> CommentPage(page.data.map { it.toModel() }, page.nextCursor) },
        mediaRequest = true,
    )

    override suspend fun repliesPage(commentId: String, cursor: String?): IgResult<ReplyCountPage> = call(
        { token -> service.replies(commentId, "id", InstagramRepository.COMMENTS_PAGE_SIZE, cursor, token) },
        { page -> ReplyCountPage(page.data.size, page.nextCursor) },
    )

    private suspend fun <D, T> call(
        request: suspend (token: String) -> Response<D>,
        map: (D) -> T,
        mediaRequest: Boolean = false,
    ): IgResult<T> {
        val token = tokens.accessToken() ?: return IgResult.Err(IgError.TokenExpired)
        return try {
            val response = request(token)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                IgResult.Ok(map(body))
            } else {
                IgResult.Err(errorOf(response, mediaRequest))
            }
        } catch (expected: IOException) {
            IgResult.Err(IgError.Offline)
        } catch (expected: SerializationException) {
            IgResult.Err(IgError.Server(httpCode = 0, apiCode = null))
        } catch (expected: DateTimeParseException) {
            IgResult.Err(IgError.Server(httpCode = 0, apiCode = null))
        }
    }

    private fun errorOf(response: Response<*>, mediaRequest: Boolean): IgError {
        val api = runCatching {
            json.decodeFromString(ErrorEnvelopeDto.serializer(), response.errorBody()?.string().orEmpty()).error
        }.getOrNull()
        val code = api?.code
        return when {
            code == CODE_INVALID_TOKEN || response.code() == HTTP_UNAUTHORIZED -> IgError.TokenExpired
            response.code() == HTTP_TOO_MANY_REQUESTS || code in THROTTLING_CODES ->
                IgError.RateLimited(response.headers()["Retry-After"]?.toLongOrNull()?.let(Duration::ofSeconds))
            mediaRequest && code == CODE_NONEXISTENT_OBJECT -> IgError.MediaNotFound
            else -> IgError.Server(response.code(), code)
        }
    }

    private fun MediaDto.toModel() = IgMedia(
        id = id,
        kind = when (mediaType) {
            "VIDEO" -> MediaKind.VIDEO
            "CAROUSEL_ALBUM" -> MediaKind.CAROUSEL_ALBUM
            else -> MediaKind.IMAGE
        },
        isReel = productType == "REELS",
        // Videos and Reels have a separate thumbnail; images use the image itself.
        thumbnailUrl = thumbnailUrl ?: mediaUrl,
        caption = caption,
        timestamp = parseTimestamp(timestamp),
        commentsCount = commentsCount,
        permalink = permalink,
    )

    private fun CommentDto.toModel() = IgComment(
        id = id,
        username = username.orEmpty(),
        text = text.orEmpty(),
        timestamp = parseTimestamp(timestamp),
        replyCount = replies?.data?.size ?: 0,
        moreRepliesCursor = replies?.nextCursor,
    )

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        const val MEDIA_FIELDS =
            "id,media_type,media_product_type,media_url,thumbnail_url,caption,timestamp,comments_count,permalink"
        const val COMMENT_FIELDS = "id,username,text,timestamp,replies{id}"

        const val CODE_INVALID_TOKEN = 190
        const val CODE_NONEXISTENT_OBJECT = 100
        val THROTTLING_CODES = setOf(4, 17, 32, 613)
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_TOO_MANY_REQUESTS = 429

        /** Instagram sends `2026-09-30T12:34:56+0000`, which Instant.parse rejects. */
        private val instagramTime = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")

        fun parseTimestamp(value: String?): Instant {
            if (value == null) return Instant.EPOCH
            return try {
                OffsetDateTime.parse(value, instagramTime).toInstant()
            } catch (expected: DateTimeParseException) {
                Instant.parse(value)
            }
        }
    }
}
