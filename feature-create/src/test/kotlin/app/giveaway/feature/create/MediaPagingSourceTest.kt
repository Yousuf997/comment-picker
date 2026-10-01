package app.giveaway.feature.create

import androidx.paging.PagingConfig
import androidx.paging.PagingSource.LoadResult
import androidx.paging.testing.TestPager
import app.giveaway.core.instagram.api.IgError
import app.giveaway.core.instagram.api.IgResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPagingSourceTest {

    private val config = PagingConfig(pageSize = 24)

    private suspend fun refresh(instagram: FakeInstagram, filter: MediaFilter) =
        TestPager(config, MediaPagingSource(instagram, filter)).refresh()

    @Test
    fun filtersPostsAndReelsOnTheDevice() = runTest {
        val instagram = FakeInstagram(mapOf(null to page(media("p1"), media("r1", reel = true), media("p2"))))
        val all = refresh(instagram, MediaFilter.ALL) as LoadResult.Page
        val posts = refresh(instagram, MediaFilter.POSTS) as LoadResult.Page
        val reels = refresh(instagram, MediaFilter.REELS) as LoadResult.Page
        assertEquals(listOf("p1", "r1", "p2"), all.data.map { it.id })
        assertEquals(listOf("p1", "p2"), posts.data.map { it.id })
        assertEquals(listOf("r1"), reels.data.map { it.id })
    }

    @Test
    fun skipsPagesTheFilterEmpties() = runTest {
        val instagram = FakeInstagram(
            mapOf(
                null to page(media("p1"), next = "c1"),
                "c1" to page(media("p2"), next = "c2"),
                "c2" to page(media("r1", reel = true), next = "c3"),
            ),
        )
        val result = refresh(instagram, MediaFilter.REELS) as LoadResult.Page
        assertEquals(listOf("r1"), result.data.map { it.id })
        assertEquals("c3", result.nextKey)
        assertEquals(listOf(null, "c1", "c2"), instagram.requestedCursors)
    }

    @Test
    fun givesUpSkippingAfterTheLimitButKeepsTheCursor() = runTest {
        val pages = (0 until MediaPagingSource.MAX_SKIPPED_PAGES + 1).associate { i ->
            (if (i == 0) null else "c$i") to page(media("p$i"), next = "c${i + 1}")
        }
        val instagram = FakeInstagram(pages)
        val result = refresh(instagram, MediaFilter.REELS) as LoadResult.Page
        assertTrue(result.data.isEmpty())
        assertEquals("c${MediaPagingSource.MAX_SKIPPED_PAGES}", result.nextKey)
        assertEquals(MediaPagingSource.MAX_SKIPPED_PAGES, instagram.requestedCursors.size)
    }

    @Test
    fun aRepeatedCursorEndsPaging() = runTest {
        val instagram = FakeInstagram(
            mapOf(null to page(media("p1"), next = "c1"), "c1" to page(media("p2"), next = "c1")),
        )
        val pager = TestPager(config, MediaPagingSource(instagram, MediaFilter.ALL))
        pager.refresh()
        val second = pager.append() as LoadResult.Page
        assertEquals(listOf("p2"), second.data.map { it.id })
        assertNull(second.nextKey)
    }

    @Test
    fun instagramErrorsBecomeLoadErrors() = runTest {
        val instagram = FakeInstagram(mapOf(null to IgResult.Err(IgError.Offline)))
        val result = refresh(instagram, MediaFilter.ALL) as LoadResult.Error
        assertEquals(IgError.Offline, (result.throwable as MediaLoadException).error)
    }
}
