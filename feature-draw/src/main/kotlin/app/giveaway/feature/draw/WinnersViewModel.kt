package app.giveaway.feature.draw

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.giveaway.core.data.draw.WinnerRepository
import app.giveaway.core.data.draw.WinnersView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** S14 Winners (plan M-01). */
@HiltViewModel
class WinnersViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val winners: WinnerRepository,
) : ViewModel() {

    private val giveawayId = savedStateHandle.toRoute<WinnersRoute>().giveawayId

    val view: StateFlow<WinnersView?> = winners.observe(giveawayId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** The place whose Replace dialog is open. */
    private val replacingState = MutableStateFlow<Int?>(null)
    val replacing: StateFlow<Int?> = replacingState.asStateFlow()

    fun confirm(position: Int) {
        viewModelScope.launch { winners.confirm(giveawayId, position) }
    }

    fun startReplace(position: Int?) {
        replacingState.value = position
    }

    fun replace(position: Int, reason: String) {
        replacingState.value = null
        viewModelScope.launch { winners.replace(giveawayId, position, reason) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
