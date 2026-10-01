package app.giveaway.feature.create

import app.giveaway.core.instagram.api.CommentPage
import app.giveaway.core.instagram.api.IgMedia
import app.giveaway.core.instagram.api.IgResult
import app.giveaway.core.instagram.api.InstagramRepository
import app.giveaway.core.instagram.api.MediaKind
import app.giveaway.core.instagram.api.MediaPage
import app.giveaway.core.instagram.api.ReplyCountPage
import java.time.LocalDate
import java.time.ZoneOffset

/** Serves media pages keyed by cursor (null is the first page) and records each request. */
internal class FakeInstagram(private val pages: Map<String?, IgResult<MediaPage>>) : InstagramRepository {
    val requestedCursors = mutableListOf<String?>()

    /** Replaces the answer for a cursor, for example to recover after an error. */
    val overrides = mutableMapOf<String?, IgResult<MediaPage>>()

    override suspend fun mediaPage(cursor: String?, limit: Int): IgResult<MediaPage> {
        requestedCursors += cursor
        return overrides[cursor] ?: pages.getValue(cursor)
    }

    override suspend fun mediaById(mediaId: String): IgResult<IgMedia> = error("not used on S6")

    override suspend fun commentsPage(mediaId: String, cursor: String?, limit: Int): IgResult<CommentPage> =
        error("not used on S6")

    override suspend fun repliesPage(commentId: String, cursor: String?): IgResult<ReplyCountPage> =
        error("not used on S6")
}

internal fun media(id: String, reel: Boolean = false, comments: Int = 10, day: Int = 20) = IgMedia(
    id = id,
    kind = if (reel) MediaKind.VIDEO else MediaKind.IMAGE,
    isReel = reel,
    thumbnailUrl = null,
    caption = "Caption $id",
    timestamp = LocalDate.of(2026, 9, day).atTime(12, 0).toInstant(ZoneOffset.UTC),
    commentsCount = comments,
    permalink = null,
)

internal fun page(vararg media: IgMedia, next: String? = null): IgResult<MediaPage> =
    IgResult.Ok(MediaPage(media.toList(), next))
