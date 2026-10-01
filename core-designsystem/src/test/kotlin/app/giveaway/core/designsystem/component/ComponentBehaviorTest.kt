package app.giveaway.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.giveaway.core.designsystem.GiveawayTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Spec: touch targets 48 dp where possible, never under 44 dp; every control usable with TalkBack. */
@RunWith(RobolectricTestRunner::class)
class ComponentBehaviorTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun everyInteractiveComponentHasA48dpTouchTarget() {
        compose.setContent {
            GiveawayTheme {
                Column {
                    PrimaryButton("Primary", onClick = {}, modifier = Modifier.testTag("primary"))
                    SecondaryButton("Secondary", onClick = {}, modifier = Modifier.testTag("secondary"))
                    GiveawaySwitch(checked = true, onCheckedChange = {}, modifier = Modifier.testTag("switch"))
                    SettingsRow("Row", onClick = {}, modifier = Modifier.testTag("row"))
                    SettingsSwitchRow(
                        "Switch row",
                        checked = false,
                        onCheckedChange = {},
                        modifier = Modifier.testTag("switchRow"),
                    )
                    GiveawayCard(onClick = {}, modifier = Modifier.testTag("card")) { Text("Card") }
                    Stepper(value = 3, onValueChange = {}, range = 1..50, label = "winners")
                }
            }
        }
        val tagged = listOf("primary", "secondary", "switch", "row", "switchRow", "card")
        val nodes = tagged.map(compose::onNodeWithTag) +
            listOf("Decrease winners", "Increase winners").map(compose::onNodeWithContentDescription)
        nodes.forEach { it.assertTouchTargetAtLeast(MIN_TOUCH) }
    }

    @Test
    fun switchRowIsOneSwitchControlThatToggles() {
        compose.setContent {
            var checked by remember { mutableStateOf(false) }
            GiveawayTheme {
                SettingsSwitchRow("Record draws", checked = checked, onCheckedChange = { checked = it })
            }
        }
        val row = compose.onNodeWithText("Record draws", useUnmergedTree = false)
        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).assertIsOff()
        row.performClick()
        row.assertIsOn()
    }

    @Test
    fun stepperStopsAtItsBoundsAndAnnouncesChanges() {
        compose.setContent {
            var value by remember { mutableIntStateOf(1) }
            GiveawayTheme {
                Stepper(value = value, onValueChange = { value = it }, range = 1..2, label = "winners")
            }
        }
        val decrease = compose.onNodeWithContentDescription("Decrease winners")
        val increase = compose.onNodeWithContentDescription("Increase winners")
        decrease.assertIsNotEnabled()
        increase.assertIsEnabled().performClick()
        compose.onNodeWithText("2").assertTextEquals("2")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
        increase.assertIsNotEnabled()
        decrease.assertIsEnabled()
    }

    /** Touch bounds include the padding Material adds around small controls (minimumInteractiveComponentSize). */
    private fun SemanticsNodeInteraction.assertTouchTargetAtLeast(min: Dp) {
        val node = fetchSemanticsNode()
        val bounds = node.touchBoundsInRoot
        val minPx = with(compose.density) { min.toPx() } - PIXEL_TOLERANCE
        val name = node.config.toString()
        assertTrue("Touch width ${bounds.width}px < $min for $name", bounds.width >= minPx)
        assertTrue("Touch height ${bounds.height}px < $min for $name", bounds.height >= minPx)
    }

    private companion object {
        const val PIXEL_TOLERANCE = 0.5f
        val MIN_TOUCH = 48.dp
    }
}
