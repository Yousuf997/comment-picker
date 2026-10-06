package app.giveaway.core.data.draw

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giveaway.core.data.db.CommentEntity
import app.giveaway.core.data.db.ConfirmationStatus
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.giveaway.DefaultGiveawayRepository
import app.giveaway.core.data.giveaway.SeedVault
import app.giveaway.core.data.importing.EntryBuilder
import app.giveaway.core.data.settings.DefaultSettingsRepository
import app.giveaway.core.instagram.api.IgMedia
import app.giveaway.core.instagram.api.MediaKind
import app.giveaway.draw.Commit
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

/** M-01 acceptance: confirming feeds past winners; replacements use the next alternate and are recorded. */
@RunWith(RobolectricTestRunner::class)
class WinnerRepositoryTest {

    private val closesAt = Instant.parse("2026-10-08T10:00:00Z")
    private val clock = Clock.fixed(closesAt.plusSeconds(7_200), ZoneOffset.UTC)
    private val seed = ByteArray(Commit.SEED_BYTES) { (it * 7).toByte() }
    private lateinit var db: GiveawayDatabase
    private lateinit var winners: WinnerRepository
    private var id = 0L

    private val vault = object : SeedVault {
        override fun seal(giveawayId: Long, seed: ByteArray) = seed.reversedArray()

        override fun open(giveawayId: Long, sealed: ByteArray) = sealed.reversedArray()
    }
    private val signer = object : RecordSigner {
        override fun sign(record: ByteArray) = RecordSigner.Signature(byteArrayOf(1), byteArrayOf(2), "fp")
    }

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, GiveawayDatabase::class.java).allowMainThreadQueries().build()
        val giveaways = DefaultGiveawayRepository(db, DefaultSettingsRepository(db.settingsDao()), clock)
        winners = WinnerRepository(db, clock)
        val media = IgMedia("m1", MediaKind.IMAGE, false, null, null, closesAt.minusSeconds(864_000), 0, null)
        id = giveaways.createDraft(media, "Drop", "shop", Rules(1, null, null, true, true, true, closesAt, 2, 2))
        giveaways.saveCommitment(id, Commit.commitHash(seed), vault.seal(id, seed))
        giveaways.commit(id)
        giveaways.transition(id, GiveawayStatus.IMPORTING)
        db.commentDao().insertAll(
            listOf("amy", "bob", "cat", "dan", "eve", "fay").mapIndexed { i, user ->
                CommentEntity(id, "c$i", user, "Count me in @friend $i", closesAt.minusSeconds(600L - i))
            },
        )
        EntryBuilder(db).rebuild(id)
        DrawService(db, vault, signer, clock).realDraw(id, DrawChecks(false))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun view() = winners.observe(id).first()!!

    @Test
    fun theViewShowsWinnersWithTheirCommentAndTheAlternates() = runTest {
        val view = view()
        assertEquals(2, view.winners.size)
        assertEquals(listOf(1, 2), view.winners.map { it.rank })
        assertTrue(view.winners.all { it.status == ConfirmationStatus.PENDING && !it.promoted })
        assertTrue(view.winners.all { it.comment!!.startsWith("Count me in") })
        assertEquals(2, view.alternates.size)
        assertEquals(6, view.entryCount)
    }

    @Test
    fun confirmingAWinnerAddsThemToPastWinners() = runTest {
        val first = view().winners.first()
        winners.confirm(id, first.position)
        assertEquals(ConfirmationStatus.CONFIRMED, view().winners.first().status)
        assertEquals(listOf(first.username), db.pastWinnerDao().usernamesExcept(-1))
        assertTrue(runCatching { winners.confirm(id, first.position) }.isFailure)
    }

    @Test
    fun replacingPromotesTheNextAlternateAndRecordsWhy() = runTest {
        val before = view()
        val replaced = before.winners.first()
        val promoted = winners.replace(id, replaced.position, "Doesn't follow the account")
        assertEquals(before.alternates.first(), promoted)
        val after = view()
        assertEquals(promoted, after.winners.first().username)
        assertTrue(after.winners.first().promoted)
        assertEquals(1, after.winners.first().rank)
        assertEquals(before.alternates.drop(1), after.alternates)
        val replacement = after.replacements.single()
        assertEquals(replaced.username, replacement.replaced)
        assertEquals("Doesn't follow the account", replacement.reason)
        assertEquals(promoted, replacement.by)
    }

    @Test
    fun aReplacementNeedsAReasonAndCanItselfBeReplaced() = runTest {
        val first = view().winners.first()
        assertTrue(runCatching { winners.replace(id, first.position, " ") }.isFailure)
        winners.replace(id, first.position, "No follow")
        val stepIn = view().winners.first()
        winners.replace(id, stepIn.position, "No like")
        assertEquals(2, view().replacements.size)
        assertTrue(view().alternatesUsedUp)
    }

    @Test
    fun whenAlternatesRunOutTheGiveawayCanFinishWithFewerWinners() = runTest {
        val (one, two) = view().winners
        winners.replace(id, one.position, "No follow")
        winners.replace(id, two.position, "No follow")
        val third = view().winners.first()
        assertNull(winners.replace(id, third.position, "No follow"))
        assertEquals(ConfirmationStatus.PENDING, view().winners.first().status)
        winners.confirm(id, third.position)
        assertEquals(ConfirmationStatus.CONFIRMED, view().winners.first().status)
    }
}
