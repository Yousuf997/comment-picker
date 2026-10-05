package app.giveaway.feature.create

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.paging.PagingData
import androidx.paging.cachedIn
import app.giveaway.core.data.db.EntryCounts
import app.giveaway.core.data.db.EntryRow
import app.giveaway.core.data.giveaway.GiveawayRepository
import app.giveaway.core.data.giveaway.GiveawayStateMachine
import app.giveaway.core.data.review.EntryListFilter
import app.giveaway.core.data.review.EntryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** S10 Review entries (plan C-20). */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ReviewEntriesViewModel(
    savedStateHandle: SavedStateHandle,
    private val giveaways: GiveawayRepository,
    private val entries: EntryRepository,
    /** Typing pauses this long before searching; tests use 0, as their clock doesn't advance on its own. */
    private val searchDebounceMs: Long,
) : ViewModel() {

    @Inject
    constructor(savedStateHandle: SavedStateHandle, giveaways: GiveawayRepository, entries: EntryRepository) :
        this(savedStateHandle, giveaways, entries, SEARCH_DEBOUNCE_MS)

    private val giveawayId = savedStateHandle.toRoute<ReviewEntriesRoute>().giveawayId

    private val filterState = MutableStateFlow(EntryListFilter.ALL)
    val filter: StateFlow<EntryListFilter> = filterState.asStateFlow()

    private val queryState = MutableStateFlow("")
    val query: StateFlow<String> = queryState.asStateFlow()

    /** The row open in the detail sheet. */
    private val selectedState = MutableStateFlow<EntryRow?>(null)
    val selected: StateFlow<EntryRow?> = selectedState.asStateFlow()

    val title: StateFlow<String?> = flow { emit(giveaways.get(giveawayId)?.title) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val counts: StateFlow<EntryCounts?> = entries.counts(giveawayId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val rows: Flow<PagingData<EntryRow>> =
        combine(filterState, queryState.debounce(searchDebounceMs)) { f, q -> f to q }
            .flatMapLatest { (f, q) -> entries.entries(giveawayId, f, q) }
            .cachedIn(viewModelScope)

    fun onFilter(filter: EntryListFilter) {
        filterState.value = filter
    }

    fun onQuery(query: String) {
        queryState.value = query
    }

    fun onOpen(row: EntryRow?) {
        selectedState.value = row
    }

    fun exclude(row: EntryRow, reason: String) = change { entries.exclude(giveawayId, row.commentId, reason) }

    fun include(row: EntryRow, note: String) = change { entries.include(giveawayId, row.commentId, note) }

    fun addToBlocklist(row: EntryRow) = change { entries.addToBlocklist(giveawayId, row.username) }

    /** The exported file's bytes: exactly the canonical list the draw hashes (spec: S10 export). */
    suspend fun exportBytes(): ByteArray = entries.canonicalList(giveawayId).text.toByteArray(Charsets.UTF_8)

    private val pendingState = MutableStateFlow<(suspend () -> Unit)?>(null)

    /** An entry change after the draw waits for the user to confirm clearing the winners (plan A31). */
    val confirmClear: StateFlow<Boolean> = pendingState.map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun confirmClearWinners() {
        val block = pendingState.value ?: return
        pendingState.value = null
        viewModelScope.launch { block() }
    }

    fun keepWinners() {
        pendingState.value = null
    }

    private fun change(block: suspend () -> Unit) {
        selectedState.value = null
        viewModelScope.launch {
            val status = giveaways.get(giveawayId)?.status
            if (status != null && GiveawayStateMachine.hasResult(status)) pendingState.value = block else block()
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val SEARCH_DEBOUNCE_MS = 200L
    }
}
