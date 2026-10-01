package app.giveaway.core.data.review

import android.content.Context
import androidx.paging.testing.asSnapshot
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giveaway.core.data.db.CommentEntity
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.giveaway.DefaultGiveawayRepository
import app.giveaway.core.data.importing.EntryBuilder
import app.giveaway.core.data.settings.DefaultSettingsRepository
import app.giveaway.core.instagram.api.IgMedia
import app.giveaway.core.instagram.api.MediaKind
import app.giveaway.draw.ExclusionReason
import app.giveaway.draw.Rules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** C-20: S10's manual changes, counts, search and the exported canonical list. */
@RunWith(RobolectricTestRunner::class)
class EntryRepositoryTest {

    private val closesAt = Instant.parse("2026-10-08T10:00:00Z")
    private val clock = Clock.fixed(closesAt.plusSeconds(3_600), ZoneOffset.UTC)
    private lateinit var db: GiveawayDatabase
    private lateinit var giveaways: DefaultGiveawayRepository
    private lateinit var repository: EntryRepository
    private var id = 0L

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, GiveawayDatabase::class.java).allowMainThreadQueries().build()
        giveaways = DefaultGiveawayRepository(db, DefaultSettingsRepository(db.settingsDao()), clock)
        repository = EntryRepository(db, EntryBuilder(db), clock)
        val media = IgMedia("m1", MediaKind.IMAGE, false, null, null, closesAt.minusSeconds(864_000), 0, null)
        id = giveaways.createDraft(media, "Drop", "shop", Rules(1, null, null, true, true, true, closesAt, 1, 1))
        giveaways.saveCommitment(id, "a".repeat(64), ByteArray(48))
        giveaways.commit(id)
        giveaways.transition(id, GiveawayStatus.IMPORTING)
        db.commentDao().insertAll(
            listOf(
                comment("c1", "Zoe", "Count me in @amy", 50),
                comment("c2", "bob", "Love this", 40),
                comment("c3", "Zoe", "Again @amy", 30),
                comment("c4", "cat_99", "100% in @bob", 20),
            ),
        )
        EntryBuilder(db).rebuild(id)
    }

    @After
    fun tearDown() = db.close()

    private fun comment(commentId: String, user: String, text: String, minutesBefore: Long) =
        CommentEntity(id, commentId, user, text, closesAt.minusSeconds(minutesBefore * 60))

    private suspend fun ids(filter: EntryListFilter, query: String = "") =
        repository.entries(id, filter, query).asSnapshot().map { it.commentId }

    private suspend fun entry(commentId: String) = db.entryDao().get(id, commentId)!!

    @Test
    fun countsMatchTheTiles() = runTest {
        val counts = repository.counts(id).first()
        assertEquals(4, counts.total)
        assertEquals(2, counts.valid)
        assertEquals(2, counts.excluded)
    }

    @Test
    fun excludingNeedsAReasonAndLetsTheNextCommentCount() = runTest {
        assertTrue(runCatching { repository.exclude(id, "c1", " ") }.isFailure)
        repository.exclude(id, "c1", "Fake account")
        assertEquals(ExclusionReason.MANUAL, entry("c1").exclusionReason)
        assertEquals("Fake account", entry("c1").manualNote)
        // Zoe's later comment becomes her entry (plan A26).
        assertTrue(entry("c3").isValid)
    }

    @Test
    fun includeUndoesAnExclusionOrOverridesOnlyContentChecks() = runTest {
        repository.exclude(id, "c1", "Fake account")
        repository.include(id, "c1")
        assertTrue(entry("c1").isValid)
        assertNull(entry("c1").manualNote)
        // bob's comment has no mention: a content check the organizer may override.
        assertEquals(ExclusionReason.TOO_FEW_MENTIONS, entry("c2").exclusionReason)
        repository.include(id, "c2", "Tagged a friend in a story")
        assertTrue(entry("c2").isValid)
        assertEquals("Tagged a friend in a story", entry("c2").manualNote)
        // A duplicate can't be slipped in.
        assertTrue(runCatching { repository.include(id, "c3") }.isFailure)
    }

    @Test
    fun theBlocklistAppliesStraightAway() = runTest {
        repository.addToBlocklist(id, "Cat_99", "Spam")
        assertEquals(ExclusionReason.BLOCKLISTED, entry("c4").exclusionReason)
    }

    @Test
    fun nothingChangesAfterTheDraw() = runTest {
        giveaways.transition(id, GiveawayStatus.DRAWN)
        assertTrue(runCatching { repository.exclude(id, "c1", "Too late") }.isFailure)
        assertTrue(entry("c1").isValid)
    }

    @Test
    fun theExportIsTheCanonicalList() = runTest {
        val list = repository.canonicalList(id)
        assertEquals("cat_99\nzoe", list.text)
    }

    @Test
    fun searchAndFiltersPage() = runTest {
        assertEquals(listOf("c1", "c4"), ids(EntryListFilter.VALID))
        assertEquals(listOf("c2", "c3"), ids(EntryListFilter.EXCLUDED))
        assertEquals(listOf("c1", "c3"), ids(EntryListFilter.ALL, "zoe"))
        // % is matched literally, not as a wildcard.
        assertEquals(listOf("c4"), ids(EntryListFilter.ALL, "100%"))
        assertEquals("Love this", repository.entries(id, EntryListFilter.ALL, "love").asSnapshot().single().text)
    }
}
