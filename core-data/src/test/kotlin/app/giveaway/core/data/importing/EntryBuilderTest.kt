package app.giveaway.core.data.importing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giveaway.core.data.db.BlocklistEntity
import app.giveaway.core.data.db.CommentEntity
import app.giveaway.core.data.db.EntryEntity
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.giveaway.DefaultGiveawayRepository
import app.giveaway.core.data.settings.DefaultSettingsRepository
import app.giveaway.core.instagram.api.IgMedia
import app.giveaway.core.instagram.api.MediaKind
import app.giveaway.draw.ExclusionReason
import app.giveaway.draw.Rules
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.measureTime

/** C-19 acceptance: entries are built from stored comments in order, keep manual choices, and the giveaway moves on. */
@RunWith(RobolectricTestRunner::class)
class EntryBuilderTest {

    private val closesAt = Instant.parse("2026-10-08T10:00:00Z")
    private val clock = Clock.fixed(closesAt.plusSeconds(3_600), ZoneOffset.UTC)
    private lateinit var db: GiveawayDatabase
    private lateinit var giveaways: DefaultGiveawayRepository
    private var id = 0L

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, GiveawayDatabase::class.java).allowMainThreadQueries().build()
        giveaways = DefaultGiveawayRepository(db, DefaultSettingsRepository(db.settingsDao()), clock)
        val media = IgMedia("m1", MediaKind.IMAGE, false, null, null, closesAt.minusSeconds(864_000), 0, null)
        val rules = Rules(
            minMentions = 1,
            requiredHashtag = null,
            keyword = null,
            onePerPerson = true,
            excludePastWinners = true,
            excludeBlocklist = true,
            closesAt = closesAt,
            winnersCount = 1,
            alternatesCount = 1,
        )
        id = giveaways.createDraft(media, "Drop", "shop", rules)
        giveaways.saveCommitment(id, "a".repeat(64), ByteArray(48))
        giveaways.commit(id)
        giveaways.transition(id, GiveawayStatus.IMPORTING)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun comments(vararg rows: Triple<String, String, Long>) = db.commentDao().insertAll(
        rows.map { (commentId, user, minutesBefore) ->
            CommentEntity(id, commentId, user, "Count me in @friend", closesAt.minusSeconds(minutesBefore * 60))
        },
    )

    @Test
    fun buildsEntriesInTimeOrderAndMovesToReview() = runTest {
        comments(
            Triple("c2", "bob", 10),
            Triple("c1", "amy", 30),
            Triple("c3", "amy", 5),
            Triple("c4", "shop", 3),
        )
        db.commentDao().insertAll(listOf(CommentEntity(id, "c5", "late", "@friend", closesAt.plusSeconds(60))))
        EntryBuilder(db).rebuild(id)
        val byId = all()
        assertEquals(true, byId.getValue("c1").isValid)
        assertEquals(ExclusionReason.DUPLICATE, byId.getValue("c3").exclusionReason)
        assertEquals(true, byId.getValue("c2").isValid)
        assertEquals(ExclusionReason.OWN_ACCOUNT, byId.getValue("c4").exclusionReason)
        assertEquals(ExclusionReason.AFTER_DEADLINE, byId.getValue("c5").exclusionReason)
        assertEquals(2, db.entryDao().validCount(id))
        assertEquals(GiveawayStatus.REVIEW, giveaways.get(id)?.status)
    }

    @Test
    fun blocklistAndManualChoicesSurviveARebuild() = runTest {
        comments(Triple("c1", "amy", 30), Triple("c2", "bob", 20), Triple("c3", "cat", 10))
        db.blocklistDao().upsert(BlocklistEntity("cat", closesAt, null))
        EntryBuilder(db).rebuild(id)
        // The organizer excludes amy's comment with a reason; then the rules are applied again.
        val excluded = all().getValue("c1")
            .copy(isValid = false, exclusionReason = ExclusionReason.MANUAL, manualNote = "Fake account")
        db.entryDao().upsertAll(listOf(excluded))
        EntryBuilder(db).rebuild(id)
        val byId = all()
        assertEquals(ExclusionReason.MANUAL, byId.getValue("c1").exclusionReason)
        assertEquals("Fake account", byId.getValue("c1").manualNote)
        assertEquals(ExclusionReason.BLOCKLISTED, byId.getValue("c3").exclusionReason)
        assertTrue(byId.getValue("c2").isValid)
        assertEquals(GiveawayStatus.REVIEW, giveaways.get(id)?.status)
    }

    @Test
    fun fiftyThousandCommentsAreFilteredInOnePass() = runTest {
        db.commentDao().insertAll(
            List(50_000) { i ->
                CommentEntity(id, "c%05d".format(i), "user${i % 40_000}", "@friend", closesAt.minusSeconds(i.toLong()))
            },
        )
        val elapsed = measureTime { EntryBuilder(db).rebuild(id) }
        assertEquals(40_000, db.entryDao().validCount(id))
        // The spec's 3 s budget is for a mid-range phone (plan C-19, instrumented); this guards against regressions.
        assertTrue("took $elapsed", elapsed.inWholeSeconds < 30)
    }

    private suspend fun all(): Map<String, EntryEntity> = db.entryDao().all(id).associateBy { it.commentId }
}
