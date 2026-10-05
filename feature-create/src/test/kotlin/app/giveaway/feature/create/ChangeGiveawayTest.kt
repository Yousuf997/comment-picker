package app.giveaway.feature.create

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.testing.invoke
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.giveaway.DefaultGiveawayRepository
import app.giveaway.core.data.giveaway.GiveawayEditor
import app.giveaway.core.data.giveaway.WizardProgress
import app.giveaway.core.data.importing.EntryBuilder
import app.giveaway.core.data.importing.ImportWork
import app.giveaway.core.data.settings.DefaultSettingsRepository
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.draw.Rules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** X-2, X-3: an existing giveaway's rules and post change from the step bar (plan A31, A32). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h1600dp-xhdpi")
class ChangeGiveawayTest {

    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val closesAt = Instant.parse("2026-10-08T10:00:00Z")
    private val clock = Clock.fixed(closesAt.minusSeconds(86_400), ZoneOffset.UTC)
    private val rules = Rules(1, null, null, true, true, true, closesAt, 1, 2)
    private lateinit var db: GiveawayDatabase
    private lateinit var giveaways: DefaultGiveawayRepository
    private lateinit var editor: GiveawayEditor
    private val scheduled = mutableListOf<Pair<Long, Instant>>()
    private val cancelled = mutableListOf<Long>()
    private val viewModels = mutableListOf<ViewModel>()
    private var id = 0L

    private val work = object : ImportWork {
        override fun start(giveawayId: Long) = Unit

        override fun observeActive(giveawayId: Long) = flowOf(false)

        override fun cancel(giveawayId: Long) {
            cancelled += giveawayId
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(app, GiveawayDatabase::class.java).allowMainThreadQueries().build()
        giveaways = DefaultGiveawayRepository(db, DefaultSettingsRepository(db.settingsDao()), clock)
        editor = GiveawayEditor(db, giveaways, EntryBuilder(db), { gid, at -> scheduled += gid to at }, work)
        id = runBlocking {
            val draft = giveaways.createDraft(media("p1"), "Win a tote bag!", "shop", rules)
            giveaways.saveCommitment(draft, "a".repeat(64), ByteArray(48))
            giveaways.commit(draft)
            draft
        }
    }

    @After
    fun tearDown() {
        viewModels.forEach { it.viewModelScope.cancel() }
        db.close()
        Dispatchers.resetMain()
    }

    private fun status() = runBlocking { giveaways.get(id)!!.status }

    /** Walks the giveaway on to [to] through the states the draw passes. */
    private fun moveTo(to: GiveawayStatus) = runBlocking {
        val path = listOf(GiveawayStatus.IMPORTING, GiveawayStatus.REVIEW, GiveawayStatus.DRAWN)
        for (next in path) {
            giveaways.transition(id, next)
            if (next == to) break
        }
    }

    private fun editRules() =
        EditRulesViewModel(SavedStateHandle(route = EditRulesRoute(id)), giveaways, editor, clock).also {
            viewModels += it
            await { it.state.value.rules != null }
        }

    private fun changePost() =
        ChangePostViewModel(SavedStateHandle(route = ChangePostRoute(id)), giveaways, editor).also {
            viewModels += it
            await { it.state.value.currentMediaId != null }
        }

    /** Room runs queries on its own threads, so loading and saving finish a moment later. */
    private fun await(condition: () -> Boolean) = compose.waitUntil(WAIT_MS, condition)

    private fun mediaId() = runBlocking { giveaways.get(id)!!.igMediaId }

    private fun showEditRules(vm: EditRulesViewModel, saved: MutableList<Int>) = compose.setContent {
        GiveawayTheme { EditRulesScreen(onBack = {}, onSaved = { saved += it }, viewModel = vm) }
    }

    private fun save() =
        compose.onNodeWithText(app.getString(R.string.edit_rules_save)).performScrollTo().performClick()

    /** The dialog opens in its own window: wait for it, then find its body node by node. */
    private fun awaitClearWinners(body: Int) {
        await { compose.onAllNodesWithTag("clear_winners:confirm").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(app.getString(body), useUnmergedTree = true).assertExists()
    }

    @Test
    fun theFormStartsFromTheSavedRules() {
        val vm = editRules()
        val form = vm.state.value.rules!!.form
        assertEquals(RulesForm.from(rules), form)
        assertEquals("Win a tote bag!", vm.state.value.title)
    }

    @Test
    fun savingANewDeadlineMovesTheCheckAndReturnsToTheCode() {
        val vm = editRules()
        val saved = mutableListOf<Int>()
        showEditRules(vm, saved)
        val later = closesAt.plusSeconds(86_400)
        vm.onFormChange { it.copy(closesAt = later, winners = 3) }
        save()
        await { saved.isNotEmpty() }
        assertEquals(listOf(WizardProgress.CODE), saved)
        assertEquals(3, runBlocking { giveaways.rules(id)!!.winnersCount })
        assertEquals(listOf(id to later), scheduled)
    }

    @Test
    fun afterTheDrawSavingAsksFirstThenClearsTheWinners() {
        moveTo(GiveawayStatus.DRAWN)
        val vm = editRules()
        val saved = mutableListOf<Int>()
        showEditRules(vm, saved)
        vm.onFormChange { it.copy(winners = 2) }
        save()
        awaitClearWinners(R.string.clear_winners_rules_body)
        assertEquals(GiveawayStatus.DRAWN, status())
        compose.onNodeWithTag("clear_winners:confirm").performClick()
        await { saved.isNotEmpty() }
        assertEquals(listOf(WizardProgress.DRAW), saved)
        assertEquals(GiveawayStatus.REVIEW, status())
        assertEquals(2, runBlocking { giveaways.rules(id)!!.winnersCount })
    }

    @Test
    fun keepingTheWinnersSavesNothing() {
        moveTo(GiveawayStatus.DRAWN)
        val vm = editRules()
        val saved = mutableListOf<Int>()
        showEditRules(vm, saved)
        vm.onFormChange { it.copy(winners = 2) }
        save()
        awaitClearWinners(R.string.clear_winners_rules_body)
        compose.onNodeWithText(app.getString(R.string.dialog_cancel)).performClick()
        await { compose.onAllNodesWithTag("clear_winners:confirm").fetchSemanticsNodes().isEmpty() }
        assertTrue(saved.isEmpty())
        assertEquals(GiveawayStatus.DRAWN, status())
        assertEquals(1, runBlocking { giveaways.rules(id)!!.winnersCount })
    }

    @Test
    fun aPastDeadlineIsAcceptedOnceEntriesClosed() {
        moveTo(GiveawayStatus.REVIEW)
        val vm = editRules()
        vm.onFormChange { it.copy(closesAt = closesAt.minusSeconds(7 * 86_400L)) }
        assertTrue(vm.state.value.rules!!.errors.isEmpty())
    }

    @Test
    fun aNewPastDeadlineIsRefusedWhileEntriesAreOpen() {
        val vm = editRules()
        vm.onFormChange { it.copy(closesAt = clock.instant().minusSeconds(60)) }
        assertTrue(RulesError.CLOSES_IN_PAST in vm.state.value.rules!!.errors)
    }

    @Test
    fun beforeTheImportANewPostIsSavedWithoutAsking() {
        val vm = changePost()
        assertFalse(vm.state.value.clearsData)
        vm.onContinue(media("p2"))
        assertNull(vm.state.value.confirming)
        await { mediaId() == "p2" }
        assertEquals(GiveawayStatus.COMMITTED, status())
    }

    @Test
    fun afterTheImportANewPostIsConfirmedFirst() {
        moveTo(GiveawayStatus.REVIEW)
        val vm = changePost()
        assertTrue(vm.state.value.clearsData)
        vm.onContinue(media("p2"))
        assertEquals("p2", vm.state.value.confirming?.id)
        assertEquals("p1", mediaId())
        vm.confirm()
        await { status() == GiveawayStatus.COMMITTED }
        assertEquals("p2", mediaId())
        assertEquals(GiveawayStatus.COMMITTED, status())
        assertEquals(listOf(id), cancelled)
    }

    @Test
    fun theSamePostChangesNothing() {
        moveTo(GiveawayStatus.REVIEW)
        val vm = changePost()
        vm.onContinue(media("p1"))
        assertNull(vm.state.value.confirming)
        assertEquals(GiveawayStatus.REVIEW, status())
    }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
