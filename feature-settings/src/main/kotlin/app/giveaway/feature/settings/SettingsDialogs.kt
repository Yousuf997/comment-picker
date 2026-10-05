package app.giveaway.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.giveaway.core.data.db.SettingsEntity
import app.giveaway.core.designsystem.GiveawayTheme

/** The S5 dialogs: confirmations (app lock off, disconnect, deleting), and the option pickers. */
@Composable
internal fun SettingsDialog(
    dialog: Dialog,
    state: SettingsUiState,
    handlers: SettingsHandlers,
    onDismiss: () -> Unit,
    onNext: (Dialog) -> Unit,
) {
    when (dialog) {
        Dialog.LOCK_AFTER, Dialog.AUTO_DELETE, Dialog.LANGUAGE ->
            ChoiceDialog(dialog, state.settings, handlers, onDismiss)
        Dialog.LOCK_OFF -> Confirm(
            title = R.string.settings_lock_off_title,
            body = R.string.settings_lock_off_body,
            confirm = R.string.settings_turn_off,
            onConfirm = handlers.onTurnOffAppLock,
            onDismiss = onDismiss,
        )
        Dialog.DISCONNECT -> Confirm(
            title = R.string.settings_disconnect_title,
            body = R.string.settings_disconnect_body,
            confirm = R.string.settings_disconnect,
            onConfirm = handlers.onDisconnect,
            onDismiss = onDismiss,
        )
        // Two steps, so a slip can't wipe the phone (spec: S5, double confirm).
        Dialog.DELETE_FIRST -> Confirm(
            title = R.string.settings_delete_first_title,
            body = R.string.settings_delete_first_body,
            confirm = R.string.settings_delete_continue,
            onConfirm = { onNext(Dialog.DELETE_SECOND) },
            onDismiss = onDismiss,
        )
        Dialog.DELETE_SECOND -> Confirm(
            title = R.string.settings_delete_second_title,
            body = R.string.settings_delete_second_body,
            confirm = R.string.settings_delete_everything,
            onConfirm = handlers.onDeleteEverything,
            onDismiss = onDismiss,
        )
        Dialog.DELETE_GIVEAWAYS -> Confirm(
            title = stringResource(R.string.settings_delete_giveaways_title),
            body = pluralStringResource(
                R.plurals.settings_delete_giveaways_body,
                state.giveawayCount,
                state.giveawayCount,
            ),
            confirm = R.string.settings_delete_giveaways_confirm,
            onConfirm = handlers.onDeleteAllGiveaways,
            onDismiss = onDismiss,
        )
    }
}

/** The option pickers: lock timing, auto-delete and language. */
@Composable
private fun ChoiceDialog(dialog: Dialog, prefs: SettingsEntity, handlers: SettingsHandlers, onDismiss: () -> Unit) {
    when (dialog) {
        Dialog.LOCK_AFTER -> Choice(
            title = R.string.settings_lock_after,
            options = SettingsViewModel.LOCK_AFTER_OPTIONS,
            selected = prefs.lockAfterSeconds,
            label = ::lockAfterLabel,
            onSelect = handlers.onLockAfter,
            onDismiss = onDismiss,
        )
        Dialog.AUTO_DELETE -> Choice(
            title = R.string.settings_auto_delete,
            options = SettingsViewModel.AUTO_DELETE_OPTIONS,
            selected = prefs.autoDeleteDays,
            label = ::autoDeleteLabel,
            onSelect = handlers.onAutoDelete,
            onDismiss = onDismiss,
        )
        Dialog.LANGUAGE -> Choice(
            title = R.string.settings_language,
            options = SettingsViewModel.LANGUAGE_OPTIONS,
            selected = prefs.language,
            label = ::languageLabel,
            onSelect = handlers.onLanguage,
            onDismiss = onDismiss,
        )
        else -> Unit
    }
}

@Composable
private fun Confirm(title: Int, body: Int, confirm: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) =
    Confirm(stringResource(title), stringResource(body), confirm, onConfirm, onDismiss)

@Composable
private fun Confirm(title: String, body: String, confirm: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) =
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            // Dismiss first, so a confirm that opens the next dialog (delete everything) keeps it open.
            TextButton(onClick = { onDismiss(); onConfirm() }) {
                Text(stringResource(confirm), color = GiveawayTheme.colors.danger)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )

@Composable
private fun <T> Choice(
    title: Int,
    options: List<T>,
    selected: T,
    label: (T) -> Int,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(title)) },
    text = {
        Column {
            options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = option == selected, role = Role.RadioButton) {
                            onSelect(option)
                            onDismiss()
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = option == selected, onClick = null)
                    Text(stringResource(label(option)), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
)

internal fun lockAfterLabel(seconds: Int): Int = when {
    seconds <= SECONDS_30 -> R.string.settings_after_30s
    seconds <= SECONDS_60 -> R.string.settings_after_1m
    seconds <= SECONDS_300 -> R.string.settings_after_5m
    else -> R.string.settings_after_15m
}

internal fun autoDeleteLabel(days: Int): Int = when {
    days <= DAYS_30 -> R.string.settings_days_30
    days <= DAYS_90 -> R.string.settings_days_90
    days <= DAYS_180 -> R.string.settings_days_180
    else -> R.string.settings_days_365
}

internal fun languageLabel(tag: String?): Int = when (tag) {
    "en" -> R.string.settings_language_en
    "ar" -> R.string.settings_language_ar
    else -> R.string.settings_language_system
}

private const val SECONDS_30 = 30
private const val SECONDS_60 = 60
private const val SECONDS_300 = 300
private const val DAYS_30 = 30
private const val DAYS_90 = 90
private const val DAYS_180 = 180
