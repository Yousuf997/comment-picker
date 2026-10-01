package app.giveaway.feature.draw

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
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
import app.giveaway.core.data.draw.DrawChecks
import app.giveaway.core.data.draw.DrawService
import app.giveaway.core.data.draw.RecordSigner
import app.giveaway.core.data.giveaway.DefaultGiveawayRepository
import app.giveaway.core.data.giveaway.SeedVault
import app.giveaway.core.data.importing.EntryBuilder
import app.giveaway.core.data.review.EntryRepository
import app.giveaway.core.data.settings.DefaultSettingsRepository
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.instagram.api.CommentPage
import app.giveaway.core.instagram.api.IgError
import app.giveaway.core.instagram.api.IgMedia
import app.giveaway.core.instagram.api.IgResult
import app.giveaway.core.instagram.api.InstagramRepository
import app.giveaway.core.instagram.api.MediaKind
import app.giveaway.core.instagram.api.MediaPage
import app.giveaway.core.instagram.api.ReplyCountPage
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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** C-21 acceptance: S11's checks, the fewer-entries notice, test draws, and the real draw. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h1000dp-xhdpi")
class DrawStageTest {

    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val closesAt = Instant.parse("2026-10-08T10:00:00Z")
    private val clock = Clock.fixed(closesAt.plusSeconds(7_200), ZoneOffset.UTC)
    private val seed = ByteArray(Commit.SEED_BYTES) { it.toByte() }
    private lateinit var db: GiveawayDatabase
    private lateinit var giveaways: DefaultGiveawayRepository
    private lateinit var vm: DrawStageViewModel
    private var id = 0L
    private var caption: IgResult<IgMedia> = IgResult.Err(IgError.Offline)

    private val vault = object : SeedVault {
        override fun seal(giveawayId: Long, seed: ByteArray) = seed.reversedArray()

        override fun open(giveawayId: Long, sealed: ByteArray) = sealed.reversedArray()
    }
    private val signer = object : RecordSigner {
        override fun sign(record: ByteArray) = RecordSigner.Signature(byteArrayOf(1), byteArrayOf(2), "fp")
    }
    private val instagram = object : InstagramRepository {
        override suspend fun mediaPage(cursor: String?, limit: Int): IgResult<MediaPage> = error("unused")
        override suspend fun mediaById(mediaId: String) = caption
        override suspend fun commentsPage(mediaId: String, cursor: String?, limit: Int): IgResult<CommentPage> =
            error("unused")
        override suspend fun repliesPage(commentId: String, cursor: String?): IgResult<ReplyCountPage> =
            error("unused")
    }

    private fun media(captionText: String?) =
        IgMedia("m1", MediaKind.IMAGE, false, null, captionText, closesAt, 5, null)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(app, GiveawayDatabase::class.java).allowMainThreadQueries().build()
        giveaways = DefaultGiveawayRepository(db, DefaultSettingsRepository(db.settingsDao()), clock)
    }

    @After
    fun tearDown() {
        if (::vm.isInitialized) vm.viewModelScope.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    /** A reviewed giveaway with [people] valid entrants, asking for [winners] and [alternates]. */
    private fun reviewed(people: Int, winners: Int = 3, alternates: Int = 2) = runBlocking {
        val rules = Rules(1, null, null, true, true, true, closesAt, winners, alternates)
        id = giveaways.createDraft(media(null), "Win a tote bag!", "shop", rules)
        giveaways.saveCommitment(id, Commit.commitHash(seed), vault.seal(id, seed))
        giveaways.commit(id)
        giveaways.transition(id, GiveawayStatus.IMPORTING)
        db.commentDao().insertAll(
            List(people) { i -> CommentEntity(id, "c$i", "user$i", "In @friend", closesAt.minusSeconds(60L + i)) },
        )
        EntryBuilder(db).rebuild(id)
    }

    private var freeBytes = Long.MAX_VALUE
    private val drawn = mutableListOf<Boolean>()
    private var alreadyDrawn = 0

    private fun show() {
        vm = DrawStageViewModel(
            SavedStateHandle(route = DrawRoute(id)),
            giveaways,
            EntryRepository(db, EntryBuilder(db), clock),
            instagram,
            DrawService(db, vault, signer, clock),
            DefaultSettingsRepository(db.settingsDao()),
            { freeBytes },
        )
        compose.setContent {
            GiveawayTheme {
                DrawStageScreen(
                    onBack = {},
                    onDrawn = { drawn += it },
                    onAlreadyDrawn = { alreadyDrawn++ },
                    viewModel = vm,
                )
            }
        }
    }

    private fun text(id: Int) = compose.onNodeWithText(app.getString(id))

    private fun awaitText(text: String) = compose.waitUntil(WAIT_MS) {
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun aCaptionWithTheCodeShowsTheMatchChip() {
        reviewed(people = 8)
        caption = IgResult.Ok(media("Win! #draw ${Commit.commitHash(seed)}"))
        show()
        awaitText(app.getString(R.string.draw_caption_found))
        text(R.string.draw_winners).assertIsEnabled()
    }

    @Test
    fun aMissingCodeWarnsButStillAllowsTheDraw() {
        reviewed(people = 8)
        caption = IgResult.Ok(media("Win a tote bag!"))
        show()
        awaitText(app.getString(R.string.draw_caption_missing_title))
        text(R.string.draw_winners).assertIsEnabled()
    }

    @Test
    fun offlineMeansNotChecked() {
        reviewed(people = 8)
        show()
        awaitText(app.getString(R.string.draw_caption_unchecked_title))
    }

    @Test
    fun fewerPeopleThanPlacesIsExplained() {
        reviewed(people = 3, winners = 3, alternates = 2)
        show()
        awaitText(app.getString(R.string.draw_fewer_title))
    }

    @Test
    fun shortStorageWarnsWhileRecordingIsOn() {
        reviewed(people = 8)
        freeBytes = 1_000_000
        show()
        awaitText(app.getString(R.string.draw_low_storage_title))
        // The draw itself is still allowed: only the video needs the space.
        compose.onNodeWithText(app.getString(R.string.draw_record)).performClick()
        compose.waitUntil(WAIT_MS) {
            compose.onAllNodesWithText(app.getString(R.string.draw_low_storage_title)).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun aTestDrawIsLabelledAndDoesNotCount() {
        reviewed(people = 8)
        show()
        awaitText(app.getString(R.string.draw_caption_unchecked_title))
        text(R.string.draw_test_first).performClick()
        awaitText(app.getString(R.string.draw_test_title))
        assertEquals(GiveawayStatus.REVIEW, runBlocking { giveaways.get(id)?.status })
        assertEquals(null, runBlocking { db.drawDao().realDraw(id) })
    }

    @Test
    fun drawingSavesTheResultFirstThenMovesOn() {
        reviewed(people = 8)
        caption = IgResult.Ok(media("#draw ${Commit.commitHash(seed)}"))
        show()
        awaitText(app.getString(R.string.draw_caption_found))
        text(R.string.draw_winners).performClick()
        compose.waitUntil(WAIT_MS) { drawn.isNotEmpty() }
        assertEquals(listOf(true), drawn)
        val draw = runBlocking { db.drawDao().realDraw(id) }!!
        assertEquals(CaptionCheck.FOUND, draw.captionCheck)
        assertEquals(GiveawayStatus.DRAWN, runBlocking { giveaways.get(id)?.status })
    }

    @Test
    fun reopeningAfterTheDrawGoesToTheResult() {
        reviewed(people = 8)
        runBlocking {
            DrawService(db, vault, signer, clock).realDraw(id, DrawChecks(CaptionCheck.FOUND, false))
        }
        show()
        compose.waitUntil(WAIT_MS) { alreadyDrawn == 1 }
    }

    @Test
    fun screenshotStage() {
        reviewed(people = 128)
        caption = IgResult.Ok(media("#draw ${Commit.commitHash(seed)}"))
        show()
        awaitText(app.getString(R.string.draw_caption_found))
        compose.onRoot().captureRoboImage("src/test/screenshots/s11_draw_stage.png")
    }

    @Test
    @Config(qualifiers = "ar-w390dp-h1000dp-xhdpi")
    fun screenshotArabicFewer() {
        reviewed(people = 2, winners = 3, alternates = 2)
        caption = IgResult.Ok(media("no code"))
        show()
        awaitText(app.getString(R.string.draw_fewer_title))
        compose.onRoot().captureRoboImage("src/test/screenshots/s11_draw_stage_arabic.png")
    }

    private companion object {
        const val WAIT_MS = 15_000L
    }
}
