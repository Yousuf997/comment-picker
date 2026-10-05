package app.giveaway.feature.create

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
import androidx.paging.compose.collectAsLazyPagingItems
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.giveaway.GiveawayEditor
import app.giveaway.core.data.giveaway.GiveawayRepository
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.instagram.api.IgMedia
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChangePostState(
    val title: String? = null,
    val currentMediaId: String? = null,
    /** After the import, a new post deletes the old post's comments, entries and any result (plan A32). */
    val clearsData: Boolean = false,
    /** The post waiting for the user to confirm that data will be deleted. */
    val confirming: IgMedia? = null,
    val saving: Boolean = false,
)

/** S6 for an existing giveaway: picks a new post (plan A32). */
@HiltViewModel
class ChangePostViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val giveaways: GiveawayRepository,
    private val editor: GiveawayEditor,
) : ViewModel() {
    private val giveawayId = savedStateHandle.toRoute<ChangePostRoute>().giveawayId

    private val stateFlow = MutableStateFlow(ChangePostState())
    val state: StateFlow<ChangePostState> = stateFlow.asStateFlow()

    private val doneChannel = Channel<Unit>(Channel.BUFFERED)
    val done: Flow<Unit> = doneChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val giveaway = giveaways.get(giveawayId) ?: return@launch
            val beforeImport = giveaway.status == GiveawayStatus.DRAFT || giveaway.status == GiveawayStatus.COMMITTED
            stateFlow.update {
                it.copy(title = giveaway.title, currentMediaId = giveaway.igMediaId, clearsData = !beforeImport)
            }
        }
    }

    fun onContinue(media: IgMedia) {
        val current = stateFlow.value
        when {
            // Until the giveaway loads it isn't known whether the change deletes data.
            current.saving || current.currentMediaId == null -> Unit
            media.id == current.currentMediaId -> doneChannel.trySend(Unit)
            current.clearsData -> stateFlow.update { it.copy(confirming = media) }
            else -> save(media)
        }
    }

    fun confirm() = stateFlow.value.confirming?.let(::save)

    fun dismiss() = stateFlow.update { it.copy(confirming = null) }

    private fun save(media: IgMedia) {
        stateFlow.update { it.copy(confirming = null, saving = true) }
        viewModelScope.launch {
            editor.changePost(giveawayId, media)
            stateFlow.update { it.copy(saving = false, currentMediaId = media.id) }
            doneChannel.send(Unit)
        }
    }
}

@Composable
internal fun ChangePostScreen(
    onBack: () -> Unit,
    onChanged: () -> Unit,
    picker: PickPostViewModel = hiltViewModel(),
    viewModel: ChangePostViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filter by picker.filter.collectAsStateWithLifecycle()
    val selected by picker.selected.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.done.collect { onChanged() } }
    PickPostScreen(
        filter = filter,
        selected = selected,
        media = picker.media.collectAsLazyPagingItems(),
        onBack = onBack,
        onFilter = picker::onFilter,
        onSelect = picker::onSelect,
        onContinue = viewModel::onContinue,
        title = state.title,
    )
    if (state.confirming != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text(stringResource(R.string.change_post_confirm_title)) },
            text = { Text(stringResource(R.string.change_post_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirm() }, modifier = Modifier.testTag("change_post:confirm")) {
                    Text(stringResource(R.string.change_post_confirm), color = GiveawayTheme.colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismiss) { Text(stringResource(R.string.dialog_cancel)) }
            },
        )
    }
}
