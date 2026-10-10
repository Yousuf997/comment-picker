package app.giveaway.feature.home

import app.giveaway.core.data.account.AccountRepository
import app.giveaway.core.data.account.SignInState
import app.giveaway.core.data.db.AppLockMethod
import app.giveaway.core.data.db.CommitmentEntity
import app.giveaway.core.data.db.GiveawayEntity
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.db.GiveawaySummary
import app.giveaway.core.data.db.SettingsEntity
import app.giveaway.core.data.giveaway.GiveawayRepository
import app.giveaway.core.data.settings.SettingsRepository
import app.giveaway.core.instagram.api.IgMedia
import app.giveaway.core.instagram.auth.AuthToken
import app.giveaway.core.instagram.auth.IgAccount
import app.giveaway.draw.Rules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val now = Instant.parse("2026-10-01T12:00:00Z")
    private val signIn = MutableStateFlow<SignInState>(SignInState.Active("shop"))
    private val summaries = MutableStateFlow<List<GiveawaySummary>>(emptyList())
    private val settings = MutableStateFlow(SettingsEntity())

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = HomeViewModel(
        FakeAccounts(signIn),
        FakeGiveaways(summaries),
        FakeSettings(settings),
        Clock.fixed(now, ZoneOffset.UTC),
    )

    private fun summary(id: Long, status: GiveawayStatus, closesIn: Duration = Duration.ofDays(2)) = GiveawaySummary(
        GiveawayEntity(id, "G$id", "m$id", "IMAGE", null, status, now, now.plus(closesIn), null, "shop"),
        commentCount = 10,
        validEntryCount = 0,
    )

    @Test
    fun eachStatusGetsItsChipAndDestination() = runTest {
        summaries.value = listOf(
            summary(1, GiveawayStatus.DRAFT),
            summary(2, GiveawayStatus.COMMITTED),
            summary(3, GiveawayStatus.COMMITTED, closesIn = Duration.ofHours(-1)),
            summary(4, GiveawayStatus.IMPORTING),
            summary(5, GiveawayStatus.REVIEW),
            summary(6, GiveawayStatus.DRAWN),
            summary(7, GiveawayStatus.ARCHIVED),
        )
        val state = viewModel().state.first { !it.loading }
        assertEquals(
            listOf(
                CardStatus.DRAFT to GiveawayDestination.RULES,
                CardStatus.WAITING to GiveawayDestination.IMPORT,
                CardStatus.READY_TO_IMPORT to GiveawayDestination.IMPORT,
                CardStatus.IMPORTING to GiveawayDestination.IMPORT,
                CardStatus.REVIEW to GiveawayDestination.REVIEW,
                CardStatus.DRAWN to GiveawayDestination.WINNERS,
            ),
            state.inProgress.map { it.status to it.destination },
        )
        assertEquals(listOf(7L), state.completed.map { it.id })
        assertEquals(GiveawayDestination.CERTIFICATE, state.completed.single().destination)
    }

    @Test
    fun eachCardShowsTheTimeLeftItsTimeOrDone() = runTest {
        val closed = Duration.ofHours(-1)
        summaries.value = listOf(
            summary(1, GiveawayStatus.COMMITTED, closesIn = Duration.ofHours(53)),
            summary(2, GiveawayStatus.COMMITTED, closesIn = Duration.ofSeconds(90)),
            summary(3, GiveawayStatus.COMMITTED, closesIn = closed),
            summary(4, GiveawayStatus.REVIEW, closesIn = closed),
            summary(5, GiveawayStatus.DRAWN, closesIn = closed),
            summary(6, GiveawayStatus.ARCHIVED, closesIn = closed),
        )
        val state = viewModel().state.first { !it.loading }
        assertEquals(
            listOf(
                CardTiming.Left(Duration.ofHours(53)),
                // Rounded up: the last part of a minute still counts as a minute left.
                CardTiming.Left(Duration.ofMinutes(2)),
                CardTiming.ItsTime,
                CardTiming.ItsTime,
                CardTiming.Done,
            ),
            state.inProgress.map { it.timing },
        )
        assertEquals(CardTiming.Done, state.completed.single().timing)
    }

    @Test
    fun theCountdownMovesOnEachMinuteUntilItsTime() = runTest {
        val clock = MovableClock(now)
        summaries.value = listOf(summary(1, GiveawayStatus.COMMITTED, closesIn = Duration.ofMinutes(2)))
        val vm = HomeViewModel(FakeAccounts(signIn), FakeGiveaways(summaries), FakeSettings(settings), clock)
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()
        fun card() = vm.state.value.inProgress.single()
        assertEquals(CardTiming.Left(Duration.ofMinutes(2)), card().timing)

        clock.now = now.plusSeconds(60)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(CardTiming.Left(Duration.ofMinutes(1)), card().timing)
        assertEquals(CardStatus.WAITING, card().status)

        clock.now = now.plusSeconds(120)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(CardTiming.ItsTime, card().timing)
        assertEquals(CardStatus.READY_TO_IMPORT, card().status)
    }

    @Test
    fun anEmptyListIsTheFirstRunStateWithoutABackupReminder() = runTest {
        val state = viewModel().state.first { !it.loading }
        assertTrue(state.isEmpty)
        assertFalse(state.showBackupReminder)
        assertEquals("shop", state.username)
    }

    @Test
    fun backupReminderAppearsWithoutARecentBackup() = runTest {
        summaries.value = listOf(summary(1, GiveawayStatus.DRAFT))
        val vm = viewModel()
        assertTrue(vm.state.first { !it.loading }.showBackupReminder)
        settings.value = SettingsEntity(lastBackupAt = now.minus(Duration.ofDays(10)))
        assertFalse(vm.state.first { it.username != null && !it.showBackupReminder }.showBackupReminder)
        settings.value = SettingsEntity(lastBackupAt = now.minus(Duration.ofDays(31)))
        assertTrue(vm.state.first { it.showBackupReminder }.showBackupReminder)
    }

    @Test
    fun anExpiredSignInShowsTheBannerAndKeepsTheHandle() = runTest {
        signIn.value = SignInState.Expired("shop")
        val state = viewModel().state.first { !it.loading }
        assertTrue(state.signInExpired)
        assertEquals("shop", state.username)
    }

    private class MovableClock(var now: Instant) : Clock() {
        override fun instant(): Instant = now

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this
    }

    private class FakeAccounts(private val state: Flow<SignInState>) : AccountRepository {
        override suspend fun saveSignIn(token: AuthToken, account: IgAccount) = Unit
        override suspend fun token(): AuthToken? = null
        override suspend fun updateToken(accessToken: String, expiresAt: Instant) = Unit
        override suspend fun markTokenRevoked() = Unit
        override suspend fun isTokenRevoked() = false
        override fun observeSignInState() = state
        override fun observeUsername(): Flow<String?> = flowOf(null)
        override suspend fun signOut() = Unit
    }

    private class FakeGiveaways(private val summaries: Flow<List<GiveawaySummary>>) : GiveawayRepository {
        override fun observeSummaries() = summaries
        override suspend fun get(id: Long): GiveawayEntity? = null
        override suspend fun createDraft(media: IgMedia, title: String, ownerUsername: String, rules: Rules) = 0L
        override suspend fun saveRules(id: Long, rules: Rules) = Unit
        override suspend fun rules(id: Long): Rules? = null
        override suspend fun commitment(id: Long): CommitmentEntity? = null
        override suspend fun saveCommitment(id: Long, commitHash: String, encryptedSeed: ByteArray) = Unit
        override suspend fun commit(id: Long) = Unit
        override suspend fun transition(id: Long, to: GiveawayStatus) = Unit
    }

    private class FakeSettings(private val settings: MutableStateFlow<SettingsEntity>) : SettingsRepository {
        override fun observe(): Flow<SettingsEntity> = settings
        override suspend fun get() = settings.value
        override suspend fun setAppLock(method: AppLockMethod?) = Unit
        override suspend fun update(transform: (SettingsEntity) -> SettingsEntity) {
            settings.value = transform(settings.value)
        }
    }
}
