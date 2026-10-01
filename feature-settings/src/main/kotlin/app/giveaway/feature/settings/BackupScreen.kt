package app.giveaway.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giveaway.core.designsystem.GiveawayDimens
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.designsystem.component.NoticeCard
import app.giveaway.core.designsystem.component.NoticeTone
import app.giveaway.core.designsystem.component.PrimaryButton
import app.giveaway.core.designsystem.component.SecondaryButton
import java.time.LocalDate
import app.giveaway.core.designsystem.R as DesignR

@Composable
internal fun BackupScreen(onBack: () -> Unit, viewModel: BackupViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    var password by rememberSaveable { mutableStateOf("") }
    val fileName = stringResource(R.string.backup_file_name, LocalDate.now().toString())
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        if (it != null) {
            viewModel.export(it, password.toCharArray())
            password = ""
        }
    }
    BackupScreen(
        status = status,
        password = password,
        onPassword = { password = it },
        onCreate = { create.launch(fileName) },
        onBack = onBack,
    )
}

/** Backup export (spec: S5): the password twice with a strength meter, then the system save dialog. */
@Composable
internal fun BackupScreen(
    status: BackupViewModel.Status,
    password: String,
    onPassword: (String) -> Unit,
    onCreate: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmation by rememberSaveable { mutableStateOf("") }
    val strength = PasswordStrength.of(password)
    val matches = password == confirmation
    SettingsPage(stringResource(R.string.backup_title), onBack, "screen:backup") {
        Text(stringResource(R.string.backup_body), style = GiveawayTheme.typography.body)
        NoticeCard(
            title = stringResource(R.string.backup_keep_password_title),
            body = stringResource(R.string.backup_keep_password_body),
            tone = NoticeTone.Info,
        )
        PasswordField(password, onPassword, stringResource(R.string.backup_password), "backup:password")
        StrengthMeter(strength)
        val again = stringResource(R.string.backup_password_again)
        PasswordField(confirmation, { confirmation = it }, again, "backup:confirm")
        if (confirmation.isNotEmpty() && !matches) {
            Text(
                stringResource(R.string.backup_mismatch),
                style = GiveawayTheme.typography.bodyStrong,
                color = GiveawayTheme.colors.danger,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        when (status) {
            BackupViewModel.Status.WORKING ->
                Text(stringResource(R.string.backup_working), style = GiveawayTheme.typography.body)
            BackupViewModel.Status.SAVED -> NoticeCard(stringResource(R.string.backup_saved), null, NoticeTone.Success)
            BackupViewModel.Status.FAILED ->
                NoticeCard(stringResource(R.string.backup_failed), null, NoticeTone.Warning)
            BackupViewModel.Status.IDLE -> Unit
        }
        PrimaryButton(
            text = stringResource(R.string.backup_create),
            onClick = onCreate,
            enabled = strength.acceptable && matches && status != BackupViewModel.Status.WORKING,
            modifier = Modifier.fillMaxWidth(),
        )
        if (status == BackupViewModel.Status.SAVED) {
            SecondaryButton(stringResource(R.string.backup_done), onClick = onBack, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Four segments filled by strength, with the word beside them, so color is never the only signal. */
@Composable
private fun StrengthMeter(strength: PasswordStrength) {
    val colors = GiveawayTheme.colors
    val filled = strength.ordinal
    val tone = when (strength) {
        PasswordStrength.NONE, PasswordStrength.WEAK -> colors.danger
        PasswordStrength.FAIR -> colors.accent
        PasswordStrength.GOOD, PasswordStrength.STRONG -> colors.success
    }
    val label = when (strength) {
        PasswordStrength.NONE -> R.string.backup_strength_none
        PasswordStrength.WEAK -> R.string.backup_strength_weak
        PasswordStrength.FAIR -> R.string.backup_strength_fair
        PasswordStrength.GOOD -> R.string.backup_strength_good
        PasswordStrength.STRONG -> R.string.backup_strength_strong
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(SEGMENTS) { i ->
            val color = if (i < filled) tone else colors.surfaceMuted
            Box(Modifier.weight(1f).height(6.dp).background(color, CircleShape))
        }
        Text(
            stringResource(label),
            style = GiveawayTheme.typography.caption,
            color = if (strength == PasswordStrength.NONE) colors.onMuted else colors.onBackground,
            modifier = Modifier.padding(start = 6.dp).testTag("backup:strength"),
        )
    }
}

private const val SEGMENTS = 4

@Composable
internal fun PasswordField(value: String, onValueChange: (String) -> Unit, label: String, tag: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

/** A Settings sub-page: back button, title and a scrolling column. */
@Composable
internal fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    tag: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GiveawayTheme.colors.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(GiveawayDimens.screenPadding)
            .testTag(tag),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(DesignR.drawable.ic_arrow_back), stringResource(DesignR.string.navigate_back))
            }
            Text(
                title,
                style = GiveawayTheme.typography.display,
                color = GiveawayTheme.colors.onBackground,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        }
        content()
    }
}
