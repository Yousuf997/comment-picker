package app.giveaway.feature.create

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.testing.invoke
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.db.ImportStateEntity
import app.giveaway.core.data.giveaway.DefaultGiveawayRepository
import app.giveaway.core.data.importing.CommentImporter
import app.giveaway.core.data.importing.ImportRepository
import app.giveaway.core.data.importing.ImportWork
import app.giveaway.core.data.settings.DefaultSettingsRepository
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.draw.Rules
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.TimeZone

/** C-18 acceptance: a UI test per S9 state, the deadline gate, partial imports and Review entries. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h1100dp-xhdpi")
class ImportCommentsTest {

    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val closesAt = Instant.parse("2026-10-08T10:00:00Z")
    private var clock = Clock.fixed(closesAt.plus(Duration.ofHours(1)), ZoneOffset.UTC)
    private val systemZone = TimeZone.getDefault()
    private lateinit var db: GiveawayDatabase
    private lateinit var giveaways: DefaultGiveawayRepository
    private var id = 0L

    private val started = mutableListOf<Long>()
    private val active = MutableStateFlow(false)
    private val work = object : ImportWork {
        override fun start(giveawayId: Long) {
            started += giveawayId
        }

        override fun observeActive(giveawayId: Long): Flow<Boolean> = active

        override fun cancel(giveawayId: Long) = Unit
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        db = Room.inMemoryDatabaseBuilder(app, GiveawayDatabase::class.java).allowMainThreadQueries().build()
        giveaways = DefaultGiveawayRepository(db, DefaultSettingsRepository(db.settingsDao()), clock)
        id = runBlocking {
            val rules = Rules(1, null, null, true, true, true, closesAt, 1, 2)
            val draft = giveaways.createDraft(media("p1"), "Win a tote bag!", "shop", rules)
            giveaways.saveCommitment(draft, "a".repeat(64), ByteArray(48))
            giveaways.commit(draft)
            draft
        }
    }

    @After
    fun tearDown() {
        // Stop the screen's flows first, or Room re-queries a closed database.
        if (::vm.isInitialized) vm.viewModelScope.cancel()
        // Accepting a partial import writes in a transaction the await can outrun; let it end before closing.
        runBlocking { db.withTransaction {} }
        db.close()
        Dispatchers.resetMain()
        TimeZone.setDefault(systemZone)
    }

    private fun state(
        imported: Int,
        expected: Int,
        error: String? = null,
        retries: Int = 0,
        finished: Boolean = false,
    ) =
        runBlocking {
            db.importStateDao().upsert(
                ImportStateEntity(
                    giveawayId = id,
                    nextCursor = if (finished) null else "next",
                    pagesFetched = imported / 50 + 1,
                    commentsFetched = imported,
                    expectedCount = expected,
                    lastError = error,
                    updatedAt = clock.instant(),
                    failedRetries = retries,
                    acceptedPartial = false,
                    repliesCounted = 0,
                ),
            )
        }

    private lateinit var vm: ImportCommentsViewModel

    private fun show() {
        vm = ImportCommentsViewModel(
            SavedStateHandle(route = ImportCommentsRoute(id)),
            giveaways,
            ImportRepository(db, work, clock),
            clock,
        )
        compose.setContent {
            GiveawayTheme { ImportCommentsScreen(onBack = {}, onReviewEntries = { reviewed++ }, viewModel = vm) }
        }
    }

    private var reviewed = 0

    private fun text(id: Int) = compose.onNodeWithText(app.getString(id))

    private fun awaitText(id: Int) = compose.waitUntil(WAIT_MS) {
        compose.onAllNodesWithText(app.getString(id)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun openingAfterTheDeadlineStartsTheImport() {
        active.value = true
        show()
        compose.waitUntil(WAIT_MS) { started.isNotEmpty() }
        assertEquals(listOf(id), started)
        text(R.string.import_review).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun beforeTheDeadlineNothingIsImported() {
        clock = Clock.fixed(closesAt.minusSeconds(60), ZoneOffset.UTC)
        show()
        awaitText(R.string.import_not_yet_title)
        assertTrue(started.isEmpty())
    }

    @Test
    fun offlineIsExplainedAndRetryResumes() {
        state(imported = 850, expected = 2_400, error = CommentImporter.CODE_OFFLINE, retries = 1)
        show()
        awaitText(R.string.import_offline_title)
        compose.onNodeWithText("850 of 2,400 comments").assertExists()
        text(R.string.import_retry).performClick()
        compose.waitUntil(WAIT_MS) { started.size == 2 }
        assertEquals(listOf(id, id), started)
    }

    @Test
    fun noCommentsReturnedIsExplainedWithRetry() {
        state(imported = 0, expected = 240, error = CommentImporter.NONE_RETURNED, finished = true)
        show()
        awaitText(R.string.import_none_title)
        text(R.string.import_retry).assertExists()
        text(R.string.import_accept_partial).assertDoesNotExist()
        text(R.string.import_unavailable_title).assertDoesNotExist()
    }

    @Test
    fun aSettledImportExplainsTheCommentsInstagramDoesntShare() {
        state(imported = 2_180, expected = 2_400, error = CommentImporter.SETTLED, finished = true)
        show()
        awaitText(R.string.import_unavailable_title)
        val body = app.resources.getQuantityString(R.plurals.import_unavailable_body, 220, "220")
        compose.onNodeWithText(body, useUnmergedTree = true).assertExists()
        text(R.string.import_accept_partial).assertDoesNotExist()
    }

    @Test
    fun afterThreeShortImportsThePartialImportCanBeAccepted() {
        state(imported = 2_310, expected = 2_400, error = CommentImporter.MISMATCH, retries = 3, finished = true)
        show()
        awaitText(R.string.import_mismatch_title)
        text(R.string.import_accept_partial).performScrollTo().performClick()
        text(R.string.import_partial_confirm).performClick()
        compose.waitUntil(WAIT_MS) { runBlocking { db.importStateDao().get(id)?.acceptedPartial == true } }
        // The worker is started again to filter what was imported.
        assertEquals(id, started.last())
    }

    @Test
    fun aDeletedPostOffersThePartialImportStraightAway() {
        state(imported = 120, expected = 400, error = CommentImporter.CODE_MEDIA_NOT_FOUND, retries = 1)
        show()
        awaitText(R.string.import_deleted_title)
        text(R.string.import_accept_partial).assertExists()
    }

    @Test
    fun reviewOpensOnceEntriesAreFiltered() {
        state(imported = 400, expected = 400, finished = true)
        runBlocking { giveaways.transition(id, GiveawayStatus.IMPORTING) }
        runBlocking { giveaways.transition(id, GiveawayStatus.REVIEW) }
        show()
        val review = compose.onNodeWithText(app.getString(R.string.import_review))
        compose.waitUntil(WAIT_MS) { runCatching { review.assertIsEnabled() }.isSuccess }
        review.performScrollTo().performClick()
        assertEquals(1, reviewed)
    }

    @Test
    fun screenshotRunning() {
        active.value = true
        state(imported = 1_850, expected = 2_400)
        show()
        compose.waitUntil(WAIT_MS) {
            compose.onAllNodesWithText("1,850 of 2,400 comments").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/s9_import_running.png")
    }

    @Test
    @Config(qualifiers = "ar-w390dp-h1100dp-xhdpi")
    fun screenshotArabicMismatch() {
        state(imported = 2_310, expected = 2_400, error = CommentImporter.MISMATCH, retries = 3, finished = true)
        show()
        awaitText(R.string.import_mismatch_title)
        compose.onRoot().captureRoboImage("src/test/screenshots/s9_import_arabic_mismatch.png")
    }

    private companion object {
        const val WAIT_MS = 15_000L
    }
}
