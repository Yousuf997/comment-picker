package app.giveaway.feature.draw

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.giveaway.core.data.db.MediaFileEntity
import app.giveaway.core.data.draw.WinnerRepository
import app.giveaway.core.data.draw.WinnersView
import app.giveaway.core.data.media.MediaRepository
import app.giveaway.core.media.VideoGallery
import app.giveaway.core.media.VideoInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** S14 Winners (plan M-01) with the S13 save-video sheet over it (plans M-04, M-05). */
@HiltViewModel
class WinnersViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val winners: WinnerRepository,
    private val media: MediaRepository,
    private val gallery: VideoGallery,
) : ViewModel() {

    private val giveawayId = savedStateHandle.toRoute<WinnersRoute>().giveawayId

    val view: StateFlow<WinnersView?> = winners.observe(giveawayId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** The place whose Replace dialog is open. */
    private val replacingState = MutableStateFlow<Int?>(null)
    val replacing: StateFlow<Int?> = replacingState.asStateFlow()

    /** A recording still waiting for the S13 choice; asked again whenever this screen opens (spec: requirement 8). */
    data class PendingVideo(val file: MediaFileEntity, val info: VideoInfo?)

    val pendingVideo: StateFlow<PendingVideo?> = media.observePendingVideo(giveawayId)
        .map { video -> video?.let { PendingVideo(it, gallery.describe(File(it.uri))) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val savingState = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = savingState.asStateFlow()

    sealed interface SaveResult {
        data class Saved(val uri: Uri) : SaveResult

        data object Failed : SaveResult
    }

    private val savedChannel = Channel<SaveResult>(Channel.BUFFERED)
    val saved: Flow<SaveResult> = savedChannel.receiveAsFlow()

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

    /** "Save to gallery": copies the video to Movies/[album] (spec: requirement 7). */
    fun saveVideo(album: String, displayName: String) {
        val video = pendingVideo.value ?: return
        if (savingState.value) return
        savingState.value = true
        viewModelScope.launch {
            val result = try {
                val uri = gallery.save(File(video.file.uri), album, displayName)
                media.savedToGallery(video.file, uri.toString())
                SaveResult.Saved(uri)
            } catch (expected: IOException) {
                SaveResult.Failed
            } catch (expected: SecurityException) {
                // Android 8 or 9 without the storage permission.
                SaveResult.Failed
            }
            savingState.value = false
            savedChannel.send(result)
        }
    }

    /** "Don't save": deletes the private file at once (spec: requirement 8). */
    fun discardVideo() {
        val video = pendingVideo.value ?: return
        viewModelScope.launch { media.discard(video.file) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
