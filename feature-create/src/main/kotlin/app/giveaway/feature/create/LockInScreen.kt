package app.giveaway.feature.create

import android.Manifest
import android.content.ClipData
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giveaway.core.designsystem.GiveawayDimens
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.designsystem.component.ChipTone
import app.giveaway.core.designsystem.component.NoticeCard
import app.giveaway.core.designsystem.component.NoticeTone
import app.giveaway.core.designsystem.component.PrimaryButton
import app.giveaway.core.designsystem.component.StatusChip
import app.giveaway.core.designsystem.component.WizardHeader
import app.giveaway.core.designsystem.formatCount
import app.giveaway.core.designsystem.formatDateTime
import app.giveaway.core.designsystem.ltrIsolated
import kotlinx.coroutines.launch
import app.giveaway.core.designsystem.R as DesignR

@Composable
internal fun LockInScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: LockInViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.done.collect { onDone() } }
    // The deadline reminder is a notification; Android 13+ asks for it here. Either answer continues.
    val context = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.onDone()
    }
    LockInScreen(
        state = state,
        onBack = onBack,
        onConfirmedChange = viewModel::onConfirmedChange,
        onDone = {
            val mustAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            if (mustAsk) {
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.onDone()
            }
        },
    )
}

/** S8 Lock in the draw (spec): the draw code to paste into the caption before entries close. */
@Composable
internal fun LockInScreen(
    state: LockInUiState,
    onBack: () -> Unit,
    onConfirmedChange: (Boolean) -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GiveawayTheme.colors.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(GiveawayDimens.screenPadding)
            .testTag("screen:S8"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WizardHeader(title = state.title ?: stringResource(R.string.wizard_new_giveaway), step = 3, onBack = onBack)
        Text(
            stringResource(R.string.lock_in_title),
            style = GiveawayTheme.typography.display,
            color = GiveawayTheme.colors.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(R.string.lock_in_explanation),
            style = GiveawayTheme.typography.body,
            color = GiveawayTheme.colors.onMuted,
        )
        state.drawCode?.let { DrawCodeCard(it) }
        Steps(state)
        ConfirmRow(checked = state.confirmed, onCheckedChange = onConfirmedChange)
        if (state.deadlinePassed) {
            NoticeCard(
                title = stringResource(R.string.lock_in_deadline_passed_title),
                body = stringResource(R.string.lock_in_deadline_passed_body),
                tone = NoticeTone.Warning,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(DesignR.drawable.ic_lock),
                contentDescription = null,
                tint = GiveawayTheme.colors.onMuted,
                modifier = Modifier.size(18.dp),
            )
            Text(
                stringResource(R.string.lock_in_secret_note),
                style = GiveawayTheme.typography.caption,
                color = GiveawayTheme.colors.onMuted,
            )
        }
        PrimaryButton(
            text = stringResource(R.string.lock_in_done),
            onClick = onDone,
            enabled = state.confirmed && state.drawCode != null && !state.saving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The dark card with the code (spec: S8). The code stays left to right in Arabic (spec: Localization). */
@Composable
private fun DrawCodeCard(drawCode: String) {
    val colors = GiveawayTheme.colors
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    val clipLabel = stringResource(R.string.lock_in_code_label)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.drawBackground, GiveawayTheme.shapes.card)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                clipLabel.uppercase(),
                style = GiveawayTheme.typography.overline,
                color = colors.onDrawMuted,
                modifier = Modifier.weight(1f),
            )
            StatusChip(stringResource(R.string.lock_in_sha256), ChipTone.Accent)
        }
        Text(
            drawCode.ltrIsolated(),
            style = GiveawayTheme.typography.code.copy(textDirection = TextDirection.Ltr),
            color = colors.onDrawBackground,
            modifier = Modifier.fillMaxWidth().testTag("lock_in:code"),
        )
        PrimaryButton(
            text = stringResource(if (copied) R.string.lock_in_copied else R.string.lock_in_copy),
            onClick = {
                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(clipLabel, drawCode))) }
                copied = true
            },
            onStage = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Steps(state: LockInUiState) {
    val closes = state.closesAt?.let { formatDateTime(it) }.orEmpty()
    val steps = listOf(
        stringResource(R.string.lock_in_step_copy),
        stringResource(R.string.lock_in_step_paste),
        stringResource(R.string.lock_in_step_return, closes),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        steps.forEachIndexed { index, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier.size(28.dp).background(GiveawayTheme.colors.accentSoft, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        formatCount(index + 1),
                        style = GiveawayTheme.typography.captionSmall,
                        color = GiveawayTheme.colors.accentOnSoft,
                    )
                }
                Text(
                    step,
                    style = GiveawayTheme.typography.body,
                    color = GiveawayTheme.colors.onBackground,
                    modifier = Modifier.weight(1f).padding(top = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun ConfirmRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = GiveawayTheme.colors.onBackground,
                checkmarkColor = GiveawayTheme.colors.background,
            ),
        )
        Text(
            stringResource(R.string.lock_in_confirm),
            style = GiveawayTheme.typography.bodyStrong,
            color = GiveawayTheme.colors.onBackground,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
