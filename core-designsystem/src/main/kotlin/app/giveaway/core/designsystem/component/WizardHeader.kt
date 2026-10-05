package app.giveaway.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.designsystem.R

/** The creation flow is a six-step wizard (spec: Product principles). */
const val WIZARD_STEPS = 6

private val SegmentHeight = 4.dp
private val SegmentGap = 4.dp

/** A touch target around each segment of the step bar (spec: Accessibility, 48 dp where possible). */
private val StepTouchHeight = 48.dp

/**
 * Moving between the steps of an existing giveaway (plan X-2): which steps can open, and how to open one. Provided by
 * the navigation host; without it the step bar only shows progress.
 */
class WizardSteps(val reachable: Set<Int>, val onStep: (Int) -> Unit)

val LocalWizardSteps = staticCompositionLocalOf<WizardSteps?> { null }

/**
 * Wizard header: back button, [title] ("New giveaway" or the giveaway's title), "Step n of 6" and a six-segment
 * progress bar in the accent color. With [LocalWizardSteps] each segment opens its step; otherwise TalkBack reads the
 * bar as one element with the step text.
 */
@Composable
fun WizardHeader(
    title: String,
    step: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    require(step in 1..WIZARD_STEPS) { "step must be 1..$WIZARD_STEPS, was $step" }
    val colors = GiveawayTheme.colors
    // On the dark stage (S11, S12) the text switches to the stage's light colors.
    val onStage = LocalOnDrawStage.current
    val textColor = if (onStage) colors.onDrawBackground else colors.onBackground
    val mutedColor = if (onStage) colors.onDrawMuted else colors.onMuted
    val stepText = stringResource(R.string.wizard_step, step, WIZARD_STEPS)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.navigate_back),
                    tint = textColor,
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    title,
                    style = GiveawayTheme.typography.title.copy(textDirection = TextDirection.Content),
                    color = textColor,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stepText, style = GiveawayTheme.typography.caption, color = mutedColor)
            }
        }
        val steps = LocalWizardSteps.current
        if (steps == null) ProgressBar(step, stepText) else StepBar(step, steps)
    }
}

@Composable
private fun ProgressBar(step: Int, stepText: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = stepText
                progressBarRangeInfo =
                    ProgressBarRangeInfo(step.toFloat(), 0f..WIZARD_STEPS.toFloat(), steps = WIZARD_STEPS - 1)
            },
        horizontalArrangement = Arrangement.spacedBy(SegmentGap),
    ) {
        repeat(WIZARD_STEPS) { index ->
            Segment(if (index < step) GiveawayTheme.colors.accent else null, Modifier.weight(1f))
        }
    }
}

/** Each segment is a button for its step: done steps in accent, open steps lighter, others muted and disabled. */
@Composable
private fun StepBar(step: Int, steps: WizardSteps) {
    val colors = GiveawayTheme.colors
    val names = stepNames()
    val current = stringResource(R.string.wizard_step_current)
    val unavailable = stringResource(R.string.wizard_step_unavailable)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SegmentGap)) {
        for (number in 1..WIZARD_STEPS) {
            val open = number in steps.reachable
            val label = stringResource(R.string.wizard_step_label, number, WIZARD_STEPS, names[number - 1])
            val color = when {
                number <= step -> colors.accent
                open -> colors.accentSoft
                else -> null
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(StepTouchHeight)
                    .clickable(enabled = open && number != step, role = Role.Button) { steps.onStep(number) }
                    .semantics {
                        contentDescription = label
                        selected = number == step
                        when {
                            number == step -> stateDescription = current
                            !open -> stateDescription = unavailable
                        }
                    }
                    .testTag("wizard:step:$number"),
                contentAlignment = Alignment.Center,
            ) {
                Segment(color, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Segment(color: Color?, modifier: Modifier) {
    Box(modifier.height(SegmentHeight).background(color ?: GiveawayTheme.colors.surfaceMuted, CircleShape))
}

@Composable
private fun stepNames(): List<String> = listOf(
    stringResource(R.string.wizard_step_post),
    stringResource(R.string.wizard_step_rules),
    stringResource(R.string.wizard_step_code),
    stringResource(R.string.wizard_step_import),
    stringResource(R.string.wizard_step_review),
    stringResource(R.string.wizard_step_draw),
)
