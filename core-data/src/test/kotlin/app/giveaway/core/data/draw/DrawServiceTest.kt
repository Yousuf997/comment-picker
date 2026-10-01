package app.giveaway.core.data.draw

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giveaway.core.data.db.CaptionCheck
import app.giveaway.core.data.db.CommentEntity
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.giveaway.DefaultGiveawayRepository
import app.giveaway.core.data.giveaway.SeedVault
import app.giveaway.core.data.importing.EntryBuilder
import app.giveaway.core.data.settings.DefaultSettingsRepository
import app.giveaway.core.instagram.api.IgMedia
import app.giveaway.core.instagram.api.MediaKind
import app.giveaway.core.security.DeviceSigner
import app.giveaway.draw.CanonicalEntryList
import app.giveaway.draw.Commit
import app.giveaway.draw.DrawRecord
import app.giveaway.draw.DrawV1
import app.giveaway.draw.Pick
import app.giveaway.draw.Rules
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** C-23 acceptance: one real draw, saved and signed before any animation; test draws stay apart. */
@RunWith(RobolectricTestRunner::class)
class DrawServiceTest {

    private val closesAt = Instant.parse("2026-10-08T10:00:00Z")
    private val clock = Clock.fixed(closesAt.plusSeconds(7_200), ZoneOffset.UTC)
    private val seed = ByteArray(Commit.SEED_BYTES) { it.toByte() }
    private lateinit var db: GiveawayDatabase
    private lateinit var giveaways: DefaultGiveawayRepository
    private lateinit var service: DrawService
    private var id = 0L

    private val vault = object : SeedVault {
        override fun seal(giveawayId: Long, seed: ByteArray) = seed.reversedArray()

        override fun open(giveawayId: Long, sealed: ByteArray) = sealed.reversedArray()
    }

    /** A real ECDSA P-256 key in software, so the signature check is the one the certificate relies on. */
    private val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        .generateKeyPair()
    private val signer = object : RecordSigner {
        override fun sign(record: ByteArray): RecordSigner.Signature {
            val bytes = Signature.getInstance("SHA256withECDSA").run {
                initSign(keyPair.private)
                update(record)
                sign()
            }
            val spki = keyPair.public.encoded
            return RecordSigner.Signature(bytes, spki, DeviceSigner.fingerprintOf(spki))
        }
    }

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, GiveawayDatabase::class.java).allowMainThreadQueries().build()
        giveaways = DefaultGiveawayRepository(db, DefaultSettingsRepository(db.settingsDao()), clock)
        service = DrawService(db, vault, signer, clock)
        val media = IgMedia("m1", MediaKind.IMAGE, false, null, null, closesAt.minusSeconds(864_000), 0, null)
        id = giveaways.createDraft(media, "Drop", "shop", Rules(1, null, null, true, true, true, closesAt, 2, 1))
        giveaways.saveCommitment(id, Commit.commitHash(seed), vault.seal(id, seed))
        giveaways.commit(id)
        giveaways.transition(id, GiveawayStatus.IMPORTING)
        db.commentDao().insertAll(
            listOf("amy", "Bob", "cat", "dan", "eve").mapIndexed { i, user ->
                CommentEntity(id, "c$i", user, "In @friend", closesAt.minusSeconds(600L - i))
            },
        )
        EntryBuilder(db).rebuild(id)
    }

    @After
    fun tearDown() = db.close()

    private val found = DrawChecks(CaptionCheck.FOUND, integrityVerified = false)

    @Test
    fun theRealDrawUsesTheCommittedSeedAndIsSavedBeforeAnything() = runTest {
        val drawId = service.realDraw(id, found)
        val draw = db.drawDao().realDraw(id)!!
        assertEquals(drawId, draw.id)
        assertArrayEquals("the seed is revealed", seed, draw.seed)
        val list = CanonicalEntryList.of(listOf("amy", "Bob", "cat", "dan", "eve"))
        assertEquals(list.hashHex, draw.entryListHash)
        val expected = DrawV1.select(seed, list, winners = 2, alternates = 1).picks
        assertEquals(expected.map { it.username }, db.drawDao().results(drawId).map { it.username })
        assertEquals(GiveawayStatus.DRAWN, giveaways.get(id)?.status)
    }

    @Test
    fun theSignatureCoversTheRecordAndBreaksIfAnythingChanges() = runTest {
        service.realDraw(id, found)
        val draw = db.drawDao().realDraw(id)!!
        val picks = db.drawDao().results(draw.id).map { Pick(it.position, it.username, it.role) }
        val record = DrawRecord(
            algorithmVersion = draw.algorithmVersion,
            account = "shop",
            postId = "m1",
            title = "Drop",
            entriesClosedAt = closesAt,
            commitHash = Commit.commitHash(seed),
            seedHex = seed.joinToString("") { "%02x".format(it) },
            entryListHash = draw.entryListHash,
            entryCount = draw.entryCount,
            winnersRequested = 2,
            alternatesRequested = 1,
            picks = picks,
            drawnAt = draw.drawnAt,
            captionCheck = "FOUND",
            integrityVerified = false,
            partialImport = false,
            manualExclusions = emptyList(),
        )
        assertTrue(DeviceSigner.verify(record.canonicalBytes(), draw.deviceSignature, draw.signerPublicKey))
        val tampered = record.copy(picks = picks.reversed())
        assertFalse(DeviceSigner.verify(tampered.canonicalBytes(), draw.deviceSignature, draw.signerPublicKey))
        assertEquals(DeviceSigner.fingerprintOf(draw.signerPublicKey), draw.signerFingerprint)
    }

    @Test
    fun aSecondRealDrawThrowsAndChangesNothing() = runTest {
        val first = service.realDraw(id, found)
        assertTrue(runCatching { service.realDraw(id, found) }.isFailure)
        assertEquals(first, db.drawDao().realDraw(id)?.id)
    }

    @Test
    fun testDrawsNeverUseTheCommittedSeedAndDontCount() = runTest {
        repeat(3) { service.testDraw(id) }
        assertEquals(GiveawayStatus.REVIEW, giveaways.get(id)?.status)
        assertEquals(null, db.drawDao().realDraw(id))
        // The real draw is still possible and uses the committed seed.
        service.realDraw(id, found)
        assertArrayEquals(seed, db.drawDao().realDraw(id)!!.seed)
    }

    @Test
    fun drawsNeedAReviewedGiveawayWithEntries() = runTest {
        val media = IgMedia("m2", MediaKind.IMAGE, false, null, null, closesAt, 0, null)
        val rules = Rules(1, null, null, true, true, true, closesAt, 1, 0)
        val draft = giveaways.createDraft(media, "Empty", "shop", rules)
        assertTrue(runCatching { service.realDraw(draft, found) }.isFailure)
        assertTrue(runCatching { service.testDraw(draft) }.isFailure)
    }
}
