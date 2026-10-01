package app.giveaway.feature.create

import androidx.paging.PagingSource
import androidx.paging.PagingState
import app.giveaway.core.instagram.api.IgError
import app.giveaway.core.instagram.api.IgMedia
import app.giveaway.core.instagram.api.IgResult
import app.giveaway.core.instagram.api.InstagramRepository

/** The S6 segmented control. Instagram has no type filter, so the account's media is filtered on the device. */
enum class MediaFilter {
    ALL,
    POSTS,
    REELS,
    ;

    fun matches(media: IgMedia): Boolean = when (this) {
        ALL -> true
        POSTS -> !media.isReel
        REELS -> media.isReel
    }
}

/** A failed page, carrying Instagram's error for the S6 error state. */
class MediaLoadException(val error: IgError) : Exception("Instagram media page failed: $error")

/**
 * Pages through the account's posts and Reels, newest first, keyed by Instagram's cursor. A page that the filter
 * empties is skipped (up to [MAX_SKIPPED_PAGES] in a row), so an account with few Reels still fills the grid.
 */
class MediaPagingSource(
    private val instagram: InstagramRepository,
    private val filter: MediaFilter,
) : PagingSource<String, IgMedia>() {

    override suspend fun load(params: LoadParams<String>): LoadResult<String, IgMedia> {
        var cursor = params.key
        repeat(MAX_SKIPPED_PAGES) {
            when (val result = instagram.mediaPage(cursor, params.loadSize)) {
                is IgResult.Err -> return LoadResult.Error(MediaLoadException(result.error))
                is IgResult.Ok -> {
                    // A repeated cursor would loop forever; treat it as the last page.
                    val next = result.value.nextCursor?.takeIf { it != cursor }
                    val matching = result.value.media.filter(filter::matches)
                    if (matching.isNotEmpty() || next == null) {
                        return LoadResult.Page(matching, prevKey = null, nextKey = next)
                    }
                    cursor = next
                }
            }
        }
        return LoadResult.Page(emptyList(), prevKey = null, nextKey = cursor)
    }

    // Instagram cursors only go forward, so a refresh starts again from the newest post.
    override fun getRefreshKey(state: PagingState<String, IgMedia>): String? = null

    companion object {
        const val MAX_SKIPPED_PAGES = 5
    }
}
