package app.giveaway.feature.create

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giveaway.core.designsystem.GiveawayDimens
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.designsystem.component.GiveawayCard
import app.giveaway.core.designsystem.component.NoticeCard
import app.giveaway.core.designsystem.component.NoticeTone
import app.giveaway.core.designsystem.component.PrimaryButton
import app.giveaway.core.designsystem.component.SettingsRow
import app.giveaway.core.designsystem.component.SettingsSwitchRow
import app.giveaway.core.designsystem.component.Stepper
import app.giveaway.core.designsystem.component.WizardHeader
import app.giveaway.core.designsystem.formatDateTime

@Composable
internal fun SetRulesScreen(
    onBack: () -> Unit,
    onSaved: (giveawayId: Long) -> Unit,
    viewModel: SetRulesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.saved.collect(onSaved) }
    val fallbackTitle = stringResource(R.string.wizard_new_giveaway)
    SetRulesScreen(
        state = state,
        onBack = onBack,
        onFormChange = viewModel::onFormChange,
        onContinue = { viewModel.onContinue(fallbackTitle) },
    )
}

/** S7 Set rules (spec): what makes an entry valid, fairness options, the deadline, and how many to pick. */
@Composable
internal fun SetRulesScreen(
    state: SetRulesUiState,
    onBack: () -> Unit,
    onFormChange: ((RulesForm) -> RulesForm) -> Unit,
    onContinue: () -> Unit,
) {
    val form = state.form
    val errors = if (state.showErrors) state.errors else emptySet()
    var pickingDeadline by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GiveawayTheme.colors.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(GiveawayDimens.screenPadding)
            .testTag("screen:S7"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WizardHeader(title = stringResource(R.string.wizard_new_giveaway), step = 2, onBack = onBack)
        Text(
            stringResource(R.string.set_rules_title),
            style = GiveawayTheme.typography.display,
            color = GiveawayTheme.colors.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        EntryRules(form, errors, onFormChange)
        FairnessSection(form, onFormChange, onPickDeadline = { pickingDeadline = true })
        ErrorText(RulesError.CLOSES_IN_PAST in errors, R.string.rules_error_closes_in_past)
        WinnersSection(form, onFormChange)
        ErrorText(RulesError.NOBODY_TO_PICK in errors, R.string.rules_error_nobody)
        NoticeCard(
            title = stringResource(R.string.rules_manual_checks_title),
            body = stringResource(R.string.rules_manual_checks_body),
            tone = NoticeTone.Info,
        )
        PrimaryButton(
            text = stringResource(R.string.wizard_continue),
            onClick = onContinue,
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (pickingDeadline) {
        DeadlinePicker(
            initial = form.closesAt,
            onPicked = { at -> onFormChange { it.copy(closesAt = at) } },
            onDismiss = { pickingDeadline = false },
        )
    }
}

@Composable
private fun FairnessSection(
    form: RulesForm,
    onFormChange: ((RulesForm) -> RulesForm) -> Unit,
    onPickDeadline: () -> Unit,
) {
    Section(stringResource(R.string.rules_fairness)) {
        SettingsSwitchRow(
            stringResource(R.string.rules_one_per_person),
            checked = form.onePerPerson,
            onCheckedChange = { on -> onFormChange { it.copy(onePerPerson = on) } },
        )
        Divider()
        SettingsSwitchRow(
            stringResource(R.string.rules_exclude_past_winners),
            checked = form.excludePastWinners,
            onCheckedChange = { on -> onFormChange { it.copy(excludePastWinners = on) } },
        )
        Divider()
        SettingsSwitchRow(
            stringResource(R.string.rules_exclude_blocklist),
            checked = form.excludeBlocklist,
            onCheckedChange = { on -> onFormChange { it.copy(excludeBlocklist = on) } },
        )
        Divider()
        SettingsRow(
            stringResource(R.string.rules_closes),
            value = formatDateTime(form.closesAt),
            onClick = onPickDeadline,
        )
    }
}

@Composable
private fun WinnersSection(form: RulesForm, onFormChange: ((RulesForm) -> RulesForm) -> Unit) {
    Section(stringResource(R.string.rules_winners_section)) {
        StepperRow(stringResource(R.string.rules_winners), form.winners, RulesForm.WINNERS_RANGE) { n ->
            onFormChange { it.copy(winners = n) }
        }
        Divider()
        StepperRow(stringResource(R.string.rules_alternates), form.alternates, RulesForm.ALTERNATES_RANGE) { n ->
            onFormChange { it.copy(alternates = n) }
        }
    }
}

@Composable
private fun EntryRules(
    form: RulesForm,
    errors: Set<RulesError>,
    onFormChange: ((RulesForm) -> RulesForm) -> Unit,
) {
    Section(stringResource(R.string.rules_valid_entry)) {
        StepperRow(stringResource(R.string.rules_mentions), form.minMentions, RulesForm.MENTIONS_RANGE) { n ->
            onFormChange { it.copy(minMentions = n) }
        }
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val hashtagError = RulesError.HASHTAG_FORMAT in errors
            OutlinedTextField(
                value = form.hashtag,
                onValueChange = { text -> onFormChange { it.copy(hashtag = text) } },
                label = { Text(stringResource(R.string.rules_hashtag)) },
                placeholder = { Text(stringResource(R.string.rules_hashtag_example)) },
                isError = hashtagError,
                supportingText = if (hashtagError) {
                    { Text(stringResource(R.string.rules_error_hashtag)) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().testTag("rules:hashtag"),
            )
            OutlinedTextField(
                value = form.keyword,
                onValueChange = { text -> onFormChange { it.copy(keyword = text) } },
                label = { Text(stringResource(R.string.rules_keyword)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().testTag("rules:keyword"),
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title.uppercase(),
            style = GiveawayTheme.typography.overline,
            color = GiveawayTheme.colors.onMuted,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
        )
        GiveawayCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) { content() }
    }
}

/** Label at the start, stepper at the end, like a settings row (spec: Components, Stepper). */
@Composable
private fun StepperRow(label: String, value: Int, range: IntRange, onValueChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = GiveawayTheme.typography.body,
            color = GiveawayTheme.colors.onBackground,
            modifier = Modifier.weight(1f),
        )
        Stepper(value = value, onValueChange = onValueChange, range = range, label = label)
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = GiveawayTheme.colors.outline)

@Composable
private fun ErrorText(show: Boolean, text: Int) {
    if (!show) return
    Text(
        stringResource(text),
        style = GiveawayTheme.typography.caption,
        color = GiveawayTheme.colors.danger,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}
