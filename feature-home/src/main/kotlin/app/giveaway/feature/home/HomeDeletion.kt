package app.giveaway.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.giveaway.core.data.cleanup.GiveawayRemoval
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** "Delete giveaway" from a Home card's menu (plan A33); the list updates from the database. */
@HiltViewModel
class HomeDeletionViewModel @Inject constructor(private val removal: GiveawayRemoval) : ViewModel() {
    fun delete(giveawayId: Long) {
        viewModelScope.launch { removal.delete(giveawayId) }
    }
}
