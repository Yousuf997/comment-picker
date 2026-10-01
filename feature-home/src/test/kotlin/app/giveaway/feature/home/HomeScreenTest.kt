package app.giveaway.feature.home

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.designsystem.handle
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/** C-09 acceptance: S4 empty state, list sections, chips and banners. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h1100dp-xhdpi")
class HomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val calls = mutableListOf<String>()
    private val actions = HomeActions(
        onNewGiveaway = { calls += "new" },
        onOpenSettings = { calls += "settings" },
        onOpenGiveaway = { id, destination -> calls += "open:$id:$destination" },
        onSignInAgain = { calls += "signIn" },
        onExportBackup = { calls += "backup" },
    )
    private val now = Instant.parse("2026-10-01T12:00:00Z")

    private fun card(
        id: Long,
        status: CardStatus,
        destination: GiveawayDestination,
        entries: Int = 0,
        comments: Int = 0,
    ) =
        GiveawayCard(id, "Summer drop $id", null, status, destination, now.plusSeconds(86_400), now, comments, entries)

    private val populated = HomeUiState(
        loading = false,
        username = "shop",
        inProgress = listOf(
            card(1, CardStatus.WAITING, GiveawayDestination.LOCK_IN),
            card(2, CardStatus.REVIEW, GiveawayDestination.REVIEW, entries = 1840, comments = 2400),
        ),
        completed = listOf(card(3, CardStatus.COMPLETED, GiveawayDestination.CERTIFICATE, entries = 312)),
    )

    private fun text(id: Int) = compose.onNodeWithText(app.getString(id))

    private fun show(state: HomeUiState) = compose.setContent { GiveawayTheme { HomeScreen(state, actions) } }

    @Test
    fun emptyStateInvitesTheFirstGiveaway() {
        show(HomeUiState(loading = false, username = "shop"))
        text(R.string.home_empty_title).assertExists()
        text(R.string.home_in_progress).assertDoesNotExist()
        text(R.string.home_new_giveaway).performClick()
        assertEquals(listOf("new"), calls)
    }

    @Test
    fun cardsAreGroupedWithChipsCountsAndOpenTheRightScreen() {
        show(populated)
        compose.onNodeWithText(handle("shop")).assertExists()
        compose.onNodeWithText(app.getString(R.string.home_in_progress).uppercase()).assertExists()
        compose.onNodeWithText(app.getString(R.string.home_completed).uppercase()).assertExists()
        text(R.string.status_waiting).assertExists()
        compose.onNodeWithText(app.resources.getQuantityString(R.plurals.home_entries, 1840, 1840)).assertExists()
        compose.onNodeWithText("Summer drop 2").performScrollTo().performClick()
        compose.onNodeWithText("Summer drop 3").performScrollTo().performClick()
        assertEquals(listOf("open:2:REVIEW", "open:3:CERTIFICATE"), calls)
    }

    @Test
    fun bannersAppearOnlyWhenNeededAndAct() {
        show(populated.copy(signInExpired = true, showBackupReminder = true))
        text(R.string.home_expired_action).performClick()
        text(R.string.home_backup_action).performScrollTo().performClick()
        compose.onNodeWithContentDescription(app.getString(R.string.home_settings)).performClick()
        assertEquals(listOf("signIn", "backup", "settings"), calls)
    }

    @Test
    fun noBannersByDefault() {
        show(populated)
        text(R.string.home_expired_title).assertDoesNotExist()
        text(R.string.home_backup_title).assertDoesNotExist()
    }

    @Test
    fun screenshotLight() = captureRoboImage("src/test/screenshots/s4_home_light.png") {
        GiveawayTheme { HomeScreen(populated.copy(showBackupReminder = true), actions) }
    }

    @Test
    fun screenshotDarkExpired() = captureRoboImage("src/test/screenshots/s4_home_dark_expired.png") {
        GiveawayTheme(darkTheme = true) { HomeScreen(populated.copy(signInExpired = true), actions) }
    }

    @Test
    @Config(qualifiers = "ar-w390dp-h1100dp-xhdpi")
    fun screenshotArabic() = captureRoboImage("src/test/screenshots/s4_home_arabic.png") {
        GiveawayTheme { HomeScreen(populated, actions) }
    }

    @Test
    fun screenshotEmpty() = captureRoboImage("src/test/screenshots/s4_home_empty.png") {
        GiveawayTheme { HomeScreen(HomeUiState(loading = false, username = "shop"), actions) }
    }
}
