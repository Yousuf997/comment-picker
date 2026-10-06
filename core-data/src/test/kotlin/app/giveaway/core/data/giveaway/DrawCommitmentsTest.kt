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

/** C-14 and plan A35: the seed is made once and sealed per giveaway, and its hash is kept as the commit hash. */
@RunWith(RobolectricTestRunner::class)
class DrawCommitmentsTest {

    private val now = Instant.parse("2026-10-01T10:00:00Z")
    private lateinit var db: GiveawayDatabase
    private lateinit var repository: DefaultGiveawayRepository
    private val sealed = mutableMapOf<Long, ByteArray>()

    /** Records what it seals; "sealing" reverses the bytes so stored data differs from the seed. */
    private val vault = object : SeedVault {
        override fun seal(giveawayId: Long, seed: ByteArray): ByteArray {
            sealed[giveawayId] = seed.copyOf()
            return seed.reversedArray()
        }

        override fun open(giveawayId: Long, sealed: ByteArray): ByteArray = sealed.reversedArray()
    }

    private val media = IgMedia("m1", MediaKind.IMAGE, false, null, null, now, 10, null)
    private val rules = Rules(1, null, null, true, true, true, now.plusSeconds(86_400), 1, 2)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, GiveawayDatabase::class.java).allowMainThreadQueries().build()
        repository = DefaultGiveawayRepository(
            db,
            DefaultSettingsRepository(db.settingsDao()),
            Clock.fixed(now, ZoneOffset.UTC),
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun theHashIsOfASealedThirtyTwoByteSeed() = runTest {
        val id = repository.createDraft(media, "Drop", "shop", rules)
        val hash = DrawCommitments(repository, vault).commitHashFor(id)
        val seed = sealed.getValue(id)
        assertEquals(Commit.SEED_BYTES, seed.size)
        assertEquals(Commit.commitHash(seed), hash)
        val stored = repository.commitment(id)!!
        assertEquals(hash, stored.commitHash)
        assertFalse("the seed must not be stored in the clear", stored.encryptedSeed.contentEquals(seed))
        assertTrue(vault.open(id, stored.encryptedSeed).contentEquals(seed))
        assertEquals(GiveawayStatus.DRAFT, repository.get(id)?.status)
    }

    @Test
    fun theSeedNeverChangesOnceMade() = runTest {
        val id = repository.createDraft(media, "Drop", "shop", rules)
        val commitments = DrawCommitments(repository, vault)
        val first = commitments.commitHashFor(id)
        assertEquals(first, commitments.commitHashFor(id))
        repository.commit(id)
        assertEquals(first, commitments.commitHashFor(id))
        assertEquals(1, sealed.size)
    }

    @Test
    fun eachGiveawayGetsItsOwnSeed() = runTest {
        val commitments = DrawCommitments(repository, vault)
        val a = commitments.commitHashFor(repository.createDraft(media, "A", "shop", rules))
        val b = commitments.commitHashFor(repository.createDraft(media, "B", "shop", rules))
        assertTrue(a != b)
    }
}
