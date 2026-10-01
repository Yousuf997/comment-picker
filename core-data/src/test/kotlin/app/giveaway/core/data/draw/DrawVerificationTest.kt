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
import app.giveaway.core.data.review.EntryRepository
import app.giveaway.core.data.settings.DefaultSettingsRepository
import app.giveaway.core.instagram.api.IgMedia
import app.giveaway.core.instagram.api.MediaKind
import app.giveaway.core.security.DeviceSigner
import app.giveaway.draw.Commit
import app.giveaway.draw.DrawRecord
import app.giveaway.draw.Pick
import app.giveaway.draw.Role
import app.giveaway.draw.Rules
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

/** M-06 acceptance: the stored draw rebuilds into the signed record, and tampering with any field fails. */
@RunWith(RobolectricTestRunner::class)
class DrawVerificationTest {

    private val closesAt = Instant.parse("2026-10-08T10:00:00Z")
    private val clock = Clock.fixed(closesAt.plusSeconds(7_200), ZoneOffset.UTC)
    private val seed = ByteArray(Commit.SEED_BYTES) { (it * 3).toByte() }
    private lateinit var db: GiveawayDatabase
    private lateinit var service: DrawService
    private var id = 0L

    private val vault = object : SeedVault {
        override fun seal(giveawayId: Long, seed: ByteArray) = seed.reversedArray()

        override fun open(giveawayId: Long, sealed: ByteArray) = sealed.reversedArray()
    }

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
        val giveaways = DefaultGiveawayRepository(db, DefaultSettingsRepository(db.settingsDao()), clock)
        service = DrawService(db, vault, signer, clock)
        val media = IgMedia("m1", MediaKind.IMAGE, false, null, null, closesAt.minusSeconds(864_000), 0, null)
        id = giveaways.createDraft(media, "Drop", "shop", Rules(1, null, null, true, true, true, closesAt, 2, 2))
        giveaways.saveCommitment(id, Commit.commitHash(seed), vault.seal(id, seed))
        giveaways.commit(id)
        giveaways.transition(id, GiveawayStatus.IMPORTING)
        db.commentDao().insertAll(
            listOf("amy", "bob", "cat", "dan", "eve", "fay").mapIndexed { i, user ->
                CommentEntity(id, "c$i", user, "In @friend", closesAt.minusSeconds(600L - i))
            },
        )
        val builder = EntryBuilder(db)
        builder.rebuild(id)
        EntryRepository(db, builder, clock).exclude(id, "c5", "Fake account")
    }

    @After
    fun tearDown() = db.close()

    private suspend fun drawn(): SignedDrawRecord {
        service.realDraw(id, DrawChecks(CaptionCheck.FOUND, integrityVerified = true))
        return service.signedRecord(id)!!
    }

    private fun sql(statement: String) = db.openHelper.writableDatabase.execSQL(statement)

    @Test
    fun thereIsNoRecordBeforeTheDraw() = runTest {
        assertNull(service.signedRecord(id))
    }

    @Test
    fun theStoredDrawRebuildsIntoTheSignedRecord() = runTest {
        val signed = drawn()
        assertTrue(signed.verifies())
        val record = signed.record
        assertEquals(listOf(DrawRecord.ManualExclusion("fay", "Fake account")), record.manualExclusions)
        assertEquals(5, record.entryCount)
        assertEquals("FOUND", record.captionCheck)
        assertTrue(record.integrityVerified)
        assertEquals(4, record.picks.size)
        assertEquals(DeviceSigner.fingerprintOf(keyPair.public.encoded), signed.fingerprint)
    }

    @Test
    fun confirmingAndReplacingWinnersKeepsTheRecordValid() = runTest {
        drawn()
        val winners = WinnerRepository(db, clock)
        winners.confirm(id, position = 1)
        winners.replace(id, position = 2, reason = "Didn't answer")
        assertTrue(service.signedRecord(id)!!.verifies())
    }

    @Test
    fun tamperingWithAnyStoredValueBreaksTheSignature() = runTest {
        val first = drawn().record.picks.first().username
        val seedHex = seed.joinToString("") { "%02x".format(it) }
        val edits = listOf(
            "UPDATE giveaway SET title = 'Other'" to "UPDATE giveaway SET title = 'Drop'",
            "UPDATE giveaway SET ownerUsername = 'shop2'" to "UPDATE giveaway SET ownerUsername = 'shop'",
            "UPDATE giveaway SET igMediaId = 'm2'" to "UPDATE giveaway SET igMediaId = 'm1'",
            "UPDATE rules SET winnersCount = 3" to "UPDATE rules SET winnersCount = 2",
            "UPDATE commitment SET commitHash = upper(commitHash)" to
                "UPDATE commitment SET commitHash = lower(commitHash)",
            "UPDATE draw SET seed = zeroblob(32)" to "UPDATE draw SET seed = X'$seedHex'",
            "UPDATE draw SET entryCount = 6" to "UPDATE draw SET entryCount = 5",
            "UPDATE draw SET captionCheck = 'NOT_FOUND'" to "UPDATE draw SET captionCheck = 'FOUND'",
            "UPDATE draw SET integrityVerified = 0" to "UPDATE draw SET integrityVerified = 1",
            "UPDATE draw SET partialImport = 1" to "UPDATE draw SET partialImport = 0",
            "UPDATE draw_result SET username = 'zed' WHERE position = 1" to
                "UPDATE draw_result SET username = '$first' WHERE position = 1",
            "UPDATE entry SET manualNote = 'Spam' WHERE commentId = 'c5'" to
                "UPDATE entry SET manualNote = 'Fake account' WHERE commentId = 'c5'",
        )
        for ((tamper, restore) in edits) {
            sql(tamper)
            assertFalse(tamper, service.signedRecord(id)!!.verifies())
            sql(restore)
            assertTrue(restore, service.signedRecord(id)!!.verifies())
        }
    }

    @Test
    fun everyFieldOfTheRecordIsSigned() = runTest {
        val signed = drawn()
        val r = signed.record
        val later = r.drawnAt.plusMillis(1)
        val changed = listOf(
            r.copy(algorithmVersion = "v2"),
            r.copy(account = "other"),
            r.copy(postId = "m9"),
            r.copy(title = "Drop!"),
            r.copy(entriesClosedAt = r.entriesClosedAt.plusMillis(1)),
            r.copy(commitHash = r.commitHash.reversed()),
            r.copy(seedHex = r.seedHex.reversed()),
            r.copy(entryListHash = r.entryListHash.reversed()),
            r.copy(entryCount = r.entryCount + 1),
            r.copy(winnersRequested = 1),
            r.copy(alternatesRequested = 3),
            r.copy(picks = r.picks.dropLast(1)),
            r.copy(picks = r.picks.map { if (it.position == 1) Pick(1, it.username, Role.ALTERNATE) else it }),
            r.copy(drawnAt = later),
            r.copy(captionCheck = "NOT_CHECKED"),
            r.copy(integrityVerified = false),
            r.copy(partialImport = true),
            r.copy(manualExclusions = emptyList()),
        )
        for (record in changed) {
            val forged = SignedDrawRecord(record, signed.signature, signed.publicKeySpki, signed.fingerprint)
            assertFalse(record.toString(), forged.verifies())
        }
        val otherKey = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
            .generateKeyPair().public.encoded
        val otherPrint = DeviceSigner.fingerprintOf(otherKey)
        val wrongPrint = SignedDrawRecord(r, signed.signature, signed.publicKeySpki, otherPrint)
        assertFalse("wrong fingerprint", wrongPrint.verifies())
        assertFalse("another key", SignedDrawRecord(r, signed.signature, otherKey, otherPrint).verifies())
    }
}
