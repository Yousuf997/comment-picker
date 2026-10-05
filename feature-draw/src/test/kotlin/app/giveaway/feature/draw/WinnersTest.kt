package app.giveaway.feature.draw

import android.app.Application
import android.net.Uri
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.testing.invoke
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giveaway.core.data.db.CaptionCheck
import app.giveaway.core.data.db.CommentEntity
import app.giveaway.core.data.db.ConfirmationStatus
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.db.GiveawayStatus
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
import app.giveaway.core.instagram.api.MediaKind
import app.giveaway.core.media.VideoGallery
import app.giveaway.core.media.VideoInfo
import app.giveaway.draw.Commit
import app.giveaway.draw.Rules
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
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
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.TimeZone

/** M-01 acceptance: confirm, replace with a reason, the alternates-used state, and the certificate step. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h1100dp-xhdpi")
class WinnersTest {

    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val closesAt = Instant.parse("2026-10-08T10:00:00Z")
    private val clock = Clock.fixed(closesAt.plusSeconds(7_200), ZoneOffset.UTC)
    private val seed = ByteArray(Commit.SEED_BYTES) { (it * 3).toByte() }
    private val systemZone = TimeZone.getDefault()
    private lateinit var db: GiveawayDatabase
    private lateinit var repository: WinnerRepository
    private lateinit var vm: WinnersViewModel
    private var id = 0L
    private var certificates = 0
    private var redraws = 0

    private val vault = object : SeedVault {
        override fun seal(giveawayId: Long, seed: ByteArray) = seed.reversedArray()

        override fun open(giveawayId: Long, sealed: ByteArray) = sealed.reversedArray()
    }
    /** No recording in these tests, so the gallery is never used. */
    private val noGallery = object : VideoGallery {
        override suspend fun save(source: File, album: String, displayName: String): Uri = error("unused")

        override suspend fun describe(file: File): VideoInfo? = null
    }
    private val signer = object : RecordSigner {
        override fun sign(record: ByteArray) = RecordSigner.Signature(byteArrayOf(1), byteArrayOf(2), "fp")
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        db = Room.inMemoryDatabaseBuilder(app, GiveawayDatabase::class.java).allowMainThreadQueries().build()
        repository = WinnerRepository(db, clock)
        id = runBlocking {
            val giveaways = DefaultGiveawayRepository(db, DefaultSettingsRepository(db.settingsDao()), clock)
            val media = IgMedia("m1", MediaKind.IMAGE, false, null, null, closesAt, 0, null)
            val rules = Rules(1, null, null, true, true, true, closesAt, 2, 1)
            val giveawayId = giveaways.createDraft(media, "Win a tote bag!", "shop", rules)
            giveaways.saveCommitment(giveawayId, Commit.commitHash(seed), vault.seal(giveawayId, seed))
            giveaways.commit(giveawayId)
            giveaways.transition(giveawayId, GiveawayStatus.IMPORTING)
            val users = listOf("amy.designs", "bob", "cat", "dan", "eve")
            db.commentDao().insertAll(
                users.mapIndexed { i, user ->
                    CommentEntity(giveawayId, "c$i", user, "Love this! @friend", closesAt.minusSeconds(600L - i))
                },
            )
            EntryBuilder(db).rebuild(giveawayId)
            DrawService(db, vault, signer, clock).realDraw(giveawayId, DrawChecks(CaptionCheck.FOUND, false))
            giveawayId
        }
    }

    @After
    fun tearDown() {
        if (::vm.isInitialized) vm.viewModelScope.cancel()
        db.close()
        Dispatchers.resetMain()
        TimeZone.setDefault(systemZone)
    }

    private fun show() {
        vm = WinnersViewModel(
            SavedStateHandle(route = WinnersRoute(id)),
            repository,
            MediaRepository(app, db, clock),
            noGallery,
        )
        compose.setContent {
            GiveawayTheme {
                WinnersScreen(onCreateCertificate = { certificates++ }, onRedraw = { redraws++ }, viewModel = vm)
            }
        }
        compose.waitUntil(WAIT_MS) { shown(R.string.winners_confirm) }
    }

    private fun shown(id: Int) = compose.onAllNodesWithText(app.getString(id)).fetchSemanticsNodes().isNotEmpty()

    private fun view() = runBlocking { repository.observe(id).first()!! }

    @Test
    fun redrawAsksFirst() {
        show()
        compose.onNodeWithTag("winners:redraw").performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.redraw_body)).assertExists()
        assertEquals(0, redraws)
        compose.onNodeWithTag("redraw:confirm").performClick()
        assertEquals(1, redraws)
    }

    @Test
    fun confirmingShowsTheConfirmedChip() {
        show()
        compose.onAllNodesWithText(app.getString(R.string.winners_confirm)).onFirst().performClick()
        compose.waitUntil(WAIT_MS) { view().winners.first().status == ConfirmationStatus.CONFIRMED }
        compose.waitUntil(WAIT_MS) { shown(R.string.winners_confirmed) }
    }

    @Test
    fun replacingAsksForAReasonAndUsesTheAlternate() {
        show()
        val alternate = view().alternates.single()
        val place = view().winners.first().position
        compose.onAllNodesWithText(app.getString(R.string.winners_replace)).onFirst().performClick()
        assertEquals(place, vm.replacing.value)
        // A dialog with a text field never lets Robolectric go idle, so the reason goes in through the ViewModel,
        // which closes the dialog. The repository tests cover that a reason is required.
        vm.replace(place, "Doesn't follow")
        compose.waitUntil(WAIT_MS) { view().winners.first().username == alternate }
        compose.waitUntil(WAIT_MS) { shown(R.string.winners_no_alternates_title) }
        // With no alternates left, Replace is off but Confirm still works.
        compose.onAllNodesWithText(app.getString(R.string.winners_replace)).onFirst().assertIsNotEnabled()
    }

    @Test
    fun createCertificateAsksFirstBecauseItFixesTheResult() {
        show()
        val create = compose.onNodeWithText(app.getString(R.string.winners_create_certificate))
        create.performClick()
        // Both winners are still pending, and the dialog says so (plan A28).
        val pending = app.resources.getQuantityString(R.plurals.certificate_make_pending, 2, "2")
        compose.onNodeWithText(pending).assertExists()
        compose.onNodeWithText(app.getString(R.string.certificate_make_cancel)).performClick()
        assertEquals(0, certificates)
        create.performClick()
        compose.onNodeWithTag("certificate:make_confirm").performClick()
        assertEquals(1, certificates)
    }

    @Test
    fun screenshotLight() {
        show()
        compose.onRoot().captureRoboImage("src/test/screenshots/s14_winners.png")
    }

    @Test
    @Config(qualifiers = "ar-w390dp-h1100dp-xhdpi")
    fun screenshotArabicAfterReplacing() {
        runBlocking {
            val first = repository.observe(id).first()!!.winners.first()
            repository.replace(id, first.position, "لا يتابع الحساب")
        }
        show()
        compose.onRoot().captureRoboImage("src/test/screenshots/s14_winners_arabic.png")
    }

    private companion object {
        const val WAIT_MS = 15_000L
    }
}
