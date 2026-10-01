package app.giveaway.feature.create

import android.app.Application
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.testing.invoke
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giveaway.core.data.db.CommentEntity
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.giveaway.DefaultGiveawayRepository
import app.giveaway.core.data.importing.EntryBuilder
import app.giveaway.core.data.review.EntryRepository
import app.giveaway.core.data.settings.DefaultSettingsRepository
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.draw.CanonicalEntryList
import app.giveaway.draw.ExclusionReason
import app.giveaway.draw.Rules
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
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
import java.util.TimeZone

/**
 * C-20 acceptance: S10 lists, filters and edits entries; zero valid entries blocks the draw; the export is the
 * canonical list's bytes.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h1000dp-xhdpi")
class ReviewEntriesTest {

    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val closesAt = Instant.parse("2026-10-08T10:00:00Z")
    private val clock = Clock.fixed(closesAt.plusSeconds(3_600), ZoneOffset.UTC)
    private val systemZone = TimeZone.getDefault()
    private lateinit var db: GiveawayDatabase
    private lateinit var giveaways: DefaultGiveawayRepository
    private lateinit var vm: ReviewEntriesViewModel
    private var id = 0L

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
            giveaways.transition(draft, GiveawayStatus.IMPORTING)
            draft
        }
    }

    @After
    fun tearDown() {
        if (::vm.isInitialized) vm.viewModelScope.cancel()
        db.close()
        Dispatchers.resetMain()
        TimeZone.setDefault(systemZone)
    }

    private fun comments(vararg rows: Pair<String, String>) = runBlocking {
        db.commentDao().insertAll(
            rows.mapIndexed { i, (user, text) ->
                CommentEntity(id, "c$i", user, text, closesAt.minusSeconds((rows.size - i) * 60L))
            },
        )
        EntryBuilder(db).rebuild(id)
    }

    private fun show(onContinue: () -> Unit = {}) {
        vm = ReviewEntriesViewModel(
            SavedStateHandle(route = ReviewEntriesRoute(id)),
            giveaways,
            EntryRepository(db, EntryBuilder(db), clock),
            searchDebounceMs = 0,
        )
        compose.setContent {
            GiveawayTheme { ReviewEntriesScreen(onBack = {}, onContinueToDraw = onContinue, viewModel = vm) }
        }
    }

    private fun text(id: Int) = compose.onNodeWithText(app.getString(id))

    private fun await(condition: () -> Boolean) = compose.waitUntil(WAIT_MS, condition)

    private fun shown(text: String) =
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun entry(commentId: String) = runBlocking { db.entryDao().get(id, commentId)!! }

    @Test
    fun rowsShowTheReasonAndFiltersNarrowThem() {
        comments("amy" to "In! @bob", "bob" to "Love it", "amy" to "Again @cat")
        show()
        await { shown(app.getString(R.string.review_reason_too_few_mentions)) }
        compose.onNodeWithText(app.getString(R.string.review_reason_duplicate)).assertExists()
        // "Valid" is also a tile and a status tag: pick the filter tab.
        val tab = hasText(app.getString(R.string.review_filter_valid)) and
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        compose.onNode(tab).performClick()
        await { compose.onAllNodesWithTag("review:row:c1").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("review:row:c0").assertExists()
    }

    @Test
    fun searchFindsHandlesAndText() {
        comments("amy" to "In! @bob", "bob" to "Love it @amy", "cat" to "Me too @amy")
        show()
        await { shown("Me too") }
        compose.onNodeWithTag("review:search").performTextInput("love")
        await { compose.onAllNodesWithTag("review:row:c2").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("review:row:c1").assertExists()
    }

    @Test
    fun excludingNeedsAReasonAndIsSaved() {
        comments("amy" to "In! @bob", "bob" to "Me too @amy")
        show()
        await { shown("Me too") }
        compose.onNodeWithTag("review:row:c0").performClick()
        text(R.string.review_exclude).performClick()
        text(R.string.review_exclude_confirm).assertIsNotEnabled()
        compose.onNodeWithTag("review:note").performTextInput("Fake account")
        text(R.string.review_exclude_confirm).performClick()
        await { entry("c0").exclusionReason == ExclusionReason.MANUAL }
        assertEquals("Fake account", entry("c0").manualNote)
    }

    @Test
    fun aDuplicateCannotBeIncluded() {
        comments("amy" to "In! @bob", "amy" to "Again @bob")
        show()
        await { shown("Again") }
        compose.onNodeWithTag("review:row:c1").performClick()
        text(R.string.review_cannot_include).assertExists()
    }

    @Test
    fun zeroValidEntriesBlocksTheDraw() {
        comments("bob" to "Love it")
        show()
        await { shown(app.getString(R.string.review_no_valid_title)) }
        text(R.string.review_continue).assertIsNotEnabled()
    }

    @Test
    fun continueOpensTheDrawWhenSomeoneIsValid() {
        comments("amy" to "In! @bob")
        var continued = 0
        show { continued++ }
        val button = compose.onNodeWithText(app.getString(R.string.review_continue))
        await { runCatching { button.assertIsEnabled() }.isSuccess }
        button.performClick()
        assertEquals(1, continued)
    }

    @Test
    fun theExportIsTheCanonicalListBytes() {
        comments("Zoe" to "In @amy", "bob" to "Me @amy", "cat_99" to "Yes @bob")
        show()
        val bytes = runBlocking { vm.exportBytes() }
        assertArrayEquals(CanonicalEntryList.of(listOf("Zoe", "bob", "cat_99")).text.toByteArray(), bytes)
        assertEquals("bob\ncat_99\nzoe", String(bytes))
    }

    @Test
    fun screenshotLight() {
        comments(
            "amy.designs" to "Count me in! @bob @cat",
            "bob" to "Love this",
            "amy.designs" to "Again @dan",
            "noor" to "Tagging @sara",
        )
        show()
        await { shown("Tagging") }
        compose.onRoot().captureRoboImage("src/test/screenshots/s10_review_light.png")
    }

    @Test
    @Config(qualifiers = "ar-w390dp-h1000dp-xhdpi")
    fun screenshotArabicSheet() {
        comments("noor" to "أنا مشاركة @sara", "bob" to "Love this")
        show()
        await { shown("Love this") }
        compose.onNodeWithTag("review:row:c1").performClick()
        await { shown(app.getString(R.string.review_add_blocklist)) }
        compose.waitForIdle()
        // The sheet is its own window: capture the whole screen, every window included.
        captureScreenRoboImage("src/test/screenshots/s10_review_arabic_sheet.png")
    }

    private companion object {
        const val WAIT_MS = 15_000L
    }
}
