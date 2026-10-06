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
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.giveaway.DefaultGiveawayRepository
import app.giveaway.core.data.settings.DefaultSettingsRepository
import app.giveaway.core.instagram.api.IgMedia
import app.giveaway.core.instagram.api.MediaKind
import app.giveaway.draw.Rules
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** C-15 and plan A35: when entries close, a waiting giveaway gets its reminder; nothing on Instagram is read. */
@RunWith(RobolectricTestRunner::class)
class DeadlineWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = Instant.parse("2026-10-08T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val rules = Rules(1, null, null, true, true, true, now, 1, 2)
    private lateinit var db: GiveawayDatabase
    private lateinit var repository: DefaultGiveawayRepository
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

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, GiveawayDatabase::class.java).allowMainThreadQueries().build()
        repository = DefaultGiveawayRepository(db, DefaultSettingsRepository(db.settingsDao()), clock)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun committedGiveaway(): Long {
        val id = repository.createDraft(media, "Summer drop", "shop", rules)
        repository.saveCommitment(id, "a".repeat(64), ByteArray(48))
        repository.commit(id)
        return id
    }

    private suspend fun run(id: Long): ListenableWorker.Result =
        TestListenableWorkerBuilder<DeadlineWorker>(context)
            .setInputData(workDataOf(DeadlineWorker.KEY_GIVEAWAY_ID to id))
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(appContext: Context, workerClassName: String, params: WorkerParameters) =
                        DeadlineWorker(appContext, params, repository) { notified += it }
                },
            )
            .build()
            .doWork()

    @Test
    fun aWaitingGiveawayIsRemindedWhenEntriesClose() = runTest {
        val id = committedGiveaway()
        assertEquals(ListenableWorker.Result.success(), run(id))
        assertEquals(listOf(id), notified)
    }

    @Test
    fun aGiveawayThatIsGoneOrPastThisStepIsIgnored() = runTest {
        assertEquals(ListenableWorker.Result.success(), run(999))
        val draft = repository.createDraft(media, "Draft", "shop", rules)
        assertEquals(ListenableWorker.Result.success(), run(draft))
        val importing = committedGiveaway()
        repository.transition(importing, GiveawayStatus.IMPORTING)
        assertEquals(ListenableWorker.Result.success(), run(importing))
        assertEquals(emptyList<Long>(), notified)
    }
}
