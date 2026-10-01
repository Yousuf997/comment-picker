package app.giveaway.feature.create

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.invoke
import androidx.test.core.app.ApplicationProvider
import app.giveaway.core.data.account.AccountRepository
import app.giveaway.core.data.account.SignInState
import app.giveaway.core.data.db.GiveawayEntity
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.db.GiveawaySummary
import app.giveaway.core.data.giveaway.GiveawayRepository
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.instagram.api.IgMedia
import app.giveaway.core.instagram.auth.AuthToken
import app.giveaway.core.instagram.auth.IgAccount
import app.giveaway.draw.Rules
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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

/** C-12 acceptance: S7 creates the draft with its rules; invalid forms are explained, not saved. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h1600dp-xhdpi")
class SetRulesTest {

    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val now = Instant.parse("2026-10-01T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val post = media("p1", comments = 240).copy(caption = "Win a tote bag!\nTag two friends")

    private val drafts = mutableListOf<Pair<IgMedia, Rules>>()
    private val titles = mutableListOf<String>()
    private val ruleUpdates = mutableListOf<Pair<Long, Rules>>()

    private val giveaways = object : GiveawayRepository {
        override fun observeSummaries(): Flow<List<GiveawaySummary>> = flowOf(emptyList())
        override suspend fun get(id: Long): GiveawayEntity? = null
        override suspend fun createDraft(media: IgMedia, title: String, ownerUsername: String, rules: Rules): Long {
            drafts += media to rules
            titles += "$title @$ownerUsername"
            return DRAFT_ID
        }
        override suspend fun saveRules(id: Long, rules: Rules) {
            ruleUpdates += id to rules
        }
        override suspend fun rules(id: Long): Rules? = null
        override suspend fun commit(id: Long, commitHash: String, encryptedSeed: ByteArray) = Unit
        override suspend fun transition(id: Long, to: GiveawayStatus) = Unit
    }

    private val accounts = object : AccountRepository {
        override suspend fun saveSignIn(token: AuthToken, account: IgAccount) = Unit
        override suspend fun token(): AuthToken? = null
        override suspend fun updateToken(accessToken: String, expiresAt: Instant) = Unit
        override suspend fun markTokenRevoked() = Unit
        override suspend fun isTokenRevoked() = false
        override fun observeSignInState(): Flow<SignInState> = flowOf(SignInState.SignedOut)
        override fun observeUsername(): Flow<String?> = flowOf("shop")
        override suspend fun signOut() = Unit
    }

    private val systemZone = TimeZone.getDefault()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // S7 shows the closing time in the phone's zone; pin it so screenshots match on every machine.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        TimeZone.setDefault(systemZone)
    }

    private fun viewModel() = SetRulesViewModel(SavedStateHandle(route = SetRulesRoute(post)), giveaways, accounts, clock)

    private fun text(id: Int) = compose.onNodeWithText(app.getString(id))

    private fun show(vm: SetRulesViewModel, onSaved: (Long) -> Unit = {}) =
        compose.setContent { GiveawayTheme { SetRulesScreen(onBack = {}, onSaved = onSaved, viewModel = vm) } }

    @Test
    fun continueCreatesTheDraftWithTheRules() {
        val saved = mutableListOf<Long>()
        show(viewModel()) { saved += it }
        compose.onNodeWithTag("rules:hashtag").performTextInput("#totebag")
        text(R.string.wizard_continue).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf(DRAFT_ID), saved)
        val (media, rules) = drafts.single()
        assertEquals(post.id, media.id)
        assertEquals(240, media.commentsCount)
        assertEquals("#totebag", rules.requiredHashtag)
        assertEquals(1, rules.minMentions)
        assertEquals(1, rules.winnersCount)
        assertEquals(2, rules.alternatesCount)
        assertTrue(rules.closesAt.isAfter(now.plus(Duration.ofDays(6))))
        assertEquals(listOf("Win a tote bag! @shop"), titles)
    }

    @Test
    fun aBadHashtagIsExplainedAndNothingIsSaved() {
        show(viewModel())
        compose.onNodeWithTag("rules:hashtag").performTextInput("totebag")
        text(R.string.wizard_continue).performScrollTo().performClick()
        text(R.string.rules_error_hashtag).assertExists()
        assertTrue(drafts.isEmpty())
    }

    @Test
    fun aPastClosingTimeIsExplained() {
        val vm = viewModel()
        vm.onFormChange { it.copy(closesAt = now.minusSeconds(1)) }
        vm.onContinue("New giveaway")
        assertEquals(setOf(RulesError.CLOSES_IN_PAST), vm.state.value.errors)
        assertTrue(vm.state.value.showErrors)
        assertTrue(drafts.isEmpty())
    }

    @Test
    fun comingBackAndContinuingUpdatesTheSameDraft() {
        val vm = viewModel()
        vm.onContinue("New giveaway")
        vm.onFormChange { it.copy(winners = 3) }
        vm.onContinue("New giveaway")
        assertEquals(1, drafts.size)
        assertEquals(listOf(DRAFT_ID to 3), ruleUpdates.map { (id, rules) -> id to rules.winnersCount })
    }

    @Test
    fun aPostWithoutCaptionUsesTheFallbackTitle() {
        val vm = SetRulesViewModel(
            SavedStateHandle(route = SetRulesRoute(post.copy(caption = null))),
            giveaways,
            accounts,
            clock,
        )
        vm.onContinue("New giveaway")
        assertEquals(listOf("New giveaway @shop"), titles)
    }

    @Test
    fun screenshotLight() {
        show(viewModel())
        compose.onRoot().captureRoboImage("src/test/screenshots/s7_set_rules_light.png")
    }

    @Test
    @Config(qualifiers = "ar-w390dp-h1600dp-xhdpi")
    fun screenshotArabicWithErrors() {
        val vm = viewModel()
        vm.onFormChange { it.copy(hashtag = "totebag", closesAt = now.minusSeconds(1)) }
        vm.onContinue("سحب جديد")
        show(vm)
        compose.onRoot().captureRoboImage("src/test/screenshots/s7_set_rules_arabic_errors.png")
    }

    private companion object {
        const val DRAFT_ID = 42L
    }
}
