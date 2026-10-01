package app.giveaway.feature.create

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.giveaway.core.data.account.AccountRepository
import app.giveaway.core.data.giveaway.GiveawayRepository
import app.giveaway.core.instagram.api.IgMedia
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class SetRulesUiState(
    val form: RulesForm,
    /** Problems with the form; shown once the user has tried to continue, so a fresh form isn't all red. */
    val errors: Set<RulesError> = emptySet(),
    val showErrors: Boolean = false,
    val saving: Boolean = false,
)

/** S7 Set rules (wizard step 2): creates the DRAFT giveaway with its rules (plan C-12). */
@HiltViewModel
class SetRulesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val giveaways: GiveawayRepository,
    private val accounts: AccountRepository,
    private val clock: Clock,
) : ViewModel() {

    private val media: IgMedia = savedStateHandle.toRoute<SetRulesRoute>().toMedia()

    private val uiState = MutableStateFlow(SetRulesUiState(RulesForm(closesAt = defaultClosesAt())))
    val state: StateFlow<SetRulesUiState> = uiState.asStateFlow()

    private val savedChannel = Channel<Long>(Channel.BUFFERED)

    /** The draft's ID once saved; S8 opens next. */
    val saved: Flow<Long> = savedChannel.receiveAsFlow()

    /** Set after the first save, so coming back from S8 and continuing again updates the same draft. */
    private var draftId: Long? = null

    fun onFormChange(transform: (RulesForm) -> RulesForm) = uiState.update { current ->
        val form = transform(current.form)
        current.copy(form = form, errors = RulesValidation.validate(form, clock.instant()))
    }

    /** [fallbackTitle] names the giveaway when the post has no caption to take a title from. */
    fun onContinue(fallbackTitle: String) {
        val current = uiState.value
        if (current.saving) return
        val errors = RulesValidation.validate(current.form, clock.instant())
        if (errors.isNotEmpty()) {
            uiState.value = current.copy(errors = errors, showErrors = true)
            return
        }
        uiState.value = current.copy(errors = emptySet(), saving = true)
        viewModelScope.launch {
            val rules = current.form.toRules()
            val id = draftId?.also { giveaways.saveRules(it, rules) }
                ?: giveaways.createDraft(
                    media = media,
                    title = titleFrom(media.caption) ?: fallbackTitle,
                    ownerUsername = accounts.observeUsername().first().orEmpty(),
                    rules = rules,
                )
            draftId = id
            uiState.update { it.copy(saving = false) }
            savedChannel.send(id)
        }
    }

    /** A week from now, on the hour: room for entries without a long wait (plan A27). */
    private fun defaultClosesAt(): Instant = clock.instant()
        .plus(Duration.ofDays(DEFAULT_DAYS_OPEN))
        .atZone(ZoneId.systemDefault())
        .truncatedTo(ChronoUnit.HOURS)
        .toInstant()

    companion object {
        const val DEFAULT_DAYS_OPEN = 7L
        const val MAX_TITLE_LENGTH = 60

        /** The caption's first non-blank line, shortened at a word boundary. */
        fun titleFrom(caption: String?): String? {
            val line = caption?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() } ?: return null
            if (line.length <= MAX_TITLE_LENGTH) return line
            val cut = line.take(MAX_TITLE_LENGTH).substringBeforeLast(' ').ifBlank { line.take(MAX_TITLE_LENGTH) }
            return "${cut.trimEnd()}…"
        }
    }
}
