package app.giveaway.feature.create

import android.app.Application
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.testing.invoke
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import app.giveaway.core.data.db.CommentEntity
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.giveaway.DefaultGiveawayRepository
import app.giveaway.core.data.importing.ImportRepository
import app.giveaway.core.data.importing.ImportWork
import app.giveaway.core.data.settings.DefaultSettingsRepository
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.draw.Rules
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
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

/** S9's "View comments": the comments imported so far, read-only, growing while the import runs. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h1000dp-xhdpi")
class ImportedCommentsTest {

    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val closesAt = Instant.parse("2026-10-08T10:00:00Z")
    private val clock = Clock.fixed(closesAt.plusSeconds(3_600), ZoneOffset.UTC)
    private val systemZone = TimeZone.getDefault()
    private lateinit var db: GiveawayDatabase
    private lateinit var vm: ImportedCommentsViewModel
    private var id = 0L

    private val work = object : ImportWork {
        override fun start(giveawayId: Long) = Unit
        override fun observeActive(giveawayId: Long) = flowOf(true)
        override fun cancel(giveawayId: Long) = Unit
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // Times show in the phone's zone; pin it so screenshots match on every machine.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        db = Room.inMemoryDatabaseBuilder(app, GiveawayDatabase::class.java).allowMainThreadQueries().build()
        val giveaways = DefaultGiveawayRepository(db, DefaultSettingsRepository(db.settingsDao()), clock)
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
        runBlocking { db.withTransaction {} }
        db.close()
        Dispatchers.resetMain()
        TimeZone.setDefault(systemZone)
    }

    /** Stores comments a minute apart, the first ten minutes before entries close. */
    private fun comments(vararg rows: Pair<String, String>) = runBlocking {
        db.commentDao().insertAll(
            rows.mapIndexed { i, (user, text) ->
                CommentEntity(id, "c$i", user, text, closesAt.minusSeconds(600L - i * 60L))
            },
        )
    }

    private fun show() {
        vm = ImportedCommentsViewModel(
            SavedStateHandle(route = ImportedCommentsRoute(id)),
            ImportRepository(db, work, clock),
        )
        compose.setContent { GiveawayTheme { ImportedCommentsScreen(onBack = {}, viewModel = vm) } }
    }

    private fun awaitRow(commentId: String) = compose.waitUntil(WAIT_MS) {
        compose.onAllNodesWithTag("comments:row:$commentId").fetchSemanticsNodes().isNotEmpty()
    }

    private fun awaitText(text: String) = compose.waitUntil(WAIT_MS) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun everyImportedCommentIsListedWithItsText() {
        comments("ana" to "@bob @cid #win", "bob" to "Me please!")
        show()
        awaitRow("c1")
        compose.onNodeWithText(app.resources.getQuantityString(R.plurals.comments_count, 2, "2")).assertExists()
        compose.onNodeWithTag("comments:row:c0").assertTextContains("@bob @cid #win")
        compose.onNodeWithTag("comments:row:c1").assertTextContains("Me please!")
    }

    @Test
    fun nothingImportedYetSaysSo() {
        show()
        awaitText(app.getString(R.string.comments_empty_title))
    }

    @Test
    fun commentsAppearAsTheyAreImported() {
        show()
        awaitText(app.getString(R.string.comments_empty_title))
        comments("ana" to "Count me in")
        awaitRow("c0")
    }

    @Test
    fun screenshotLight() {
        comments(
            "ana" to "@bob @cid #win",
            "bob" to "Me please! @dan",
            "cat" to "Love this bag! @eve @fay",
            "dan" to "In! @gus",
        )
        show()
        awaitRow("c3")
        compose.onRoot().captureRoboImage("src/test/screenshots/s9_comments_light.png")
    }

    @Test
    @Config(qualifiers = "ar-w390dp-h1000dp-xhdpi")
    fun screenshotArabic() {
        comments("noor" to "@sara @huda أنا مشتركة", "omar" to "بالتوفيق للجميع @ali", "lina" to "Count me in @rami")
        show()
        awaitRow("c2")
        compose.onRoot().captureRoboImage("src/test/screenshots/s9_comments_arabic.png")
    }

    private companion object {
        const val WAIT_MS = 15_000L
    }
}
