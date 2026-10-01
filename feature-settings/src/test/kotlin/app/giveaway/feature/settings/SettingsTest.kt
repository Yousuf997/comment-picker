package app.giveaway.feature.settings

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.giveaway.core.data.account.AccountRepository
import app.giveaway.core.data.account.SignInState
import app.giveaway.core.data.db.AppLockMethod
import app.giveaway.core.data.db.SettingsEntity
import app.giveaway.core.data.settings.SettingsRepository
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.designsystem.handle
import app.giveaway.core.instagram.auth.AuthToken
import app.giveaway.core.instagram.auth.IgAccount
import app.giveaway.core.security.lock.PinCheck
import app.giveaway.core.security.lock.PinStore
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
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
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/** C-10 acceptance: S5 rows and dialogs, app lock on/off, language switch, disconnect. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h1400dp-xhdpi")
class SettingsTest {

    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val settings = MutableStateFlow(SettingsEntity())
    private val username = MutableStateFlow<String?>("shop")
    private val languages = mutableListOf<String?>()
    private var signedOut = false
    private var pinCleared = false

    private val settingsRepo = object : SettingsRepository {
        override fun observe(): Flow<SettingsEntity> = settings
        override suspend fun get() = settings.value
        override suspend fun setAppLock(method: AppLockMethod?) {
            settings.value = settings.value.copy(appLockEnabled = method != null, appLockMethod = method)
        }
        override suspend fun update(transform: (SettingsEntity) -> SettingsEntity) {
            settings.value = transform(settings.value)
        }
    }
    private val accounts = object : AccountRepository {
        override suspend fun saveSignIn(token: AuthToken, account: IgAccount) = Unit
        override suspend fun token(): AuthToken? = null
        override suspend fun updateToken(accessToken: String, expiresAt: Instant) = Unit
        override suspend fun markTokenRevoked() = Unit
        override suspend fun isTokenRevoked() = false
        override fun observeSignInState(): Flow<SignInState> = flowOf(SignInState.SignedOut)
        override fun observeUsername(): Flow<String?> = username
        override suspend fun signOut() {
            signedOut = true
            username.value = null
        }
    }
    private val pins = object : PinStore {
        override fun hasPin() = true
        override fun setPin(pin: String) = Unit
        override fun verify(pin: String) = PinCheck.Correct
        override fun clear() {
            pinCleared = true
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = SettingsViewModel(settingsRepo, accounts, { pins }) { languages += it }

    private val noActions = SettingsActions({}, {}, {}, {}, {}, {}, {})

    private fun text(id: Int) = compose.onNodeWithText(app.getString(id))

    private fun show(vm: SettingsViewModel, actions: SettingsActions = noActions) =
        compose.setContent { GiveawayTheme { SettingsScreen(actions, vm) } }

    @Test
    fun switchingAppLockOnGoesThroughSetup() = runTest {
        val vm = viewModel()
        var setups = 0
        show(vm, noActions.copy(onSetUpAppLock = { setups++ }))
        text(R.string.settings_app_lock).performClick()
        compose.waitForIdle()
        assertEquals(1, setups)
    }

    @Test
    fun switchingAppLockOffAsksFirstThenClearsThePin() = runTest {
        settings.value = SettingsEntity(appLockEnabled = true, appLockMethod = AppLockMethod.PIN)
        show(viewModel())
        text(R.string.settings_app_lock).performClick()
        text(R.string.settings_lock_off_title).assertExists()
        text(R.string.settings_turn_off).performClick()
        compose.waitForIdle()
        assertNull(settings.value.appLockMethod)
        assertTrue(pinCleared)
    }

    @Test
    fun choosingALockTimeStoresIt() = runTest {
        show(viewModel())
        text(R.string.settings_lock_after).performClick()
        text(R.string.settings_after_5m).performClick()
        compose.waitForIdle()
        assertEquals(300, settings.value.lockAfterSeconds)
    }

    @Test
    fun choosingArabicSwitchesTheAppLanguage() = runTest {
        show(viewModel())
        text(R.string.settings_language).performScrollTo().performClick()
        text(R.string.settings_language_ar).performClick()
        compose.waitForIdle()
        assertEquals(listOf<String?>("ar"), languages)
        assertEquals("ar", settings.value.language)
    }

    @Test
    fun disconnectAsksThenSignsOut() = runTest {
        var disconnected = 0
        show(viewModel(), noActions.copy(onDisconnected = { disconnected++ }))
        compose.onNodeWithText(handle("shop")).assertExists()
        text(R.string.settings_disconnect).performScrollTo().performClick()
        compose.onNodeWithText(app.getString(R.string.settings_disconnect_body)).assertExists()
        // The dialog's confirm button is the last node labelled "Disconnect"; the row behind it is the first.
        val disconnects = compose.onAllNodesWithText(app.getString(R.string.settings_disconnect))
        disconnects[disconnects.fetchSemanticsNodes().size - 1].performClick()
        compose.waitForIdle()
        assertTrue(signedOut)
        assertEquals(1, disconnected)
    }

    @Test
    fun switchesAndDefaultsMatchTheSpec() = runTest {
        val vm = viewModel()
        val state = vm.state.first { it.username != null }
        assertTrue(state.settings.recordDrawsByDefault)
        assertFalse(state.settings.blockScreenshots)
        assertEquals(90, state.settings.autoDeleteDays)
        assertEquals(60, state.settings.lockAfterSeconds)
    }

    @Test
    fun screenshotLight() = captureRoboImage("src/test/screenshots/s5_settings_light.png") {
        GiveawayTheme { SettingsScreen(noActions, viewModel()) }
    }

    @Test
    @Config(qualifiers = "ar-w390dp-h1400dp-xhdpi")
    fun screenshotArabic() = captureRoboImage("src/test/screenshots/s5_settings_arabic.png") {
        GiveawayTheme { SettingsScreen(noActions, viewModel()) }
    }
}
