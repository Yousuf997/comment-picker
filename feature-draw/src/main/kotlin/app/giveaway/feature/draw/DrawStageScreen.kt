package app.giveaway.feature.draw

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giveaway.core.designsystem.GiveawayDimens
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.designsystem.component.ChipTone
import app.giveaway.core.designsystem.component.DrawStage
import app.giveaway.core.designsystem.component.GiveawayBottomSheet
import app.giveaway.core.designsystem.component.GiveawaySwitch
import app.giveaway.core.designsystem.component.NoticeCard
import app.giveaway.core.designsystem.component.NoticeTone
import app.giveaway.core.designsystem.component.PrimaryButton
import app.giveaway.core.designsystem.component.StatusChip
import app.giveaway.core.designsystem.component.WizardHeader
import app.giveaway.core.designsystem.formatCount
import app.giveaway.core.designsystem.handle
import app.giveaway.draw.Pick
import app.giveaway.draw.Role as DrawRole

private val RingSize = 168.dp
private val RingStroke = 10.dp

@Composable
internal fun DrawStageScreen(
    onBack: () -> Unit,
    onDrawn: (record: Boolean) -> Unit,
    onAlreadyDrawn: () -> Unit,
    viewModel: DrawStageViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DrawStageEvent.Drawn -> onDrawn(event.record)
                DrawStageEvent.AlreadyDrawn -> onAlreadyDrawn()
            }
        }
    }
    DrawStageScreen(
        state = state,
        onBack = onBack,
        onRecordDraw = viewModel::onRecordDraw,
        onDraw = viewModel::drawWinners,
        onTestDraw = viewModel::testDraw,
        onCloseTest = viewModel::closeTest,
    )
}

/** S11 Draw (spec): the dark stage with the checks, the entry count, the record switch and the draw buttons. */
@Composable
internal fun DrawStageScreen(
    state: DrawStageState,
    onBack: () -> Unit,
    onRecordDraw: (Boolean) -> Unit,
    onDraw: () -> Unit,
    onTestDraw: () -> Unit,
    onCloseTest: () -> Unit,
) {
    DrawStage(modifier = Modifier.fillMaxSize().testTag("screen:S11")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(GiveawayDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WizardHeader(
                title = state.title ?: stringResource(R.string.draw_title),
                step = 5,
                onBack = onBack,
            )
            Checks(state)
            EntryRing(state.entries)
            Text(
                stringResource(R.string.draw_title),
                style = GiveawayTheme.typography.display,
                color = GiveawayTheme.colors.onDrawBackground,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                pickSummary(state.winners, state.alternates),
                style = GiveawayTheme.typography.body,
                color = GiveawayTheme.colors.onDrawMuted,
                textAlign = TextAlign.Center,
            )
            if (state.fewerThanRequested) {
                NoticeCard(
                    title = stringResource(R.string.draw_fewer_title),
                    body = pluralStringResource(R.plurals.draw_fewer_body, state.people, formatCount(state.people)),
                    tone = NoticeTone.Info,
                )
            }
            RecordingOptions(state, onRecordDraw)
            PrimaryButton(
                text = stringResource(R.string.draw_winners),
                onClick = onDraw,
                enabled = state.canDraw,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onTestDraw, enabled = state.canDraw) {
                Text(stringResource(R.string.draw_test_first), color = GiveawayTheme.colors.onDrawBackground)
            }
            Text(
                stringResource(R.string.draw_test_note),
                style = GiveawayTheme.typography.caption,
                color = GiveawayTheme.colors.onDrawMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
    state.testPicks?.let { TestDrawSheet(it, onCloseTest) }
}

/** The check before the draw (spec: S11): Play Integrity. It never blocks the draw, only what the certificate says. */
@Composable
private fun Checks(state: DrawStageState) {
    when (state.integrity) {
        null -> Text(
            stringResource(R.string.draw_checking),
            style = GiveawayTheme.typography.caption,
            color = GiveawayTheme.colors.onDrawMuted,
        )
        false -> NoticeCard(
            title = stringResource(R.string.draw_integrity_title),
            body = stringResource(R.string.draw_integrity_body),
            tone = NoticeTone.Info,
        )
        true -> Unit
    }
}

/** The ring with the valid entry count (spec: S11). */
@Composable
private fun EntryRing(entries: Int) {
    val colors = GiveawayTheme.colors
    val label = pluralStringResource(R.plurals.draw_entries, entries, formatCount(entries))
    Box(
        modifier = Modifier.size(RingSize).clearAndSetSemantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = RingStroke.toPx()
            drawCircle(colors.accent, radius = (size.minDimension - stroke) / 2, style = Stroke(stroke))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(formatCount(entries), style = GiveawayTheme.typography.displayLarge, color = colors.onDrawBackground)
            Text(
                stringResource(R.string.draw_entries_label),
                style = GiveawayTheme.typography.caption,
                color = colors.onDrawMuted,
            )
        }
    }
}

/** The record switch, and a warning when the phone is short of space for the video (spec: S11 checks). */
@Composable
private fun RecordingOptions(state: DrawStageState, onRecordDraw: (Boolean) -> Unit) {
    RecordSwitch(state.recordDraw, onRecordDraw)
    if (state.recordDraw && state.lowStorage) {
        NoticeCard(
            title = stringResource(R.string.draw_low_storage_title),
            body = stringResource(R.string.draw_low_storage_body),
            tone = NoticeTone.Warning,
        )
    }
}

@Composable
private fun RecordSwitch(record: Boolean, onRecordDraw: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = record, role = Role.Switch, onValueChange = onRecordDraw),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.draw_record),
                style = GiveawayTheme.typography.bodyStrong,
                color = GiveawayTheme.colors.onDrawBackground,
            )
            Text(
                stringResource(R.string.draw_record_help),
                style = GiveawayTheme.typography.caption,
                color = GiveawayTheme.colors.onDrawMuted,
            )
        }
        GiveawaySwitch(checked = record, onCheckedChange = null)
    }
}

@Composable
private fun pickSummary(winners: Int, alternates: Int): String {
    val w = pluralStringResource(R.plurals.draw_winner_count, winners, formatCount(winners))
    val a = pluralStringResource(R.plurals.draw_alternate_count, alternates, formatCount(alternates))
    return stringResource(R.string.draw_pick_summary, w, a)
}

/** A test draw's result, clearly labelled TEST (spec: Test draws). */
@Composable
private fun TestDrawSheet(picks: List<Pick>, onClose: () -> Unit) {
    GiveawayBottomSheet(onDismissRequest = onClose, modifier = Modifier.testTag("draw:test_sheet")) {
        Column(
            modifier = Modifier.padding(horizontal = GiveawayDimens.screenPadding).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatusChip(stringResource(R.string.draw_test_label), ChipTone.Accent)
            Text(
                stringResource(R.string.draw_test_title),
                style = GiveawayTheme.typography.title,
                color = GiveawayTheme.colors.onBackground,
            )
            picks.forEach { pick ->
                val role = stringResource(
                    if (pick.role == DrawRole.WINNER) R.string.draw_role_winner else R.string.draw_role_alternate,
                )
                Text(
                    stringResource(R.string.draw_test_pick, formatCount(pick.position), handle(pick.username), role),
                    style = GiveawayTheme.typography.body,
                    color = GiveawayTheme.colors.onBackground,
                )
            }
            Text(
                stringResource(R.string.draw_test_sheet_note),
                style = GiveawayTheme.typography.caption,
                color = GiveawayTheme.colors.onMuted,
            )
            // The sheet is a light surface, even over the stage.
            PrimaryButton(
                text = stringResource(R.string.draw_test_close),
                onClick = onClose,
                onStage = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

