package app.giveaway.feature.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giveaway.core.data.backup.BackupSummary
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.designsystem.component.GiveawayCard
import app.giveaway.core.designsystem.component.NoticeCard
import app.giveaway.core.designsystem.component.NoticeTone
import app.giveaway.core.designsystem.component.PrimaryButton
import app.giveaway.core.designsystem.component.SecondaryButton
import app.giveaway.core.designsystem.formatCount
import app.giveaway.core.designsystem.formatDateTime

@Composable
internal fun RestoreScreen(
    onBack: () -> Unit,
    onRestored: () -> Unit,
    viewModel: RestoreViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onFile(uri)
    }
    LaunchedEffect(state.restored) { if (state.restored) onRestored() }
    val fileName = remember(state.file) { state.file?.let { displayName(context, it) } }
    RestoreScreen(
        state = state,
        fileName = fileName,
        actions = RestoreActions(
            onPickFile = { pick.launch(arrayOf("*/*")) },
            onCheck = { viewModel.check(it.toCharArray()) },
            onRestore = { viewModel.restore(it.toCharArray()) },
            onBack = onBack,
        ),
    )
}

internal data class RestoreActions(
    val onPickFile: () -> Unit,
    val onCheck: (password: String) -> Unit,
    val onRestore: (password: String) -> Unit,
    val onBack: () -> Unit,
)

private fun displayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }
}.getOrNull() ?: uri.lastPathSegment

/** Restore (spec: S5): pick the file, enter the password, then confirm replacing the data on this phone. */
@Composable
internal fun RestoreScreen(state: RestoreState, fileName: String?, actions: RestoreActions) {
    // Not saveable: a password must never go into saved instance state.
    var password by remember { mutableStateOf("") }
    SettingsPage(stringResource(R.string.restore_title), actions.onBack, "screen:restore") {
        Text(stringResource(R.string.restore_body), style = GiveawayTheme.typography.body)
        SecondaryButton(
            text = stringResource(if (state.file == null) R.string.restore_pick else R.string.restore_pick_other),
            onClick = actions.onPickFile,
            modifier = Modifier.fillMaxWidth(),
        )
        fileName?.let { Text(it, style = GiveawayTheme.typography.code, color = GiveawayTheme.colors.onMuted) }
        PasswordField(password, { password = it }, stringResource(R.string.backup_password), "restore:password")
        state.error?.let { RestoreErrorText(it) }
        if (state.summary == null) {
            PrimaryButton(
                text = stringResource(if (state.working) R.string.restore_checking else R.string.restore_check),
                onClick = { actions.onCheck(password) },
                enabled = state.file != null && password.isNotEmpty() && !state.working,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            ConfirmRestore(state.summary, state.working) { actions.onRestore(password) }
        }
    }
}

@Composable
private fun RestoreErrorText(error: RestoreError) {
    val text = when (error) {
        RestoreError.WRONG_PASSWORD_OR_DAMAGED -> R.string.restore_error_password
        RestoreError.NOT_A_BACKUP -> R.string.restore_error_not_backup
        RestoreError.NEWER_VERSION -> R.string.restore_error_newer
        RestoreError.FAILED -> R.string.restore_error_failed
    }
    Text(
        stringResource(text),
        style = GiveawayTheme.typography.bodyStrong,
        color = GiveawayTheme.colors.danger,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** What the backup holds, and the warning that it replaces everything (spec: confirm replacing current data). */
@Composable
private fun ConfirmRestore(summary: BackupSummary, working: Boolean, onRestore: () -> Unit) {
    GiveawayCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.restore_from, formatDateTime(summary.createdAt)),
                style = GiveawayTheme.typography.bodyStrong,
            )
            Text(
                pluralStringResource(R.plurals.restore_giveaways, summary.giveaways, formatCount(summary.giveaways)),
                style = GiveawayTheme.typography.body,
                color = GiveawayTheme.colors.onMuted,
            )
        }
    }
    NoticeCard(
        title = stringResource(R.string.restore_warning_title),
        body = stringResource(R.string.restore_warning_body),
        tone = NoticeTone.Warning,
    )
    PrimaryButton(
        text = stringResource(if (working) R.string.restore_working else R.string.restore_confirm),
        onClick = onRestore,
        enabled = !working,
        modifier = Modifier.fillMaxWidth().testTag("restore:confirm"),
    )
}
