package app.giveaway.feature.draw

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.giveaway.core.data.draw.DrawService
import app.giveaway.core.data.draw.SavedDraw
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** S12 Drawing (plan C-22): replays the saved real draw; the result was fixed before this screen opened. */
@HiltViewModel
class DrawingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    draws: DrawService,
) : ViewModel() {

    private val giveawayId = savedStateHandle.toRoute<DrawingRoute>().giveawayId

    private val drawState = MutableStateFlow<Loaded?>(null)

    /** Null while loading; [Loaded.draw] is null if there's no saved draw (the screen then just offers to move on). */
    val draw: StateFlow<Loaded?> = drawState.asStateFlow()

    data class Loaded(val draw: SavedDraw?)

    init {
        viewModelScope.launch { drawState.value = Loaded(draws.savedDraw(giveawayId)) }
    }
}
