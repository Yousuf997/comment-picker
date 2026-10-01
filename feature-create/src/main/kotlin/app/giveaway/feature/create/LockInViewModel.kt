package app.giveaway.feature.create

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.giveaway.DrawCommitments
import app.giveaway.core.data.giveaway.GiveawayRepository
import app.giveaway.core.data.work.DeadlineScheduler
import app.giveaway.draw.Commit
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

data class LockInUiState(
    val title: String? = null,
    /** "#draw <64 hex>", the public commitment. The seed itself never reaches the UI. */
    val drawCode: String? = null,
    val closesAt: Instant? = null,
    /** "I've added the code to my caption". Already true for a giveaway committed earlier. */
    val confirmed: Boolean = false,
    val saving: Boolean = false,
    /** Entries closed while the giveaway was still a draft: the code can't be committed any more. */
    val deadlinePassed: Boolean = false,
)

/** S8 Lock in the draw (plan C-14): shows the draw code, then commits and schedules the deadline check. */
@HiltViewModel
class LockInViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val giveaways: GiveawayRepository,
    private val commitments: DrawCommitments,
    private val deadlines: DeadlineScheduler,
    private val clock: Clock,
) : ViewModel() {

    private val giveawayId = savedStateHandle.toRoute<LockInDrawRoute>().giveawayId

    private val uiState = MutableStateFlow(LockInUiState())
    val state: StateFlow<LockInUiState> = uiState.asStateFlow()

    private val doneChannel = Channel<Unit>(Channel.BUFFERED)
    val done: Flow<Unit> = doneChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val giveaway = giveaways.get(giveawayId) ?: return@launch
            val hash = commitments.commitHashFor(giveawayId)
            uiState.update {
                it.copy(
                    title = giveaway.title,
                    drawCode = Commit.drawCode(hash),
                    closesAt = giveaway.closesAt,
                    confirmed = giveaway.status != GiveawayStatus.DRAFT,
                )
            }
        }
    }

    fun onConfirmedChange(confirmed: Boolean) = uiState.update { it.copy(confirmed = confirmed) }

    fun onDone() {
        val current = uiState.value
        val closesAt = current.closesAt
        if (!current.confirmed || current.saving || closesAt == null) return
        uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            if (giveaways.get(giveawayId)?.status == GiveawayStatus.DRAFT) {
                // A commitment only means something if it was public before entries closed (spec: Commit).
                if (!closesAt.isAfter(clock.instant())) {
                    uiState.update { it.copy(saving = false, deadlinePassed = true) }
                    return@launch
                }
                giveaways.commit(giveawayId)
            }
            deadlines.schedule(giveawayId, closesAt)
            doneChannel.send(Unit)
        }
    }
}
