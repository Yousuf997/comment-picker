package app.giveaway.feature.draw

import android.app.Application
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import app.giveaway.core.data.settings.DefaultSettingsRepository
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.instagram.api.IgMedia
import app.giveaway.core.instagram.api.MediaKind as IgMediaKind
import app.giveaway.core.media.VideoGallery
import app.giveaway.core.media.VideoInfo
import app.giveaway.draw.Commit
import app.giveaway.draw.Rules
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.TimeZone
import app.giveaway.core.designsystem.R as DesignR

/** M-04/M-05 acceptance: S13 over S14 must be answered; Save copies to the gallery, Don't save deletes at once. */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xhdpi")
class SaveVideoTest {

    private val systemZone: TimeZone = TimeZone.getDefault()

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val closesAt = Instant.parse("2026-10-08T10:00:00Z")
    private val clock = Clock.fixed(closesAt.plusSeconds(7_200), ZoneOffset.UTC)
    private val seed = ByteArray(Commit.SEED_BYTES) { (it * 5).toByte() }
    private lateinit var db: GiveawayDatabase
    private lateinit var media: MediaRepository
    private lateinit var vm: WinnersViewModel
    private lateinit var video: File
    private var id = 0L

    private val galleryCopies = mutableListOf<Pair<String, String>>()
    private val gallery = object : VideoGallery {
        override suspend fun save(source: File, album: String, displayName: String): Uri {
            assertTrue("copied from the private file", source.exists())
            galleryCopies += album to displayName
            return Uri.parse("content://media/external/video/media/7")
        }

        override suspend fun describe(file: File) = VideoInfo(durationMs = 21_000, width = 1080, height = 1920)
    }

    private val vault = object : SeedVault {
        override fun seal(giveawayId: Long, seed: ByteArray) = seed.reversedArray()

        override fun open(giveawayId: Long, sealed: ByteArray) = sealed.reversedArray()
    }
    private val signer = object : RecordSigner {
        override fun sign(record: ByteArray) = RecordSigner.Signature(byteArrayOf(1), byteArrayOf(2), "fp")
    }

    @Before
    fun setUp() {
        // Dates on screen follow the phone's zone; pin it so screenshots match on every machine.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(app, GiveawayDatabase::class.java).allowMainThreadQueries().build()
        media = MediaRepository(app, db, clock)
        id = runBlocking {
            val giveaways = DefaultGiveawayRepository(db, DefaultSettingsRepository(db.settingsDao()), clock)
            val post = IgMedia("m1", IgMediaKind.IMAGE, false, null, null, closesAt, 0, null)
            val rules = Rules(1, null, null, true, true, true, closesAt, 1, 1)
            val giveawayId = giveaways.createDraft(post, "Win a tote bag!", "shop", rules)
            giveaways.saveCommitment(giveawayId, Commit.commitHash(seed), vault.seal(giveawayId, seed))
            giveaways.commit(giveawayId)
            giveaways.transition(giveawayId, GiveawayStatus.IMPORTING)
            db.commentDao().insertAll(
                listOf("amy", "bob", "cat").mapIndexed { i, user ->
                    CommentEntity(giveawayId, "c$i", user, "In @friend", closesAt.minusSeconds(60L + i))
                },
            )
            EntryBuilder(db).rebuild(giveawayId)
            DrawService(db, vault, signer, clock).realDraw(giveawayId, DrawChecks(CaptionCheck.FOUND, false))
            giveawayId
        }
        video = media.recordingFile(id).apply { writeBytes(ByteArray(1_024)) }
        runBlocking { media.recordingSaved(id, video) }
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(systemZone)
        if (::vm.isInitialized) vm.viewModelScope.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    private fun show() {
        vm = WinnersViewModel(SavedStateHandle(route = WinnersRoute(id)), WinnerRepository(db, clock), media, gallery)
        compose.setContent { GiveawayTheme { WinnersScreen(onCreateCertificate = {}, viewModel = vm) } }
        compose.waitUntil(WAIT_MS) { sheetShown() }
    }

    private fun sheetShown() = compose.onAllNodesWithTag("screen:S13").fetchSemanticsNodes().isNotEmpty()

    private fun text(id: Int) = compose.onNodeWithText(app.getString(id))

    private fun videos() = runBlocking { db.mediaFileDao().forGiveaway(id).filter { it.kind == MediaKind.VIDEO } }

    @Test
    fun saveCopiesToTheGalleryAndClearsTheChoice() {
        show()
        text(R.string.save_video_save).performClick()
        compose.waitUntil(WAIT_MS) { !sheetShown() }
        assertEquals("Movies album", app.getString(DesignR.string.app_name), galleryCopies.single().first)
        val saved = videos().single()
        assertTrue(saved.savedToGallery)
        assertFalse(saved.pendingDecision)
        assertFalse("the private copy is removed", video.exists())
    }

    @Test
    fun dontSaveDeletesTheRecordingAtOnce() {
        show()
        text(R.string.save_video_discard).performClick()
        compose.waitUntil(WAIT_MS) { !sheetShown() }
        assertFalse(video.exists())
        assertTrue(videos().isEmpty())
        assertTrue(galleryCopies.isEmpty())
    }

    @Test
    fun backDoesNotDismissTheSheet() {
        show()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertTrue(sheetShown())
        assertTrue(videos().single().pendingDecision)
    }

    @Test
    fun theChoiceIsAskedAgainUntilMade() {
        // Nothing chosen: a second visit to S14 shows the sheet again (spec: requirement 8).
        show()
        assertTrue(videos().single().pendingDecision)
    }

    @Test
    fun screenshotSheet() {
        show()
        compose.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/s13_save_video.png")
    }

    private companion object {
        const val WAIT_MS = 15_000L
    }
}
