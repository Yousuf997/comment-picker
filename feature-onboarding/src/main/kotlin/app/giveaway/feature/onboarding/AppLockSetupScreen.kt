package app.giveaway.feature.onboarding

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giveaway.core.designsystem.GiveawayDimens
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.designsystem.component.NoticeCard
import app.giveaway.core.designsystem.component.NoticeTone
import app.giveaway.core.designsystem.component.PrimaryButton
import app.giveaway.core.designsystem.component.SecondaryButton
import app.giveaway.core.security.lock.BiometricGate
import app.giveaway.core.security.lock.PinStore

/** S3 with storage and BiometricPrompt wired up; [onDone] continues to S4. */
@Composable
internal fun AppLockSetupScreen(onDone: () -> Unit, viewModel: AppLockSetupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as? FragmentActivity
    val promptTitle = stringResource(R.string.app_lock_prompt_title)
    val promptSubtitle = stringResource(R.string.app_lock_prompt_subtitle)
    val promptCancel = stringResource(R.string.app_lock_prompt_cancel)
    LaunchedEffect(viewModel) { viewModel.done.collect { onDone() } }
    AppLockSetupScreen(
        state = state,
        onUseBiometrics = {
            if (activity != null) {
                BiometricGate.authenticate(
                    activity,
                    promptTitle,
                    promptSubtitle,
                    promptCancel,
                    viewModel::onBiometricResult,
                )
            }
        },
        onChoosePin = viewModel::onChoosePin,
        onPinSubmitted = viewModel::onPinSubmitted,
        onBack = viewModel::onBack,
        onSkip = viewModel::onSkip,
    )
}

/** S3 App lock setup (spec): offered once, after connecting Instagram. */
@Composable
internal fun AppLockSetupScreen(
    state: AppLockSetupState,
    onUseBiometrics: () -> Unit,
    onChoosePin: () -> Unit,
    onPinSubmitted: (String) -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GiveawayTheme.colors.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(GiveawayDimens.screenPaddingWide)
            .testTag("screen:S3"),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        when (val step = state.step) {
            AppLockStep.Choose -> ChooseLock(state, onUseBiometrics, onChoosePin, onSkip)
            is AppLockStep.EnterPin -> EnterPin(step, onPinSubmitted, onBack)
            AppLockStep.Saving -> CircularProgressIndicator(
                color = GiveawayTheme.colors.accent,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun ChooseLock(
    state: AppLockSetupState,
    onUseBiometrics: () -> Unit,
    onChoosePin: () -> Unit,
    onSkip: () -> Unit,
) {
    val colors = GiveawayTheme.colors
    Heading(stringResource(R.string.app_lock_title))
    Text(stringResource(R.string.app_lock_body), style = GiveawayTheme.typography.body, color = colors.onMuted)
    if (state.biometricFailed) {
        NoticeCard(
            title = stringResource(R.string.app_lock_biometric_failed),
            body = null,
            tone = NoticeTone.Warning,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    if (state.biometricsAvailable) {
        PrimaryButton(
            stringResource(R.string.app_lock_use_biometrics),
            onClick = onUseBiometrics,
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryButton(
            stringResource(R.string.app_lock_set_pin),
            onClick = onChoosePin,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Text(
            stringResource(R.string.app_lock_no_biometrics),
            style = GiveawayTheme.typography.body,
            color = colors.onMuted,
        )
        PrimaryButton(
            stringResource(R.string.app_lock_set_pin),
            onClick = onChoosePin,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.app_lock_skip),
            style = GiveawayTheme.typography.bodyStrong,
            color = colors.onBackground,
        )
    }
}

@Composable
private fun EnterPin(step: AppLockStep.EnterPin, onPinSubmitted: (String) -> Unit, onBack: () -> Unit) {
    // Cleared whenever the step changes, so the confirmation starts empty.
    var pin by remember(step) { mutableStateOf("") }
    Heading(stringResource(if (step.confirming) R.string.app_lock_pin_confirm_title else R.string.app_lock_pin_title))
    if (step.mismatch) {
        Text(
            stringResource(R.string.app_lock_pin_mismatch),
            style = GiveawayTheme.typography.bodyStrong,
            color = GiveawayTheme.colors.danger,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    OutlinedTextField(
        value = pin,
        onValueChange = { value -> pin = value.filter(Char::isDigit).take(PinStore.PIN_LENGTH) },
        label = { Text(stringResource(R.string.app_lock_pin_label)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        textStyle = GiveawayTheme.typography.codeLarge,
        modifier = Modifier.fillMaxWidth().testTag("pin"),
    )
    PrimaryButton(
        stringResource(R.string.app_lock_continue),
        onClick = { onPinSubmitted(pin) },
        enabled = PinStore.isValidPin(pin),
        modifier = Modifier.fillMaxWidth(),
    )
    SecondaryButton(stringResource(R.string.app_lock_back), onClick = onBack, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Heading(text: String) = Text(
    text,
    style = GiveawayTheme.typography.display,
    color = GiveawayTheme.colors.onBackground,
    modifier = Modifier.semantics { heading() },
)
