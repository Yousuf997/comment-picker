package app.giveaway.core.data.giveaway

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.settings.DefaultSettingsRepository
import app.giveaway.core.instagram.api.IgMedia
import app.giveaway.core.instagram.api.MediaKind
import app.giveaway.draw.Commit
import app.giveaway.draw.Rules
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Plan A35: finishing S7 opens the giveaway with a sealed seed and a reminder; nothing is posted. */
@RunWith(RobolectricTestRunner::class)
class GiveawayOpenerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val closesAt = Instant.parse("2026-10-08T10:00:00Z")
    private val clock = Clock.fixed(closesAt.minusSeconds(86_400), ZoneOffset.UTC)
    private val rules = Rules(1, null, null, true, true, true, closesAt, 2, 1)
    private val post = IgMedia("m1", MediaKind.IMAGE, false, null, "Win a tote bag!", closesAt, 0, null)
    private val seed = ByteArray(Commit.SEED_BYTES) { (it * 7 + 3).toByte() }
    private val scheduled = mutableListOf<Pair<Long, Instant>>()
    private lateinit var db: GiveawayDatabase
    private lateinit var giveaways: DefaultGiveawayRepository

    private val vault = object : SeedVault {
        override fun seal(giveawayId: Long, seed: ByteArray) = seed.reversedArray()

        override fun open(giveawayId: Long, sealed: ByteArray) = sealed.reversedArray()
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, GiveawayDatabase::class.java).allowMainThreadQueries().build()
        giveaways = DefaultGiveawayRepository(db, DefaultSettingsRepository(db.settingsDao()), clock)
    }

    @After
    fun tearDown() = db.close()

    // The seed source hands out a copy: the commitment zeroes the bytes it was given once they're sealed.
    private fun opener() =
        DefaultGiveawayOpener(db, giveaways, DrawCommitments(giveaways, vault) { seed.copyOf() }) { id, at ->
            scheduled += id to at
        }

    @Test
    fun createOpensTheGiveawayWithASealedSeedAndAReminder() = runTest {
        val id = opener().create(post, "Win a tote bag!", "shop", rules)
        assertEquals(GiveawayStatus.COMMITTED, giveaways.get(id)?.status)
        assertEquals(rules, giveaways.rules(id))
        val commitment = checkNotNull(giveaways.commitment(id))
        assertEquals(Commit.commitHash(seed), commitment.commitHash)
        assertArrayEquals(seed, vault.open(id, commitment.encryptedSeed))
        assertNull(commitment.captionVerifiedAt)
        assertEquals(listOf(id to closesAt), scheduled)
    }

    @Test
    fun aDraftLeftByAnOlderVersionOpensOnce() = runTest {
        val draft = giveaways.createDraft(post, "Old draft", "shop", rules)
        opener().open(draft)
        assertEquals(GiveawayStatus.COMMITTED, giveaways.get(draft)?.status)
        assertEquals(Commit.commitHash(seed), giveaways.commitment(draft)?.commitHash)
        // Already open, or gone: nothing more happens.
        opener().open(draft)
        opener().open(999)
        assertEquals(listOf(draft to closesAt), scheduled)
    }
}
