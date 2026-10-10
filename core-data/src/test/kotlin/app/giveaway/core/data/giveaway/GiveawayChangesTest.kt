package app.giveaway.core.data.giveaway

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giveaway.core.data.cleanup.GiveawayDeleter
import app.giveaway.core.data.db.AccountEntity
import app.giveaway.core.data.db.BlocklistEntity
import app.giveaway.core.data.db.CommentEntity
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.db.MediaFileEntity
import app.giveaway.core.data.db.MediaKind
import app.giveaway.core.data.draw.DrawChecks
import app.giveaway.core.data.draw.DrawReset
import app.giveaway.core.data.draw.DrawService
import app.giveaway.core.data.draw.RecordSigner
import app.giveaway.core.data.draw.WinnerRepository
import app.giveaway.core.data.importing.EntryBuilder
import app.giveaway.core.data.importing.ImportWork
import app.giveaway.core.data.settings.DefaultSettingsRepository
import app.giveaway.core.data.work.DeadlineScheduler
import app.giveaway.core.instagram.api.IgMedia
import app.giveaway.core.instagram.api.MediaKind as IgMediaKind
import app.giveaway.core.security.DeviceSigner
import app.giveaway.draw.Commit
import app.giveaway.draw.Rules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** X-1: redraw, rules and post changes at any stage, and deleting giveaways (plan A30–A33). */
@RunWith(RobolectricTestRunner::class)
class GiveawayChangesTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val closesAt = Instant.parse("2026-10-08T10:00:00Z")
    private val clock = Clock.fixed(closesAt.plusSeconds(3_600), ZoneOffset.UTC)
    private val seed = ByteArray(Commit.SEED_BYTES) { (it * 3).toByte() }
    private val nextSeed = ByteArray(Commit.SEED_BYTES) { (it * 5 + 1).toByte() }
    private val rules = Rules(1, null, null, true, true, true, closesAt, 2, 1)
    private lateinit var db: GiveawayDatabase
    private lateinit var giveaways: DefaultGiveawayRepository
    private lateinit var editor: GiveawayEditor
    private val scheduled = mutableListOf<Pair<Long, Instant>>()
    private val cancelled = mutableListOf<Long>()
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
    private val work = object : ImportWork {
        override fun start(giveawayId: Long) = Unit

        override fun observeActive(giveawayId: Long) = flowOf(false)

        override fun cancel(giveawayId: Long) {
            cancelled += giveawayId
        }
    }

    private val draws get() = DrawService(db, vault, signer, clock)
    private val reset get() = DrawReset(db, vault, clock) { nextSeed.copyOf() }

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(context, GiveawayDatabase::class.java).allowMainThreadQueries().build()
        giveaways = DefaultGiveawayRepository(db, DefaultSettingsRepository(db.settingsDao()), clock)
        val schedule = DeadlineScheduler { gid, at -> scheduled += gid to at }
        val commitments = DrawCommitments(giveaways, vault) { seed.copyOf() }
        val opener = DefaultGiveawayOpener(db, giveaways, commitments, clock, schedule)
        editor = GiveawayEditor(db, giveaways, EntryBuilder(db), schedule, work, opener, clock)
        val post = IgMedia("m1", IgMediaKind.IMAGE, false, null, null, closesAt, 0, null)
        id = giveaways.createDraft(post, "Win a tote bag!", "shop", rules)
        giveaways.saveCommitment(id, Commit.commitHash(seed), vault.seal(id, seed))
        giveaways.commit(id)
    }

    @After
    fun tearDown() = db.close()

    /** Imports four comments (two of them mention nobody) and builds the entries. */
    private suspend fun reviewed() {
        giveaways.transition(id, GiveawayStatus.IMPORTING)
        db.commentDao().insertAll(
            listOf("amy" to "In @friend", "bob" to "Me @pal", "cat" to "Love it", "dan" to "Yes!")
                .mapIndexed { i, (u, t) ->
                    CommentEntity(id, "c$i", u, t, closesAt.minusSeconds(60L + i))
                },
        )
        EntryBuilder(db).rebuild(id)
    }

    private suspend fun drawn() {
        reviewed()
        draws.realDraw(id, DrawChecks(true))
    }

    private suspend fun valid() = db.entryDao().validCount(id)

    private suspend fun status() = giveaways.get(id)?.status

    // --- Redraw (plan A30) ---

    @Test
    fun redrawClearsTheResultWithANewSeedAndTheNextDrawVerifies() = runTest {
        drawn()
        val first = db.drawDao().realDraw(id)!!
        val winner = db.drawDao().results(first.id).first().position
        WinnerRepository(db, clock).confirm(id, winner)
        val certificate = File(context.filesDir, "certificates/certificate-$id.pdf").apply {
            parentFile?.mkdirs()
            writeText("pdf")
        }
        val row = MediaFileEntity(0, id, MediaKind.CERTIFICATE_PDF, certificate.path, false, clock.instant(), false)
        db.mediaFileDao().insert(row)

        reset.redraw(id)

        assertEquals(GiveawayStatus.REVIEW, status())
        assertNull(db.drawDao().realDraw(id))
        assertTrue("past winners from the cleared result go", db.pastWinnerDao().usernamesExcept(-1).isEmpty())
        assertFalse(certificate.exists())
        assertTrue(db.mediaFileDao().forGiveaway(id).isEmpty())
        val commitment = db.commitmentDao().get(id)!!
        assertEquals(Commit.commitHash(nextSeed), commitment.commitHash)
        assertArrayEquals(nextSeed, vault.open(id, commitment.encryptedSeed))
        assertNull(commitment.captionVerifiedAt)

        draws.realDraw(id, DrawChecks(true))
        val again = draws.signedRecord(id)!!
        assertTrue(again.verifies())
        assertEquals(Commit.commitHash(nextSeed), again.record.commitHash)
    }

    @Test
    fun anArchivedGiveawayCanBeRedrawnAndLosesItsDeleteDate() = runTest {
        drawn()
        giveaways.transition(id, GiveawayStatus.ARCHIVED)
        assertNotNull(giveaways.get(id)?.autoDeleteAt)
        reset.redraw(id)
        assertEquals(GiveawayStatus.REVIEW, status())
        assertNull(giveaways.get(id)?.autoDeleteAt)
    }

    @Test
    fun onlyADrawnGiveawayCanBeRedrawn() = runTest {
        reviewed()
        assertTrue(runCatching { reset.redraw(id) }.isFailure)
        assertEquals(Commit.commitHash(seed), db.commitmentDao().get(id)?.commitHash)
    }

    // --- Rules at any stage (plan A31) ---

    @Test
    fun aNewDeadlineAfterCommitIsRescheduled() = runTest {
        val later = closesAt.plusSeconds(86_400)
        editor.saveRules(id, rules.copy(closesAt = later))
        assertEquals(listOf(id to later), scheduled)
        editor.saveRules(id, rules.copy(closesAt = later, winnersCount = 3))
        assertEquals("same deadline, no new schedule", 1, scheduled.size)
    }

    @Test
    fun entriesClosedNowGetNoReminder() = runTest {
        editor.saveRules(id, rules.copy(closesAt = clock.instant()))
        assertEquals(clock.instant(), giveaways.rules(id)?.closesAt)
        assertTrue(scheduled.isEmpty())
    }

    @Test
    fun inReviewTheEntriesAreFilteredAgain() = runTest {
        reviewed()
        assertEquals(2, valid())
        editor.saveRules(id, rules.copy(minMentions = 0))
        assertEquals(4, valid())
        assertFalse(editor.clearsResult(id))
    }

    @Test
    fun afterTheDrawNewRulesClearTheResultButKeepTheSeed() = runTest {
        drawn()
        assertTrue(editor.clearsResult(id))
        editor.saveRules(id, rules.copy(minMentions = 0))
        assertEquals(GiveawayStatus.REVIEW, status())
        assertNull(db.drawDao().realDraw(id))
        assertEquals(4, valid())
        // The seed stays, so the next draw verifies against the same commit hash.
        assertEquals(Commit.commitHash(seed), db.commitmentDao().get(id)?.commitHash)
        draws.realDraw(id, DrawChecks(true))
        assertTrue(draws.signedRecord(id)!!.verifies())
    }

    // --- A new post (plan A32) ---

    private val newPost = IgMedia("m2", IgMediaKind.VIDEO, true, "https://cdn.example/t.jpg", null, closesAt, 0, null)

    @Test
    fun beforeTheImportOnlyThePostChanges() = runTest {
        editor.changePost(id, newPost)
        val giveaway = giveaways.get(id)!!
        assertEquals("m2", giveaway.igMediaId)
        assertEquals("REEL", giveaway.mediaType)
        assertEquals(GiveawayStatus.COMMITTED, giveaway.status)
        assertEquals(Commit.commitHash(seed), db.commitmentDao().get(id)?.commitHash)
    }

    @Test
    fun afterTheDrawANewPostStartsTheImportOver() = runTest {
        drawn()
        editor.changePost(id, newPost)
        assertEquals(GiveawayStatus.COMMITTED, status())
        assertEquals(0, db.commentDao().count(id))
        assertEquals(0, valid())
        assertNull(db.drawDao().realDraw(id))
        assertNull(db.importStateDao().get(id))
        assertEquals(listOf(id), cancelled)
        assertEquals(listOf(id to closesAt), scheduled)
    }

    // --- Deleting (plan A33) ---

    @Test
    fun deletingAGiveawayRemovesItsDataAndFilesOnly() = runTest {
        drawn()
        val video = File(context.filesDir, "videos/draw-$id.mp4").apply {
            parentFile?.mkdirs()
            writeText("video")
        }
        db.mediaFileDao().insert(MediaFileEntity(0, id, MediaKind.VIDEO, video.path, false, clock.instant(), true))
        db.blocklistDao().upsert(BlocklistEntity("spam.bot", clock.instant(), null))
        db.accountDao().upsert(AccountEntity("ig1", "shop", byteArrayOf(1), closesAt))

        GiveawayDeleter(db, work).delete(id)

        assertNull(giveaways.get(id))
        assertEquals(0, db.commentDao().count(id))
        assertNull(db.commitmentDao().get(id))
        assertFalse(video.exists())
        assertEquals(listOf(id), cancelled)
        assertEquals(listOf("spam.bot"), db.blocklistDao().usernames())
        assertEquals("shop", db.accountDao().get()?.username)
    }

    @Test
    fun deleteAllRemovesEveryGiveaway() = runTest {
        val post = IgMedia("m9", IgMediaKind.IMAGE, false, null, null, closesAt, 0, null)
        val other = giveaways.createDraft(post, "Second", "shop", rules)
        assertEquals(2, GiveawayDeleter(db, work).deleteAll())
        assertNull(giveaways.get(id))
        assertNull(giveaways.get(other))
        assertNotEquals(0, cancelled.size)
    }

    @Test
    fun theStepBarFollowsTheStatus() = runTest {
        val progress = WizardProgress(db)
        assertEquals(setOf(1, 2, 3), progress.steps(id).first())
        reviewed()
        assertEquals((1..5).toSet(), progress.steps(id).first())
        assertEquals(setOf(1, 2), WizardProgress.reachable(GiveawayStatus.DRAFT))
        assertEquals(emptySet<Int>(), progress.steps(-1).first())
    }
}
