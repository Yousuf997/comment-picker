package app.giveaway.core.designsystem.component

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.designsystem.R

/** The creation flow is a six-step wizard (spec: Product principles). */
const val WIZARD_STEPS = 6

private val SegmentHeight = 4.dp
private val SegmentGap = 4.dp

/**
 * Wizard header: back button, [title] ("New giveaway" or the giveaway's title), "Step n of 6" and a six-segment
 * progress bar in the accent color. TalkBack reads the progress as one element with the step text.
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
    val stepText = stringResource(R.string.wizard_step, step, WIZARD_STEPS)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.navigate_back),
                    tint = colors.onBackground,
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(
                    title,
                    style = GiveawayTheme.typography.title.copy(textDirection = TextDirection.Content),
                    color = colors.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stepText, style = GiveawayTheme.typography.caption, color = colors.onMuted)
            }
        }
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
                val done = index < step
                Box(
                    Modifier
                        .weight(1f)
                        .height(SegmentHeight)
                        .background(if (done) colors.accent else colors.surfaceMuted, CircleShape),
                )
            }
        }
    }
}
