package app.giveaway.core.data.giveaway

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giveaway.core.data.db.CommentEntity
import app.giveaway.core.data.db.EntryEntity
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.db.GiveawayStatus.ARCHIVED
import app.giveaway.core.data.db.GiveawayStatus.COMMITTED
import app.giveaway.core.data.db.GiveawayStatus.DRAFT
import app.giveaway.core.data.db.GiveawayStatus.DRAWN
import app.giveaway.core.data.db.GiveawayStatus.IMPORTING
import app.giveaway.core.data.db.GiveawayStatus.REVIEW
import app.giveaway.core.data.db.SettingsEntity
import app.giveaway.core.data.settings.DefaultSettingsRepository
import app.giveaway.core.instagram.api.IgMedia
import app.giveaway.core.instagram.api.MediaKind
import app.giveaway.draw.ExclusionReason
import app.giveaway.draw.Rules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** C-13 acceptance: illegal moves throw, and rules can't change once committed. */
@RunWith(RobolectricTestRunner::class)
class GiveawayRepositoryTest {

    private val now = Instant.parse("2026-10-01T12:00:00Z")
    private lateinit var db: GiveawayDatabase
    private lateinit var repository: DefaultGiveawayRepository

    private val media = IgMedia("m1", MediaKind.VIDEO, true, "https://cdn.test/t.jpg", "caption", now, 120, null)
    private val rules = Rules(2, "#win", null, true, true, true, now.plus(Duration.ofDays(7)), 3, 2)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GiveawayDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        repository = DefaultGiveawayRepository(db, DefaultSettingsRepository(db.settingsDao()), clock)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun draft() = repository.createDraft(media, "Summer drop", "shop", rules)

    private suspend fun moveTo(id: Long, vararg path: GiveawayStatus) = path.forEach {
        if (it == COMMITTED) repository.commit(id, "hash", ByteArray(32)) else repository.transition(id, it)
    }

    @Test
    fun theSpecFlowIsAllowed() = runTest {
        val id = draft()
        moveTo(id, COMMITTED, IMPORTING, REVIEW, IMPORTING, REVIEW, DRAWN, ARCHIVED)
        assertEquals(ARCHIVED, repository.get(id)?.status)
    }

    @Test
    fun everyOtherMoveIsRejected() {
        val allowed = setOf(
            DRAFT to COMMITTED, COMMITTED to IMPORTING, IMPORTING to REVIEW,
            REVIEW to IMPORTING, REVIEW to DRAWN, DRAWN to ARCHIVED,
        )
        for (from in GiveawayStatus.entries) for (to in GiveawayStatus.entries) {
            assertEquals("$from -> $to", (from to to) in allowed, GiveawayStateMachine.canMove(from, to))
        }
    }

    @Test
    fun illegalTransitionsThrowAndChangeNothing() = runTest {
        val id = draft()
        assertTrue(runCatching { repository.transition(id, DRAWN) }.exceptionOrNull() is IllegalStateException)
        moveTo(id, COMMITTED, IMPORTING, REVIEW, DRAWN)
        // A finished draw can never go back to review or be redone.
        assertTrue(runCatching { repository.transition(id, REVIEW) }.exceptionOrNull() is IllegalStateException)
        assertEquals(DRAWN, repository.get(id)?.status)
    }

    @Test
    fun aDraftStoresItsRulesAndPost() = runTest {
        val id = draft()
        val giveaway = requireNotNull(repository.get(id))
        assertEquals(DRAFT, giveaway.status)
        assertEquals("REEL", giveaway.mediaType)
        assertEquals(rules.closesAt, giveaway.closesAt)
        assertEquals(rules, repository.rules(id))
    }

    @Test
    fun rulesCanChangeWhileADraft() = runTest {
        val id = draft()
        val changed = rules.copy(minMentions = 1, closesAt = now.plus(Duration.ofDays(3)))
        repository.saveRules(id, changed)
        assertEquals(changed, repository.rules(id))
        assertEquals(changed.closesAt, repository.get(id)?.closesAt)
    }

    @Test
    fun rulesAreFrozenOnceCommitted() = runTest {
        val id = draft()
        moveTo(id, COMMITTED)
        val error = runCatching { repository.saveRules(id, rules.copy(minMentions = 0)) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertEquals(rules, repository.rules(id))
        assertNotNull(db.commitmentDao().get(id))
    }

    @Test
    fun committingTwiceIsRejected() = runTest {
        val id = draft()
        moveTo(id, COMMITTED)
        assertTrue(runCatching { repository.commit(id, "other", ByteArray(32)) }.isFailure)
        assertEquals("hash", db.commitmentDao().get(id)?.commitHash)
    }

    @Test
    fun archivingStartsTheAutoDeleteClock() = runTest {
        db.settingsDao().upsert(SettingsEntity(autoDeleteDays = 30))
        val id = draft()
        moveTo(id, COMMITTED, IMPORTING, REVIEW, DRAWN)
        assertNull(repository.get(id)?.autoDeleteAt)
        moveTo(id, ARCHIVED)
        assertEquals(now.plus(Duration.ofDays(30)), repository.get(id)?.autoDeleteAt)
    }

    @Test
    fun summariesCountCommentsAndValidEntries() = runTest {
        val id = draft()
        db.commentDao().insertAll((1..3).map { CommentEntity(id, "c$it", "u$it", "#win", now) })
        db.entryDao().upsertAll(
            listOf(
                EntryEntity(id, "c1", "u1", true, null, null),
                EntryEntity(id, "c2", "u2", true, null, null),
                EntryEntity(id, "c3", "u3", false, ExclusionReason.DUPLICATE, null),
            ),
        )
        val summary = repository.observeSummaries().first().single()
        assertEquals(3, summary.commentCount)
        assertEquals(2, summary.validEntryCount)
        assertFalse(summary.giveaway.title.isEmpty())
    }
}
