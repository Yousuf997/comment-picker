package app.giveaway.feature.create

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.giveaway.core.data.db.GiveawayEntity
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.giveaway.GiveawayRepository
import app.giveaway.core.data.importing.ImportPhase
import app.giveaway.core.data.importing.ImportProgress
import app.giveaway.core.data.importing.ImportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/** One line of the S9 checklist. */
enum class StepState {
    PENDING,
    RUNNING,

    /** Waiting for the user: a short import after every retry, a deleted post or an expired sign-in. */
    STOPPED,
    DONE,
}

data class ImportUiState(
    val title: String? = null,
    val progress: ImportProgress = ImportProgress(ImportPhase.NOT_STARTED),
    /** Entries are still open: import starts only after the deadline (plan A15). */
    val opensAt: Instant? = null,
    val fetching: StepState = StepState.PENDING,
    /** Checking hashtags and mentions, removing duplicates and applying exclusions run as one pass (plan C-19). */
    val filtering: StepState = StepState.PENDING,
) {
    /** Review entries -> S10 once filtering is done (spec: S9). */
    val canReview: Boolean get() = filtering == StepState.DONE
}

/** S9 Import comments (plan C-18). The import itself runs in a foreground worker, so the user can leave. */
@HiltViewModel
class ImportCommentsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val giveaways: GiveawayRepository,
    private val imports: ImportRepository,
    private val clock: Clock,
) : ViewModel() {

    private val giveawayId = savedStateHandle.toRoute<ImportCommentsRoute>().giveawayId

    private val giveaway = giveaways.observeSummaries().map { list ->
        list.firstOrNull { it.giveaway.id == giveawayId }?.giveaway
    }

    val state: StateFlow<ImportUiState> = combine(giveaway.filterNotNull(), imports.observe(giveawayId)) { g, p ->
        uiState(g, p)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ImportUiState())

    init {
        viewModelScope.launch {
            val current = giveaway.filterNotNull().first()
            if (isImportable(current)) imports.start(giveawayId)
        }
    }

    /** "Retry now" on the warning card: runs again straight away, resuming from the saved cursor. */
    fun retry() {
        viewModelScope.launch { imports.retry(giveawayId) }
    }

    /** After three failed retries or a deleted post: continue with what was imported (printed on the certificate). */
    fun acceptPartial() {
        viewModelScope.launch { imports.acceptPartial(giveawayId) }
    }

    private fun isImportable(g: GiveawayEntity) =
        !g.closesAt.isAfter(clock.instant()) &&
            (g.status == GiveawayStatus.COMMITTED || g.status == GiveawayStatus.IMPORTING)

    private fun uiState(g: GiveawayEntity, progress: ImportProgress): ImportUiState {
        val reviewing = g.status != GiveawayStatus.COMMITTED && g.status != GiveawayStatus.IMPORTING
        val fetching = when {
            reviewing || progress.done -> StepState.DONE
            progress.phase == ImportPhase.NOT_STARTED -> StepState.PENDING
            progress.phase in STOPPED_PHASES -> StepState.STOPPED
            else -> StepState.RUNNING
        }
        val filtering = when {
            reviewing -> StepState.DONE
            progress.done -> StepState.RUNNING
            else -> StepState.PENDING
        }
        return ImportUiState(
            title = g.title,
            progress = progress,
            opensAt = g.closesAt.takeIf { it.isAfter(clock.instant()) },
            fetching = fetching,
            filtering = filtering,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        val STOPPED_PHASES = setOf(ImportPhase.MISMATCH, ImportPhase.POST_DELETED, ImportPhase.SIGNED_OUT)
    }
}
