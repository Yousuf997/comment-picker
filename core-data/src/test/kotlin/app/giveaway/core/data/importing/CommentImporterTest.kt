package app.giveaway.core.data.importing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.giveaway.DefaultGiveawayRepository
import app.giveaway.core.data.settings.DefaultSettingsRepository
import app.giveaway.core.instagram.api.CommentPage
import app.giveaway.core.instagram.api.IgComment
import app.giveaway.core.instagram.api.IgError
import app.giveaway.core.instagram.api.IgMedia
import app.giveaway.core.instagram.api.IgResult
import app.giveaway.core.instagram.api.InstagramRepository
import app.giveaway.core.instagram.api.MediaKind
import app.giveaway.core.instagram.api.MediaPage
import app.giveaway.core.instagram.api.ReplyCountPage
import app.giveaway.draw.Rules
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** C-16/C-17 acceptance: resumable import, early stops, rate limits, a killed run, replies and completeness. */
@RunWith(RobolectricTestRunner::class)
class CommentImporterTest {

    private val now = Instant.parse("2026-10-08T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private lateinit var db: GiveawayDatabase
    private lateinit var giveaways: DefaultGiveawayRepository
    private lateinit var importer: CommentImporter
    private val instagram = FakeComments()
    private var giveawayId = 0L

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, GiveawayDatabase::class.java).allowMainThreadQueries().build()
        giveaways = DefaultGiveawayRepository(db, DefaultSettingsRepository(db.settingsDao()), clock)
        importer = CommentImporter(db, instagram, clock)
        val media = IgMedia("m1", MediaKind.IMAGE, false, null, null, now.minusSeconds(864_000), 0, null)
        giveawayId = giveaways.createDraft(media, "Drop", "shop", Rules(1, null, null, true, true, true, now, 1, 2))
        giveaways.saveCommitment(giveawayId, "a".repeat(64), ByteArray(48))
        giveaways.commit(giveawayId)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun stored() = db.commentDao().count(giveawayId)

    private suspend fun progress() = ImportProgress.of(db.importStateDao().get(giveawayId), workerActive = false)

    @Test
    fun importsEveryPageAndCountsReplies() = runTest {
        instagram.pages = pages(50, 50, 20)
        instagram.replies = mapOf("c0-0" to 3)
        instagram.moreReplies = mapOf("c1-0" to listOf(4, 2))
        instagram.expected = 120 + 3 + 6
        assertEquals(ImportRun.Complete, importer.run(giveawayId))
        assertEquals(120, stored())
        assertEquals(ImportPhase.COMPLETE, progress().phase)
        assertEquals(129, progress().imported)
        assertEquals(GiveawayStatus.IMPORTING, giveaways.get(giveawayId)?.status)
    }

    @Test
    fun anErrorStopsAndTheNextRunResumesFromTheSavedCursor() = runTest {
        instagram.pages = pages(50, 50, 50)
        instagram.expected = 150
        instagram.failAt = mapOf("p2" to IgError.Offline)
        assertEquals(ImportRun.Stopped(IgError.Offline), importer.run(giveawayId))
        assertEquals(100, stored())
        assertEquals(ImportPhase.OFFLINE, progress().phase)
        instagram.failAt = emptyMap()
        instagram.requested.clear()
        assertEquals(ImportRun.Complete, importer.run(giveawayId))
        assertEquals(listOf<String?>("p2"), instagram.requested)
        assertEquals(150, stored())
        assertEquals(0, progress().failedRetries)
    }

    @Test
    fun aRunKilledMidPageResumesWithoutDuplicates() = runTest {
        instagram.pages = pages(50, 50, 50)
        instagram.expected = 150
        instagram.crashAt = "p1"
        assertTrue(runCatching { importer.run(giveawayId) }.isFailure)
        assertEquals(50, stored())
        instagram.crashAt = null
        assertEquals(ImportRun.Complete, importer.run(giveawayId))
        assertEquals(150, stored())
    }

    @Test
    fun rateLimitsAreRecordedForS9() = runTest {
        instagram.pages = pages(50, 50)
        instagram.expected = 100
        instagram.failAt = mapOf("p1" to IgError.RateLimited(null))
        assertEquals(ImportRun.Stopped(IgError.RateLimited(null)), importer.run(giveawayId))
        assertEquals(ImportPhase.RATE_LIMITED, progress().phase)
        assertEquals(1, progress().failedRetries)
    }

    @Test
    fun anEarlyStopIsRetriedThreeTimesThenThePartialImportCanBeAccepted() = runTest {
        // Instagram reports 150 comments but paging ends after 100.
        instagram.pages = pages(50, 50)
        instagram.expected = 150
        repeat(MAX_FAILED_RETRIES) { attempt ->
            assertEquals(ImportRun.Mismatch, importer.run(giveawayId))
            assertEquals(attempt + 1, progress().failedRetries)
        }
        assertEquals("paging restarted from the first page each time", 3, instagram.requested.count { it == null })
        assertEquals(ImportRun.Mismatch, importer.run(giveawayId))
        assertEquals(ImportPhase.MISMATCH, progress().phase)
        assertTrue(progress().canAcceptPartial)
        val repository = ImportRepository(db, NoWork, clock)
        repository.acceptPartial(giveawayId)
        assertEquals(ImportPhase.COMPLETE, progress().phase)
        assertEquals(ImportRun.Complete, importer.run(giveawayId))
        assertEquals(100, stored())
    }

    @Test
    fun aRetryPicksUpMissingComments() = runTest {
        instagram.pages = pages(50, 50)
        instagram.expected = 150
        assertEquals(ImportRun.Mismatch, importer.run(giveawayId))
        instagram.pages = pages(50, 50, 50)
        assertEquals(ImportRun.Complete, importer.run(giveawayId))
        assertEquals(150, stored())
    }

    @Test
    fun aDeletedPostAllowsDrawingFromWhatWasImported() = runTest {
        instagram.pages = pages(50, 50)
        instagram.expected = 100
        instagram.failAt = mapOf("p1" to IgError.MediaNotFound)
        importer.run(giveawayId)
        assertEquals(ImportPhase.POST_DELETED, progress().phase)
        assertTrue(progress().canAcceptPartial)
        assertFalse(progress().done)
    }

    @Test
    fun fiftyThousandCommentsArriveAPageAtATime() = runTest {
        instagram.pages = pages(*IntArray(1_000) { 50 })
        instagram.expected = 50_000
        assertEquals(ImportRun.Complete, importer.run(giveawayId))
        assertEquals(50_000, stored())
        assertEquals(1_000, progress().pages)
    }

    private object NoWork : ImportWork {
        override fun start(giveawayId: Long) = Unit
        override fun observeActive(giveawayId: Long) = flowOf(false)
        override fun cancel(giveawayId: Long) = Unit
    }

    /** Page i is served at cursor "p<i>" (the first at null); comment ids are "c<page>-<index>". */
    private fun pages(vararg sizes: Int): List<Int> = sizes.toList()

    private inner class FakeComments : InstagramRepository {
        var pages: List<Int> = emptyList()
        var expected = 0
        var replies: Map<String, Int> = emptyMap()
        var moreReplies: Map<String, List<Int>> = emptyMap()
        var failAt: Map<String?, IgError> = emptyMap()
        var crashAt: String? = null
        val requested = mutableListOf<String?>()

        override suspend fun mediaById(mediaId: String): IgResult<IgMedia> =
            IgResult.Ok(IgMedia(mediaId, MediaKind.IMAGE, false, null, null, now, expected, null))

        override suspend fun commentsPage(mediaId: String, cursor: String?, limit: Int): IgResult<CommentPage> {
            requested += cursor
            failAt[cursor]?.let { return IgResult.Err(it) }
            check(crashAt == null || cursor != crashAt) { "process killed" }
            val index = cursor?.removePrefix("p")?.toInt() ?: 0
            val comments = List(pages[index]) { i ->
                val id = "c$index-$i"
                IgComment(
                    id = id,
                    username = "user$index$i",
                    text = "@a @b",
                    timestamp = now.minusSeconds(60),
                    replyCount = replies[id] ?: moreReplies[id]?.first() ?: 0,
                    moreRepliesCursor = moreReplies[id]?.let { "$id-r1" },
                )
            }
            val next = if (index + 1 < pages.size) "p${index + 1}" else null
            return IgResult.Ok(CommentPage(comments, next))
        }

        override suspend fun repliesPage(commentId: String, cursor: String?): IgResult<ReplyCountPage> {
            val counts = moreReplies.getValue(commentId)
            val page = cursor!!.substringAfterLast("-r").toInt()
            val next = if (page + 1 < counts.size) "$commentId-r${page + 1}" else null
            return IgResult.Ok(ReplyCountPage(counts[page], next))
        }

        override suspend fun mediaPage(cursor: String?, limit: Int): IgResult<MediaPage> = error("unused")
    }
}
