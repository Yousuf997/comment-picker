package app.giveaway.feature.draw

import android.app.Application
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.testing.invoke
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giveaway.core.data.db.CaptionCheck
import app.giveaway.core.data.db.CommentEntity
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.db.MediaKind
import app.giveaway.core.data.draw.DrawChecks
import app.giveaway.core.data.draw.DrawService
import app.giveaway.core.data.draw.RecordSigner
import app.giveaway.core.data.draw.WinnerRepository
import app.giveaway.core.data.giveaway.DefaultGiveawayRepository
import app.giveaway.core.data.giveaway.SeedVault
import app.giveaway.core.data.importing.EntryBuilder
import app.giveaway.core.data.media.MediaRepository
import app.giveaway.core.data.review.EntryRepository
import app.giveaway.core.data.settings.DefaultSettingsRepository
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.instagram.api.IgMedia
import app.giveaway.core.media.CertificateContent
import app.giveaway.core.media.CertificateOutput
import app.giveaway.core.media.CertificateRenderer
import app.giveaway.core.media.CertificateStyle
import app.giveaway.core.media.VideoGallery
import app.giveaway.core.media.VideoInfo
import app.giveaway.core.security.DeviceSigner
import app.giveaway.draw.CanonicalEntryList
import app.giveaway.draw.Commit
import app.giveaway.draw.Rules
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.OutputStream
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import app.giveaway.core.instagram.api.MediaKind as IgMediaKind

/** M-08 acceptance: S15 shows the signed certificate, writes the PDF and Story image, and archives the giveaway. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h1400dp-xhdpi")
class CertificateScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val closesAt = Instant.parse("2026-10-08T10:00:00Z")
    private val clock = Clock.fixed(closesAt.plusSeconds(7_200), ZoneOffset.UTC)
    private val seed = ByteArray(Commit.SEED_BYTES) { (it * 7).toByte() }
    private lateinit var db: GiveawayDatabase
    private lateinit var giveaways: DefaultGiveawayRepository
    private lateinit var vm: CertificateViewModel
    private var id = 0L

    private val vault = object : SeedVault {
        override fun seal(giveawayId: Long, seed: ByteArray) = seed.reversedArray()

        override fun open(giveawayId: Long, sealed: ByteArray) = sealed.reversedArray()
    }

    /** A real P-256 key, so the signature check S15 runs first passes. */
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
    private val noGallery = object : VideoGallery {
        override suspend fun save(source: File, album: String, displayName: String): Uri = error("unused")

        override suspend fun describe(file: File): VideoInfo? = null
    }

    /** The real Story image; a stand-in PDF, since PdfDocument needs a device (CertificateRendererTest). */
    private val output = object : CertificateOutput {
        override fun writePdf(content: CertificateContent, style: CertificateStyle, out: OutputStream) =
            out.write("%PDF-stand-in".toByteArray())

        override fun writePng(content: CertificateContent, style: CertificateStyle, out: OutputStream) =
            CertificateRenderer(style).writePng(content, out)
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(app, GiveawayDatabase::class.java).allowMainThreadQueries().build()
        giveaways = DefaultGiveawayRepository(db, DefaultSettingsRepository(db.settingsDao()), clock)
        id = runBlocking {
            val post = IgMedia("m1", IgMediaKind.IMAGE, false, null, null, closesAt, 0, null)
            val rules = Rules(1, null, null, true, true, true, closesAt, 2, 1)
            val giveawayId = giveaways.createDraft(post, "Win a tote bag!", "shop", rules)
            giveaways.saveCommitment(giveawayId, Commit.commitHash(seed), vault.seal(giveawayId, seed))
            giveaways.commit(giveawayId)
            giveaways.transition(giveawayId, GiveawayStatus.IMPORTING)
            db.commentDao().insertAll(
                listOf("amy", "bob", "cat", "dan").mapIndexed { i, user ->
                    CommentEntity(giveawayId, "c$i", user, "In @friend", closesAt.minusSeconds(60L + i))
                },
            )
            EntryBuilder(db).rebuild(giveawayId)
            DrawService(db, vault, signer, clock).realDraw(giveawayId, DrawChecks(CaptionCheck.FOUND, true))
            giveawayId
        }
    }

    @After
    fun tearDown() {
        if (::vm.isInitialized) vm.viewModelScope.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    private fun show() {
        vm = CertificateViewModel(
            SavedStateHandle(route = CertificateRoute(id)),
            DrawService(db, vault, signer, clock),
            WinnerRepository(db, clock),
            giveaways,
            EntryRepository(db, EntryBuilder(db), clock),
            MediaRepository(app, db, clock),
            noGallery,
            output,
        )
        compose.setContent { GiveawayTheme { CertificateScreen(onDone = {}, viewModel = vm) } }
    }

    private fun status() = runBlocking { giveaways.get(id)?.status }

    private fun shown(text: String) =
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun theCertificateIsMadeAndTheGiveawayArchived() {
        show()
        compose.waitUntil(WAIT_MS) { vm.files.value != null }
        val files = vm.files.value!!
        assertTrue(files.pdf.length() > 0)
        assertTrue(files.image.length() > 0)
        assertEquals(GiveawayStatus.ARCHIVED, status())
        val kinds = runBlocking { db.mediaFileDao().forGiveaway(id).map { it.kind }.toSet() }
        assertEquals(setOf(MediaKind.CERTIFICATE_PDF, MediaKind.CERTIFICATE_IMAGE), kinds)
        // The card shows the revealed seed, so anyone can re-run the draw (spec: S15).
        assertTrue(shown(seed.joinToString("") { "%02x".format(it) }))
    }

    @Test
    fun aChangedRecordGetsNoCertificate() {
        db.openHelper.writableDatabase.execSQL("UPDATE giveaway SET title = 'Other'")
        show()
        compose.waitUntil(WAIT_MS) { shown(app.getString(R.string.certificate_broken_title)) }
        assertNull(vm.files.value)
        assertEquals(GiveawayStatus.DRAWN, status())
        assertTrue(compose.onAllNodesWithTag("certificate:card").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun theExportedListHashesToTheCertificatesEntryListHash() {
        show()
        compose.waitUntil(WAIT_MS) { vm.files.value != null }
        val bytes = runBlocking { vm.entryListBytes() }
        val list = CanonicalEntryList.of(listOf("amy", "bob", "cat", "dan"))
        assertArrayEquals(list.text.toByteArray(Charsets.UTF_8), bytes)
        val record = (vm.state.value as CertificateState.Ready).data.record
        assertEquals(record.entryListHash, list.hashHex)
    }

    @Test
    fun screenshot() {
        show()
        compose.waitUntil(WAIT_MS) { vm.files.value != null }
        compose.onRoot().captureRoboImage("src/test/screenshots/s15_certificate.png")
    }

    @Test
    @Config(qualifiers = "ar-w390dp-h1400dp-xhdpi")
    fun screenshotArabic() {
        show()
        compose.waitUntil(WAIT_MS) { vm.files.value != null }
        compose.onRoot().captureRoboImage("src/test/screenshots/s15_certificate_ar.png")
    }

    private companion object {
        const val WAIT_MS = 15_000L
    }
}
