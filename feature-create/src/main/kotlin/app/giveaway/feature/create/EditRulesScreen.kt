package app.giveaway.feature.create

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.giveaway.GiveawayEditor
import app.giveaway.core.data.giveaway.GiveawayRepository
import app.giveaway.core.data.giveaway.WizardProgress
import app.giveaway.core.designsystem.GiveawayTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

data class EditRulesState(
    /** Null until the saved rules are loaded. */
    val rules: SetRulesUiState? = null,
    val title: String? = null,
    /** Saving would clear a drawn result; the user confirms first (plan A31). */
    val confirmClear: Boolean = false,
)

/** S7 for an existing giveaway: its rules are editable at any stage (plan A31). */
@HiltViewModel
class EditRulesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val giveaways: GiveawayRepository,
    private val editor: GiveawayEditor,
    private val clock: Clock,
) : ViewModel() {
    private val giveawayId = savedStateHandle.toRoute<EditRulesRoute>().giveawayId

    private val stateFlow = MutableStateFlow(EditRulesState())
    val state: StateFlow<EditRulesState> = stateFlow.asStateFlow()

    private val savedChannel = Channel<Int>(Channel.BUFFERED)

    /** The step to open after saving: where the giveaway now is (the draw, when its winners were cleared). */
    val saved: Flow<Int> = savedChannel.receiveAsFlow()

    private var savedClosesAt: Instant? = null
    private var entriesOpen = true

    init {
        viewModelScope.launch {
            val giveaway = giveaways.get(giveawayId) ?: return@launch
            val rules = giveaways.rules(giveawayId) ?: return@launch
            savedClosesAt = rules.closesAt
            entriesOpen = giveaway.status == GiveawayStatus.DRAFT || giveaway.status == GiveawayStatus.COMMITTED
            stateFlow.update { it.copy(rules = SetRulesUiState(RulesForm.from(rules)), title = giveaway.title) }
        }
    }

    fun onFormChange(transform: (RulesForm) -> RulesForm) = stateFlow.update { current ->
        val rules = current.rules ?: return@update current
        val form = transform(rules.form)
        current.copy(rules = rules.copy(form = form, errors = validate(form)))
    }

    fun onSave() {
        val rules = stateFlow.value.rules ?: return
        if (rules.saving) return
        val errors = validate(rules.form)
        if (errors.isNotEmpty()) {
            stateFlow.update { it.copy(rules = rules.copy(errors = errors, showErrors = true)) }
            return
        }
        viewModelScope.launch {
            if (editor.clearsResult(giveawayId)) stateFlow.update { it.copy(confirmClear = true) } else save()
        }
    }

    fun confirmClear() {
        stateFlow.update { it.copy(confirmClear = false) }
        viewModelScope.launch { save() }
    }

    fun dismissClear() = stateFlow.update { it.copy(confirmClear = false) }

    private suspend fun save() {
        val rules = stateFlow.value.rules ?: return
        stateFlow.update { it.copy(rules = rules.copy(errors = emptySet(), saving = true)) }
        val cleared = editor.clearsResult(giveawayId)
        editor.saveRules(giveawayId, rules.form.toRules())
        stateFlow.update { current -> current.copy(rules = current.rules?.copy(saving = false)) }
        savedChannel.send(nextStep(giveaways.get(giveawayId)?.status, cleared))
    }

    private fun nextStep(status: GiveawayStatus?, cleared: Boolean) = when {
        cleared -> WizardProgress.DRAW
        status == GiveawayStatus.REVIEW -> WizardProgress.REVIEW
        status == GiveawayStatus.IMPORTING -> WizardProgress.IMPORT
        else -> WizardProgress.IMPORT
    }

    /** Only a new deadline for entries that are still open has to be in the future. */
    private fun validate(form: RulesForm) = RulesValidation.validate(
        form = form,
        now = clock.instant(),
        requireFutureClose = entriesOpen && form.closesAt != savedClosesAt,
    )
}

@Composable
internal fun EditRulesScreen(
    onBack: () -> Unit,
    onSaved: (nextStep: Int) -> Unit,
    viewModel: EditRulesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.saved.collect(onSaved) }
    val rules = state.rules
    if (rules == null) {
        Box(Modifier.fillMaxSize().background(GiveawayTheme.colors.background))
        return
    }
    SetRulesScreen(
        state = rules,
        onBack = onBack,
        onFormChange = viewModel::onFormChange,
        onContinue = viewModel::onSave,
        title = state.title,
        continueLabel = stringResource(R.string.edit_rules_save),
    )
    if (state.confirmClear) {
        ClearWinnersDialog(
            body = stringResource(R.string.clear_winners_rules_body),
            onConfirm = viewModel::confirmClear,
            onDismiss = viewModel::dismissClear,
        )
    }
}

/** Asked before a change after the draw clears the winners (plan A31). */
@Composable
internal fun ClearWinnersDialog(body: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clear_winners_title)) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("clear_winners:confirm")) {
                Text(stringResource(R.string.clear_winners_confirm), color = GiveawayTheme.colors.danger)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}
