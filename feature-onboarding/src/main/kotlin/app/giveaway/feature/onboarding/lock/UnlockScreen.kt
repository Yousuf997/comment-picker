package app.giveaway.feature.onboarding.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giveaway.core.data.db.AppLockMethod
import app.giveaway.core.designsystem.GiveawayDimens
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.designsystem.component.PrimaryButton
import app.giveaway.core.security.lock.BiometricGate
import app.giveaway.core.security.lock.PinStore
import app.giveaway.feature.onboarding.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class LockGateViewModel @Inject constructor(controller: AppLockController) : ViewModel() {
    val status = controller.status
}

/**
 * Shows [content] when unlocked and the lock screen over it otherwise. The content stays composed underneath (so
 * navigation state survives locking) but is hidden from TalkBack while locked.
 */
@Composable
fun AppLockGate(viewModel: LockGateViewModel = hiltViewModel(), content: @Composable () -> Unit) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val unlocked = status == LockStatus.UNLOCKED
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().then(if (unlocked) Modifier else Modifier.clearAndSetSemantics {})) { content() }
        when (status) {
            LockStatus.CHECKING -> Box(Modifier.fillMaxSize().background(GiveawayTheme.colors.background))
            LockStatus.LOCKED -> UnlockScreen()
            LockStatus.UNLOCKED -> Unit
        }
    }
}

@Composable
internal fun UnlockScreen(viewModel: UnlockViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? FragmentActivity
    val title = stringResource(R.string.app_lock_prompt_title)
    val subtitle = stringResource(R.string.lock_prompt_subtitle)
    val cancel = stringResource(R.string.app_lock_prompt_cancel)
    val promptBiometrics = {
        if (activity != null) {
            BiometricGate.authenticate(activity, title, subtitle, cancel, viewModel::onBiometricResult)
        }
    }
    // Biometric lock asks straight away; the button is there if the user dismissed the prompt.
    LaunchedEffect(state.method) { if (state.method == AppLockMethod.BIOMETRIC) promptBiometrics() }
    UnlockScreen(state = state, onUnlockWithBiometrics = promptBiometrics, onPinSubmitted = viewModel::onPinSubmitted)
}

@Composable
internal fun UnlockScreen(
    state: UnlockState,
    onUnlockWithBiometrics: () -> Unit,
    onPinSubmitted: (String) -> Unit,
    now: () -> Instant = Instant::now,
) {
    val colors = GiveawayTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .padding(GiveawayDimens.screenPaddingWide)
            .testTag("screen:lock"),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            stringResource(R.string.lock_title),
            style = GiveawayTheme.typography.display,
            color = colors.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        if (state.method == AppLockMethod.PIN) {
            PinEntry(state, onPinSubmitted, now)
        } else {
            if (state.biometricFailed) Message(stringResource(R.string.lock_biometric_failed))
            PrimaryButton(
                stringResource(R.string.lock_unlock),
                onClick = onUnlockWithBiometrics,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PinEntry(state: UnlockState, onPinSubmitted: (String) -> Unit, now: () -> Instant) {
    var pin by remember(state.attemptsLeft, state.lockedUntil) { mutableStateOf("") }
    val secondsLeft = rememberSecondsUntil(state.lockedUntil, now)
    when {
        secondsLeft > 0 -> Message(stringResource(R.string.lock_locked_out, formatWait(secondsLeft)))
        state.attemptsLeft != null ->
            Message(pluralStringResource(R.plurals.lock_wrong_pin, state.attemptsLeft, state.attemptsLeft))
    }
    OutlinedTextField(
        value = pin,
        onValueChange = { value -> pin = value.filter(Char::isDigit).take(PinStore.PIN_LENGTH) },
        label = { Text(stringResource(R.string.app_lock_pin_label)) },
        singleLine = true,
        enabled = secondsLeft <= 0,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        textStyle = GiveawayTheme.typography.codeLarge,
        modifier = Modifier.fillMaxWidth().testTag("pin"),
    )
    PrimaryButton(
        stringResource(R.string.lock_unlock),
        onClick = { onPinSubmitted(pin) },
        enabled = secondsLeft <= 0 && !state.checking && PinStore.isValidPin(pin),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Seconds until [until], ticking once a second while positive. */
@Composable
private fun rememberSecondsUntil(until: Instant?, now: () -> Instant): Long {
    var seconds by remember(until) { mutableLongStateOf(secondsBetween(now(), until)) }
    LaunchedEffect(until) {
        while (seconds > 0) {
            delay(TICK_MILLIS)
            seconds = secondsBetween(now(), until)
        }
    }
    return seconds
}

private fun secondsBetween(from: Instant, until: Instant?): Long =
    if (until == null) 0 else (Duration.between(from, until).toMillis() + MILLIS_ROUND_UP) / MILLIS_PER_SECOND

private fun formatWait(seconds: Long): String =
    String.format(Locale.getDefault(), "%d:%02d", seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)

@Composable
private fun Message(text: String) = Text(
    text,
    style = GiveawayTheme.typography.bodyStrong,
    color = GiveawayTheme.colors.danger,
    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
)

private const val TICK_MILLIS = 1_000L
private const val MILLIS_PER_SECOND = 1_000L
private const val MILLIS_ROUND_UP = 999L
private const val SECONDS_PER_MINUTE = 60
