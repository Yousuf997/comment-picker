package app.giveaway.core.data.work

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.giveaway.DefaultGiveawayRepository
import app.giveaway.core.data.settings.DefaultSettingsRepository
import app.giveaway.core.instagram.api.CommentPage
import app.giveaway.core.instagram.api.IgError
import app.giveaway.core.instagram.api.IgMedia
import app.giveaway.core.instagram.api.IgResult
import app.giveaway.core.instagram.api.InstagramRepository
import app.giveaway.core.instagram.api.MediaKind
import app.giveaway.core.instagram.api.MediaPage
import app.giveaway.core.instagram.api.ReplyCountPage
import app.giveaway.draw.Rules
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** C-15 acceptance: at the deadline, a caption with the hash sets captionVerifiedAt; without it, it stays null. */
@RunWith(RobolectricTestRunner::class)
class DeadlineWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = Instant.parse("2026-10-08T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val hash = "a".repeat(64)
    private lateinit var db: GiveawayDatabase
    private lateinit var repository: DefaultGiveawayRepository

    private var caption: IgResult<IgMedia> = IgResult.Err(IgError.Offline)
    private val notified = mutableListOf<Long>()

    private val media = IgMedia(
        id = "m1",
        kind = MediaKind.IMAGE,
        isReel = false,
        thumbnailUrl = null,
        caption = null,
        timestamp = now.minus(Duration.ofDays(10)),
        commentsCount = 50,
        permalink = null,
    )

    private val instagram = object : InstagramRepository {
        override suspend fun mediaPage(cursor: String?, limit: Int): IgResult<MediaPage> = error("unused")
        override suspend fun mediaById(mediaId: String) = caption
        override suspend fun commentsPage(mediaId: String, cursor: String?, limit: Int): IgResult<CommentPage> =
            error("unused")
        override suspend fun repliesPage(commentId: String, cursor: String?): IgResult<ReplyCountPage> =
            error("unused")
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, GiveawayDatabase::class.java).allowMainThreadQueries().build()
        repository = DefaultGiveawayRepository(db, DefaultSettingsRepository(db.settingsDao()), clock)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun committedGiveaway(): Long {
        val rules = Rules(1, null, null, true, true, true, now, 1, 2)
        val id = repository.createDraft(media, "Summer drop", "shop", rules)
        repository.saveCommitment(id, hash, ByteArray(48))
        repository.commit(id)
        return id
    }

    private suspend fun run(id: Long, attempt: Int = 0): ListenableWorker.Result =
        TestListenableWorkerBuilder<DeadlineWorker>(context)
            .setInputData(workDataOf(DeadlineWorker.KEY_GIVEAWAY_ID to id))
            .setRunAttemptCount(attempt)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(appContext: Context, workerClassName: String, params: WorkerParameters) =
                        DeadlineWorker(appContext, params, repository, instagram, { notified += it }, clock)
                },
            )
            .build()
            .doWork()

    private fun mediaWithCaption(text: String?) = IgResult.Ok(media.copy(caption = text))

    @Test
    fun aCaptionWithTheCodeIsRecordedAsVerified() = runTest {
        val id = committedGiveaway()
        caption = mediaWithCaption("Win a tote bag! Tag two friends.\n\n#draw ${hash.uppercase()}")
        assertEquals(ListenableWorker.Result.success(), run(id))
        assertEquals(now, repository.commitment(id)?.captionVerifiedAt)
        assertEquals(listOf(id), notified)
    }

    @Test
    fun aCaptionWithoutTheCodeLeavesItUnverified() = runTest {
        val id = committedGiveaway()
        caption = mediaWithCaption("Win a tote bag! Tag two friends.")
        assertEquals(ListenableWorker.Result.success(), run(id))
        assertNull(repository.commitment(id)?.captionVerifiedAt)
        assertEquals(listOf(id), notified)
    }

    @Test
    fun beingOfflineRetriesThenGivesUpAndStillReminds() = runTest {
        val id = committedGiveaway()
        caption = IgResult.Err(IgError.Offline)
        assertEquals(ListenableWorker.Result.retry(), run(id))
        assertEquals(emptyList<Long>(), notified)
        assertEquals(ListenableWorker.Result.success(), run(id, attempt = 5))
        assertNull(repository.commitment(id)?.captionVerifiedAt)
        assertEquals(listOf(id), notified)
    }

    @Test
    fun aDeletedPostIsNotRetried() = runTest {
        val id = committedGiveaway()
        caption = IgResult.Err(IgError.MediaNotFound)
        assertEquals(ListenableWorker.Result.success(), run(id))
        assertEquals(listOf(id), notified)
    }

    @Test
    fun aGiveawayThatIsGoneOrNotCommittedIsIgnored() = runTest {
        assertEquals(ListenableWorker.Result.success(), run(999))
        val draft = repository.createDraft(media, "Draft", "shop", Rules(1, null, null, true, true, true, now, 1, 2))
        assertEquals(ListenableWorker.Result.success(), run(draft))
        assertEquals(emptyList<Long>(), notified)
        assertNotNull(repository.get(draft))
    }
}
