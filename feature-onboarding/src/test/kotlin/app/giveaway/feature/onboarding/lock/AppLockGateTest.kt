package app.giveaway.feature.onboarding.lock

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.giveaway.core.data.db.AppLockMethod
import app.giveaway.core.designsystem.GiveawayTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** H-01: while locked nothing of the app is on screen, not even a dialog; its state comes back on unlock. */
@RunWith(RobolectricTestRunner::class)
class AppLockGateTest {

    @get:Rule
    val compose = createComposeRule()

    private var now = 1_000_000L
    private val controller = AppLockController { now }
    private val gate = LockGateViewModel(controller)

    private fun gone(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()

    private fun show() = compose.setContent {
        GiveawayTheme {
            AppLockGate(gate, lockScreen = { Text("Locked") }) {
                var taps by rememberSaveable { mutableIntStateOf(0) }
                // A dialog opens in its own window, which is how one could sit above a lock screen.
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Turn off app lock?") },
                    confirmButton = { TextButton(onClick = { taps++ }) { Text("Taps: $taps") } },
                )
            }
        }
    }

    @Test
    fun aDialogDoesNotOutliveTheLock() {
        controller.onSettings(AppLockMethod.PIN, lockAfterSeconds = 60)
        show()
        compose.onNodeWithText("Locked").assertExists()
        assertTrue(gone("Turn off app lock?"))
        controller.onUnlocked()
        compose.onNodeWithText("Turn off app lock?").assertExists()
    }

    @Test
    fun savedStateComesBackAfterUnlocking() {
        controller.onSettings(AppLockMethod.PIN, lockAfterSeconds = 60)
        controller.onUnlocked()
        show()
        compose.onNodeWithText("Taps: 0").performClick()
        compose.onNodeWithText("Taps: 1").performClick()
        // Two minutes in the background locks the app.
        controller.onBackground()
        now += 120_000
        controller.onForeground()
        compose.waitForIdle()
        assertTrue(gone("Taps: 2"))
        controller.onUnlocked()
        compose.onNodeWithText("Taps: 2").assertExists()
    }
}
