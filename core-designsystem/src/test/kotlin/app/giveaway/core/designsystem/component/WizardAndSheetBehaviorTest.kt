package app.giveaway.core.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.test.espresso.Espresso
import app.giveaway.core.designsystem.GiveawayTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WizardAndSheetBehaviorTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun wizardHeaderExposesBackAndProgressToTalkBack() {
        var backs = 0
        compose.setContent {
            GiveawayTheme { WizardHeader(title = "New giveaway", step = 2, onBack = { backs++ }) }
        }
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
        compose.onNodeWithContentDescription("Step 2 of 5", useUnmergedTree = true)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(2f, 0f..5f, steps = 4),
                ),
            )
    }

    @Test
    fun stepBarOpensReachableStepsOnly() {
        val opened = mutableListOf<Int>()
        compose.setContent {
            GiveawayTheme {
                CompositionLocalProvider(LocalWizardSteps provides WizardSteps(setOf(1, 2, 3, 4)) { opened += it }) {
                    WizardHeader(title = "Win a tote bag!", step = 3, onBack = {})
                }
            }
        }
        compose.onNodeWithTag("wizard:step:2").assertContentDescriptionEquals("Step 2 of 5: Rules").performClick()
        compose.onNodeWithTag("wizard:step:4").performClick()
        // The current step and steps not reached yet don't open.
        compose.onNodeWithTag("wizard:step:3").assertIsNotEnabled().assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Current step"))
        compose.onNodeWithTag("wizard:step:5").assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Not available yet"))
        assertEquals(listOf(2, 4), opened)
    }

    @Test
    fun wizardHeaderRejectsStepsOutsideTheWizard() {
        assertThrows(IllegalArgumentException::class.java) {
            compose.setContent { GiveawayTheme { WizardHeader(title = "x", step = 6, onBack = {}) } }
        }
    }

    @Test
    fun dismissibleSheetClosesOnBackAndSwipe() {
        var dismissals by mutableIntStateOf(0)
        compose.setContent {
            var shown by remember { mutableStateOf(true) }
            GiveawayTheme {
                if (shown) {
                    GiveawayBottomSheet(onDismissRequest = { dismissals++; shown = false }) { Text("Sheet content") }
                }
            }
        }
        compose.onNodeWithText("Sheet content").assertExists()
        Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText("Sheet content").assertDoesNotExist()
        assertEquals(1, dismissals)
    }

    @Test
    fun nonDismissibleSheetStaysOpenUntilTheUserChooses() {
        var dismissals = 0
        compose.setContent {
            GiveawayTheme {
                GiveawayBottomSheet(onDismissRequest = { dismissals++ }, dismissible = false) {
                    Text("Save the draw video?")
                }
            }
        }
        val sheet = compose.onNodeWithText("Save the draw video?")
        Espresso.pressBack()
        compose.waitForIdle()
        sheet.assertExists()
        sheet.performTouchInput { swipeDown() }
        compose.waitForIdle()
        sheet.assertExists()
        assertEquals(0, dismissals)
    }
}
