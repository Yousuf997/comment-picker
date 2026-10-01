package app.giveaway.feature.create

import android.Manifest
import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.giveaway.DefaultGiveawayRepository
import app.giveaway.core.data.giveaway.DrawCommitments
import app.giveaway.core.data.giveaway.SeedVault
import app.giveaway.core.data.settings.DefaultSettingsRepository
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.draw.Commit
import app.giveaway.draw.Rules
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.TimeZone

/** C-14 acceptance: Done stays disabled until the box is ticked; the seed never appears in the semantics tree. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h1200dp-xhdpi")
class LockInTest {

    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val now = Instant.parse("2026-10-01T10:00:00Z")
    private var clock = Clock.fixed(now, ZoneOffset.UTC)
    private val systemZone = TimeZone.getDefault()
    private lateinit var db: GiveawayDatabase
    private lateinit var repository: DefaultGiveawayRepository
    private val seeds = mutableListOf<ByteArray>()
    private var seedsMade = 0
    private val scheduled = mutableListOf<Pair<Long, Instant>>()

    private val vault = object : SeedVault {
        override fun seal(giveawayId: Long, seed: ByteArray): ByteArray {
            seeds += seed.copyOf()
            return seed.reversedArray()
        }

        override fun open(giveawayId: Long, sealed: ByteArray): ByteArray = sealed.reversedArray()
    }

    private val closesAt = now.plus(Duration.ofDays(7))
    private val media = media("p1").copy(caption = "Win a tote bag!")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        db = Room.inMemoryDatabaseBuilder(app, GiveawayDatabase::class.java).allowMainThreadQueries().build()
        repository = DefaultGiveawayRepository(db, DefaultSettingsRepository(db.settingsDao()), clock)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
        TimeZone.setDefault(systemZone)
    }

    private fun draft(): Long = runBlocking {
        val rules = Rules(1, null, null, true, true, true, closesAt, 1, 2)
        repository.createDraft(media, "Win a tote bag!", "shop", rules)
    }

    private fun viewModel(id: Long) = LockInViewModel(
        SavedStateHandle(route = LockInDrawRoute(id)),
        repository,
        DrawCommitments(repository, vault) { ByteArray(Commit.SEED_BYTES) { i -> (i + seedsMade++).toByte() } },
        { giveawayId, at -> scheduled += giveawayId to at },
        clock,
    )

    private fun LockInViewModel.loaded() = runBlocking { state.first { it.drawCode != null } }

    private fun awaitCode() = compose.waitUntil(TIMEOUT_MS) {
        compose.onAllNodesWithTag("lock_in:code").fetchSemanticsNodes().isNotEmpty()
    }

    private fun text(id: Int) = compose.onNodeWithText(app.getString(id))

    private fun show(vm: LockInViewModel, onDone: () -> Unit = {}) =
        compose.setContent { GiveawayTheme { LockInScreen(onBack = {}, onDone = onDone, viewModel = vm) } }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }

    @Test
    fun doneIsDisabledUntilTheBoxIsTicked() {
        val id = draft()
        var done = 0
        show(viewModel(id)) { done++ }
        awaitCode()
        text(R.string.lock_in_done).performScrollTo().assertIsNotEnabled()
        text(R.string.lock_in_confirm).performScrollTo().performClick()
        text(R.string.lock_in_done).assertIsEnabled().performClick()
        compose.waitUntil(TIMEOUT_MS) { done == 1 }
        assertEquals(GiveawayStatus.COMMITTED, runBlocking { repository.get(id)?.status })
        assertEquals(listOf(id to closesAt), scheduled)
    }

    @Test
    fun theCodeIsTheCommitHashAndTheSeedIsNeverShown() {
        val id = draft()
        show(viewModel(id))
        awaitCode()
        val seedHex = seeds.single().joinToString("") { "%02x".format(it) }
        val code = Commit.drawCode(Commit.commitHash(seeds.single()))
        compose.onNodeWithTag("lock_in:code").assertExists()
        val texts = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode().let { root ->
            buildList {
                fun visit(node: androidx.compose.ui.semantics.SemanticsNode) {
                    node.config.getOrNull(SemanticsProperties.Text)?.forEach { add(it.text) }
                    node.config.getOrNull(SemanticsProperties.ContentDescription)?.forEach { add(it) }
                    node.children.forEach(::visit)
                }
                visit(root)
            }
        }
        assertTrue("the draw code is shown", texts.any { code in it })
        assertFalse("the seed must never reach the UI", texts.any { seedHex in it.lowercase() })
    }

    @Test
    fun reopeningShowsTheSameCodeAndKeepsItCommitted() {
        val id = draft()
        val first = viewModel(id)
        val code = first.loaded().drawCode
        first.onConfirmedChange(true)
        first.onDone()
        runBlocking { first.done.first() }
        val again = viewModel(id).loaded()
        assertEquals(code, again.drawCode)
        assertTrue("already committed, so already confirmed", again.confirmed)
        assertEquals(1, seeds.size)
    }

    @Test
    fun aDraftWhoseDeadlinePassedCannotBeCommitted() {
        val id = draft()
        clock = Clock.fixed(closesAt.plusSeconds(1), ZoneOffset.UTC)
        val vm = viewModel(id)
        vm.loaded()
        vm.onConfirmedChange(true)
        vm.onDone()
        runBlocking { vm.state.first { it.deadlinePassed } }
        assertEquals(GiveawayStatus.DRAFT, runBlocking { repository.get(id)?.status })
        assertTrue(scheduled.isEmpty())
    }

    @Test
    fun screenshotLight() {
        show(viewModel(draft()))
        awaitCode()
        compose.onRoot().captureRoboImage("src/test/screenshots/s8_lock_in_light.png")
    }

    @Test
    @Config(qualifiers = "ar-w390dp-h1200dp-xhdpi")
    fun screenshotArabic() {
        val vm = viewModel(draft())
        vm.loaded()
        vm.onConfirmedChange(true)
        show(vm)
        awaitCode()
        compose.onRoot().captureRoboImage("src/test/screenshots/s8_lock_in_arabic.png")
    }
}
