package app.giveaway.feature.settings

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giveaway.core.data.backup.BackupManager
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.giveaway.DefaultGiveawayRepository
import app.giveaway.core.data.giveaway.SeedVault
import app.giveaway.core.data.media.MediaRepository
import app.giveaway.core.data.settings.DefaultSettingsRepository
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.instagram.api.IgMedia
import app.giveaway.core.instagram.api.MediaKind
import app.giveaway.core.security.backup.BackupCipher
import app.giveaway.core.security.backup.PasswordKdf
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** M-13 acceptance: backup needs a matching, strong enough password; restore checks the file, then confirms. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h1100dp-xhdpi")
class BackupScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val clock = Clock.fixed(Instant.parse("2026-10-01T09:00:00Z"), ZoneOffset.UTC)
    private val cipher = BackupCipher(
        PasswordKdf { pw, salt, _ -> MessageDigest.getInstance("SHA-256").digest(pw + salt) },
        BackupCipher.DEFAULT_PARAMS,
    )
    private val vault = object : SeedVault {
        override fun seal(giveawayId: Long, seed: ByteArray) = seed.reversedArray()

        override fun open(giveawayId: Long, sealed: ByteArray) = sealed.reversedArray()
    }
    private lateinit var db: GiveawayDatabase
    private lateinit var backups: BackupManager
    private val file get() = File(app.cacheDir, "test.gwbk")
    private val password = "Tote bags 4 all!"

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(app, GiveawayDatabase::class.java).allowMainThreadQueries().build()
        backups = BackupManager(db, cipher, vault, clock, MediaRepository(app, db, clock)) { _, _ -> }
        runBlocking {
            val post = IgMedia("m1", MediaKind.IMAGE, false, null, null, clock.instant(), 0, null)
            DefaultGiveawayRepository(db, DefaultSettingsRepository(db.settingsDao()), clock)
                .createDraft(post, "Win a tote bag!", "shop", rules())
        }
    }

    @After
    fun tearDown() {
        db.close()
        file.delete()
        Dispatchers.resetMain()
    }

    private fun rules() = Rules(1, null, null, true, true, true, clock.instant(), 1, 0)

    private fun titles() = runBlocking { db.giveawayDao().observeAll().first().map { it.title } }

    private fun shown(id: Int) = compose.onAllNodesWithText(app.getString(id)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun passwordStrengthGrowsWithLengthAndVariety() {
        assertEquals(PasswordStrength.NONE, PasswordStrength.of(""))
        assertEquals(PasswordStrength.WEAK, PasswordStrength.of("abc123"))
        assertEquals(PasswordStrength.WEAK, PasswordStrength.of("aaaaaaaaaaaa"))
        assertEquals(PasswordStrength.FAIR, PasswordStrength.of("toteshop"))
        assertEquals(PasswordStrength.GOOD, PasswordStrength.of("toteshop2026"))
        assertEquals(PasswordStrength.STRONG, PasswordStrength.of(password))
        assertTrue(PasswordStrength.of("كلمة مرور طويلة").acceptable)
    }

    @Test
    fun createNeedsAStrongEnoughPasswordTypedTwice() {
        var typed by mutableStateOf("")
        compose.setContent {
            GiveawayTheme {
                BackupScreen(BackupViewModel.Status.IDLE, typed, { typed = it }, onCreate = {}, onBack = {})
            }
        }
        val create = compose.onNodeWithText(app.getString(R.string.backup_create))
        compose.onNodeWithTag("backup:password").performTextInput("short")
        compose.onNodeWithTag("backup:confirm").performTextInput("short")
        create.performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("backup:password").performTextReplacement(password)
        assertTrue(shown(R.string.backup_mismatch))
        create.assertIsNotEnabled()
        compose.onNodeWithTag("backup:confirm").performTextReplacement(password)
        create.assertIsEnabled()
    }

    @Test
    fun exportWritesAnEncryptedFileThatInspectReads() {
        val vm = BackupViewModel(app, backups)
        vm.export(Uri.fromFile(file), password.toCharArray())
        compose.waitUntil(WAIT_MS) { vm.status.value == BackupViewModel.Status.SAVED }
        assertEquals("GWBK", file.readBytes().copyOf(4).toString(Charsets.US_ASCII))
        val summary = runBlocking { backups.inspect(file.inputStream(), password.toCharArray()) }
        assertEquals(1, summary.giveaways)
        vm.viewModelScope.cancel()
    }

    private fun showRestore(vm: RestoreViewModel) {
        compose.setContent {
            val state by vm.state.collectAsState()
            GiveawayTheme {
                RestoreScreen(
                    state = state,
                    fileName = "test.gwbk",
                    actions = RestoreActions(
                        onPickFile = {},
                        onCheck = { vm.check(it.toCharArray()) },
                        onRestore = { vm.restore(it.toCharArray()) },
                        onBack = {},
                    ),
                )
            }
        }
    }

    @Test
    fun aWrongPasswordIsExplainedAndChangesNothing() {
        runBlocking { backups.export(file.outputStream(), password.toCharArray()) }
        val vm = RestoreViewModel(app, backups).apply { onFile(Uri.fromFile(file)) }
        showRestore(vm)
        compose.onNodeWithTag("restore:password").performTextInput("not it")
        compose.onNodeWithText(app.getString(R.string.restore_check)).performClick()
        compose.waitUntil(WAIT_MS) { shown(R.string.restore_error_password) }
        assertEquals(listOf("Win a tote bag!"), titles())
        vm.viewModelScope.cancel()
    }

    @Test
    fun restoreShowsWhatComesBackThenReplacesTheData() {
        runBlocking { backups.export(file.outputStream(), password.toCharArray()) }
        // After the backup, this phone gets another giveaway that the restore will remove.
        runBlocking {
            val post = IgMedia("m2", MediaKind.IMAGE, false, null, null, clock.instant(), 0, null)
            DefaultGiveawayRepository(db, DefaultSettingsRepository(db.settingsDao()), clock)
                .createDraft(post, "Made later", "shop", rules())
        }
        val vm = RestoreViewModel(app, backups).apply { onFile(Uri.fromFile(file)) }
        showRestore(vm)
        compose.onNodeWithTag("restore:password").performTextInput(password)
        compose.onNodeWithText(app.getString(R.string.restore_check)).performClick()
        compose.waitUntil(WAIT_MS) { shown(R.string.restore_warning_title) }
        compose.onRoot().captureRoboImage("src/test/screenshots/restore_confirm.png")
        compose.onNodeWithTag("restore:confirm").performScrollTo().performClick()
        compose.waitUntil(WAIT_MS) { vm.state.value.restored }
        assertEquals(listOf("Win a tote bag!"), titles())
        vm.viewModelScope.cancel()
    }

    @Test
    fun screenshotBackup() {
        compose.setContent {
            GiveawayTheme { BackupScreen(BackupViewModel.Status.IDLE, password, {}, onCreate = {}, onBack = {}) }
        }
        compose.onNodeWithTag("backup:confirm").performTextInput(password)
        compose.onRoot().captureRoboImage("src/test/screenshots/backup.png")
    }

    private companion object {
        const val WAIT_MS = 15_000L
    }
}
