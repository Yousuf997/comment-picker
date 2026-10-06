package app.giveaway.feature.create

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giveaway.core.data.importing.ImportPhase
import app.giveaway.core.designsystem.GiveawayDimens
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.designsystem.component.NoticeCard
import app.giveaway.core.designsystem.component.NoticeTone
import app.giveaway.core.designsystem.component.PrimaryButton
import app.giveaway.core.designsystem.component.SecondaryButton
import app.giveaway.core.designsystem.component.WizardHeader
import app.giveaway.core.designsystem.formatCount
import app.giveaway.core.designsystem.formatDateTime
import app.giveaway.core.designsystem.R as DesignR

private const val PERCENT = 100

@Composable
internal fun ImportCommentsScreen(
    onBack: () -> Unit,
    onReviewEntries: () -> Unit,
    viewModel: ImportCommentsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ImportCommentsScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
        onAcceptPartial = viewModel::acceptPartial,
        onReviewEntries = onReviewEntries,
    )
}

/** S9 Import comments (spec): progress, what went wrong if anything, and the checklist. */
@Composable
internal fun ImportCommentsScreen(
    state: ImportUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAcceptPartial: () -> Unit,
    onReviewEntries: () -> Unit,
) {
    var confirmingPartial by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GiveawayTheme.colors.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(GiveawayDimens.screenPadding)
            .testTag("screen:S9"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WizardHeader(title = state.title ?: stringResource(R.string.wizard_new_giveaway), step = 3, onBack = onBack)
        Text(
            stringResource(R.string.import_title),
            style = GiveawayTheme.typography.display,
            color = GiveawayTheme.colors.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        ImportBody(state, onRetry)
        if (state.progress.canAcceptPartial) {
            SecondaryButton(
                text = stringResource(R.string.import_accept_partial),
                onClick = { confirmingPartial = true },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        PrimaryButton(
            text = stringResource(R.string.import_review),
            onClick = onReviewEntries,
            enabled = state.canReview,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (confirmingPartial) {
        PartialImportDialog(
            imported = state.progress.imported,
            onConfirm = {
                confirmingPartial = false
                onAcceptPartial()
            },
            onDismiss = { confirmingPartial = false },
        )
    }
}

/** Before the deadline, a note; afterwards the progress, any problem and the checklist. */
@Composable
private fun ImportBody(state: ImportUiState, onRetry: () -> Unit) {
    val opensAt = state.opensAt
    if (opensAt != null) {
        NoticeCard(
            title = stringResource(R.string.import_not_yet_title),
            body = stringResource(R.string.import_not_yet_body, formatDateTime(opensAt)),
            tone = NoticeTone.Info,
        )
    } else {
        Progress(state)
        Problem(state, onRetry)
        Checklist(state)
        Text(
            stringResource(R.string.import_leave_note),
            style = GiveawayTheme.typography.caption,
            color = GiveawayTheme.colors.onMuted,
        )
    }
}

@Composable
private fun PartialImportDialog(imported: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.import_partial_title)) },
    text = { Text(pluralStringResource(R.plurals.import_partial_body, imported, formatCount(imported))) },
    confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.import_partial_confirm)) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.import_partial_cancel)) } },
)

@Composable
private fun Progress(state: ImportUiState) {
    val progress = state.progress
    val fraction = if (progress.expected > 0) (progress.imported.toFloat() / progress.expected).coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            pluralStringResource(
                R.plurals.import_count,
                progress.expected,
                formatCount(progress.imported),
                formatCount(progress.expected),
            ),
            style = GiveawayTheme.typography.titleLarge,
            color = GiveawayTheme.colors.onBackground,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("import:count"),
        )
        LinearProgressIndicator(
            progress = { fraction },
            color = GiveawayTheme.colors.accent,
            trackColor = GiveawayTheme.colors.surfaceMuted,
            modifier = Modifier.fillMaxWidth(),
        )
        Row {
            Text(
                stringResource(R.string.import_percent, formatCount((fraction * PERCENT).toInt())),
                style = GiveawayTheme.typography.caption,
                color = GiveawayTheme.colors.onMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.import_page, formatCount(progress.pages)),
                style = GiveawayTheme.typography.caption,
                color = GiveawayTheme.colors.onMuted,
            )
        }
    }
}

/** The warning card for each S9 state that needs one (spec: S9 states). */
@Composable
private fun Problem(state: ImportUiState, onRetry: () -> Unit) {
    val (title, body, retry) = when (state.progress.phase) {
        ImportPhase.OFFLINE -> Triple(R.string.import_offline_title, R.string.import_offline_body, true)
        ImportPhase.RATE_LIMITED -> Triple(R.string.import_paused_title, R.string.import_paused_body, true)
        ImportPhase.RETRYING -> Triple(R.string.import_retrying_title, R.string.import_retrying_body, true)
        ImportPhase.MISMATCH -> Triple(R.string.import_mismatch_title, R.string.import_mismatch_body, true)
        ImportPhase.POST_DELETED -> Triple(R.string.import_deleted_title, R.string.import_deleted_body, false)
        ImportPhase.SIGNED_OUT -> Triple(R.string.import_signed_out_title, R.string.import_signed_out_body, false)
        ImportPhase.NOT_STARTED, ImportPhase.RUNNING, ImportPhase.COMPLETE -> return
    }
    NoticeCard(
        title = stringResource(title),
        body = stringResource(body),
        tone = NoticeTone.Warning,
        actionLabel = if (retry) stringResource(R.string.import_retry) else null,
        onAction = if (retry) onRetry else null,
        modifier = Modifier.testTag("import:problem"),
    )
}

@Composable
private fun Checklist(state: ImportUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Step(R.string.import_step_fetch, state.fetching)
        Step(R.string.import_step_check, state.filtering)
        Step(R.string.import_step_duplicates, state.filtering)
        Step(R.string.import_step_exclusions, state.filtering)
    }
}

@Composable
private fun Step(label: Int, step: StepState) {
    val colors = GiveawayTheme.colors
    val status = stringResource(
        when (step) {
            StepState.PENDING -> R.string.import_step_pending
            StepState.RUNNING -> R.string.import_step_running
            StepState.STOPPED -> R.string.import_step_stopped
            StepState.DONE -> R.string.import_step_done
        },
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when (step) {
                StepState.DONE -> Box(
                    modifier = Modifier.size(24.dp).background(colors.successContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(DesignR.drawable.ic_check), null, Modifier.size(16.dp), colors.success) }
                // A still ring, not a spinner: the status text says it's running, and screenshots stay stable.
                StepState.RUNNING -> Box(Modifier.size(18.dp).border(2.dp, colors.accent, CircleShape))
                StepState.STOPPED -> Icon(
                    painterResource(DesignR.drawable.ic_warning),
                    contentDescription = null,
                    tint = colors.accentOnSoft,
                    modifier = Modifier.size(20.dp),
                )
                StepState.PENDING -> Box(Modifier.size(12.dp).background(colors.outline, CircleShape))
            }
        }
        Text(
            stringResource(label),
            style = GiveawayTheme.typography.body,
            color = if (step == StepState.PENDING) colors.onMuted else colors.onBackground,
            modifier = Modifier.weight(1f),
        )
        // Color and icon are never the only signal (spec: Accessibility).
        Text(status, style = GiveawayTheme.typography.caption, color = colors.onMuted)
    }
}
