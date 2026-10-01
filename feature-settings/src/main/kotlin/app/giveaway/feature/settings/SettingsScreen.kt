package app.giveaway.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giveaway.core.data.db.SettingsEntity
import app.giveaway.core.designsystem.GiveawayDimens
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.designsystem.component.GiveawayCard
import app.giveaway.core.designsystem.component.SettingsRow
import app.giveaway.core.designsystem.component.SettingsSwitchRow
import app.giveaway.core.designsystem.handle
import app.giveaway.feature.settings.SettingsViewModel.Companion.lockOn
import app.giveaway.core.designsystem.R as DesignR

/** Callbacks from S5 to the rest of the app. */
data class SettingsActions(
    val onBack: () -> Unit,
    val onSetUpAppLock: () -> Unit,
    val onDisconnected: () -> Unit,
    val onExportBackup: () -> Unit,
    val onRestoreBackup: () -> Unit,
    val onDeleteEverything: () -> Unit,
    val onOpenLicenses: () -> Unit,
)

@Composable
internal fun SettingsScreen(actions: SettingsActions, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SettingsEvent.SetUpAppLock -> actions.onSetUpAppLock()
                SettingsEvent.Disconnected -> actions.onDisconnected()
            }
        }
    }
    val uriHandler = LocalUriHandler.current
    SettingsScreen(
        state = state,
        handlers = SettingsHandlers(
            onAppLock = viewModel::onAppLockSwitched,
            onTurnOffAppLock = { viewModel.turnOffAppLock() },
            onBlockScreenshots = viewModel::onBlockScreenshots,
            onLockAfter = viewModel::onLockAfter,
            onAutoDelete = viewModel::onAutoDeleteDays,
            onRecordDraws = viewModel::onRecordDraws,
            onLanguage = viewModel::onLanguage,
            onDisconnect = { viewModel.disconnect() },
            onOpenPrivacyPolicy = { uriHandler.openUri(BuildConfig.PRIVACY_POLICY_URL) },
        ),
        actions = actions,
    )
}

internal data class SettingsHandlers(
    val onAppLock: (Boolean) -> Unit,
    val onTurnOffAppLock: () -> Unit,
    val onBlockScreenshots: (Boolean) -> Unit,
    val onLockAfter: (Int) -> Unit,
    val onAutoDelete: (Int) -> Unit,
    val onRecordDraws: (Boolean) -> Unit,
    val onLanguage: (String?) -> Unit,
    val onDisconnect: () -> Unit,
    val onOpenPrivacyPolicy: () -> Unit,
)

private enum class Dialog { LOCK_OFF, LOCK_AFTER, AUTO_DELETE, LANGUAGE, DISCONNECT }

/** S5 Settings (spec). */
@Composable
internal fun SettingsScreen(state: SettingsUiState, handlers: SettingsHandlers, actions: SettingsActions) {
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    val open: (Dialog) -> Unit = { dialog = it }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GiveawayTheme.colors.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(GiveawayDimens.screenPadding)
            .testTag("screen:S5"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onBack) {
                Icon(painterResource(DesignR.drawable.ic_arrow_back), stringResource(DesignR.string.navigate_back))
            }
            Text(
                stringResource(R.string.settings_title),
                style = GiveawayTheme.typography.display,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        }
        SecurityGroup(state.settings, handlers, open)
        DataGroup(state.settings, actions, open)
        Group(stringResource(R.string.settings_draw)) {
            SettingsSwitchRow(
                stringResource(R.string.settings_record_draws),
                checked = state.settings.recordDrawsByDefault,
                onCheckedChange = handlers.onRecordDraws,
            )
        }
        InstagramGroup(state.username, open)
        GiveawayCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
            SettingsRow(
                stringResource(R.string.settings_delete_everything),
                destructive = true,
                onClick = actions.onDeleteEverything,
            )
        }
        AboutGroup(state.settings, handlers, actions, open)
    }
    dialog?.let { SettingsDialog(it, state.settings, handlers, onDismiss = { dialog = null }) }
}

@Composable
private fun SecurityGroup(prefs: SettingsEntity, handlers: SettingsHandlers, open: (Dialog) -> Unit) {
    Group(stringResource(R.string.settings_security)) {
        SettingsSwitchRow(
            stringResource(R.string.settings_app_lock),
            checked = prefs.lockOn(),
            onCheckedChange = { on -> if (on) handlers.onAppLock(true) else open(Dialog.LOCK_OFF) },
        )
        Divider()
        SettingsSwitchRow(
            stringResource(R.string.settings_block_screenshots),
            checked = prefs.blockScreenshots,
            onCheckedChange = handlers.onBlockScreenshots,
        )
        Divider()
        SettingsRow(
            stringResource(R.string.settings_lock_after),
            value = stringResource(lockAfterLabel(prefs.lockAfterSeconds)),
            onClick = { open(Dialog.LOCK_AFTER) },
        )
    }
}

@Composable
private fun DataGroup(prefs: SettingsEntity, actions: SettingsActions, open: (Dialog) -> Unit) {
    Group(stringResource(R.string.settings_data), note = stringResource(R.string.settings_data_note)) {
        SettingsRow(
            stringResource(R.string.settings_auto_delete),
            value = stringResource(autoDeleteLabel(prefs.autoDeleteDays)),
            onClick = { open(Dialog.AUTO_DELETE) },
        )
        Divider()
        SettingsRow(stringResource(R.string.settings_export_backup), onClick = actions.onExportBackup)
        Divider()
        SettingsRow(stringResource(R.string.settings_restore_backup), onClick = actions.onRestoreBackup)
    }
}

@Composable
private fun InstagramGroup(username: String?, open: (Dialog) -> Unit) {
    Group(stringResource(R.string.settings_instagram)) {
        SettingsRow(
            stringResource(R.string.settings_connected_as),
            value = username?.let(::handle) ?: stringResource(R.string.settings_not_connected),
        )
        if (username != null) {
            Divider()
            SettingsRow(stringResource(R.string.settings_disconnect), onClick = { open(Dialog.DISCONNECT) })
        }
    }
}

@Composable
private fun AboutGroup(
    prefs: SettingsEntity,
    handlers: SettingsHandlers,
    actions: SettingsActions,
    open: (Dialog) -> Unit,
) {
    Group(stringResource(R.string.settings_about)) {
        SettingsRow(
            stringResource(R.string.settings_language),
            value = stringResource(languageLabel(prefs.language)),
            onClick = { open(Dialog.LANGUAGE) },
        )
        Divider()
        SettingsRow(stringResource(R.string.settings_privacy), onClick = handlers.onOpenPrivacyPolicy)
        Divider()
        SettingsRow(stringResource(R.string.settings_licenses), onClick = actions.onOpenLicenses)
        Divider()
        SettingsRow(stringResource(R.string.settings_version), value = versionName())
    }
}

@Composable
private fun SettingsDialog(dialog: Dialog, prefs: SettingsEntity, handlers: SettingsHandlers, onDismiss: () -> Unit) {
    when (dialog) {
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
    }
}


@Composable
private fun Group(title: String, note: String? = null, rows: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title.uppercase(),
            style = GiveawayTheme.typography.overline,
            color = GiveawayTheme.colors.onMuted,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
        )
        if (note != null) Text(note, style = GiveawayTheme.typography.caption, color = GiveawayTheme.colors.onMuted)
        GiveawayCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) { rows() }
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = GiveawayTheme.colors.outline)

@Composable
private fun Confirm(title: Int, body: Int, confirm: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(title)) },
    text = { Text(stringResource(body)) },
    confirmButton = {
        TextButton(onClick = { onConfirm(); onDismiss() }) {
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

@Composable
private fun versionName(): String {
    val context = LocalContext.current
    return remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
}

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
