package app.giveaway.feature.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import app.giveaway.core.instagram.api.IgMedia
import app.giveaway.core.instagram.api.InstagramRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

/** S6 Pick post (wizard step 1). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PickPostViewModel @Inject constructor(
    private val instagram: InstagramRepository,
) : ViewModel() {

    private val filterState = MutableStateFlow(MediaFilter.ALL)
    val filter: StateFlow<MediaFilter> = filterState.asStateFlow()

    private val selectedState = MutableStateFlow<IgMedia?>(null)

    /** The chosen post or Reel. It stays chosen when the filter hides it, and the summary line still names it. */
    val selected: StateFlow<IgMedia?> = selectedState.asStateFlow()

    val media: Flow<PagingData<IgMedia>> = filterState
        .flatMapLatest { filter ->
            Pager(PagingConfig(pageSize = InstagramRepository.MEDIA_PAGE_SIZE, initialLoadSize = INITIAL_LOAD_SIZE)) {
                MediaPagingSource(instagram, filter)
            }.flow
        }
        .cachedIn(viewModelScope)

    fun onFilter(filter: MediaFilter) {
        filterState.value = filter
    }

    fun onSelect(media: IgMedia) {
        selectedState.value = media
    }

    private companion object {
        /** Two pages up front: enough to fill a phone screen of three-column tiles. */
        const val INITIAL_LOAD_SIZE = InstagramRepository.MEDIA_PAGE_SIZE * 2
    }
}
