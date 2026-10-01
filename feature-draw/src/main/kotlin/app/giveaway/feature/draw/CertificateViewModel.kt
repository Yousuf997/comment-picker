package app.giveaway.feature.draw

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.db.MediaFileEntity
import app.giveaway.core.data.db.MediaKind
import app.giveaway.core.data.draw.DrawService
import app.giveaway.core.data.draw.WinnerRepository
import app.giveaway.core.data.giveaway.GiveawayRepository
import app.giveaway.core.data.media.MediaRepository
import app.giveaway.core.data.review.EntryRepository
import app.giveaway.core.media.CertificateContent
import app.giveaway.core.media.CertificateData
import app.giveaway.core.media.CertificateOutput
import app.giveaway.core.media.CertificateStyle
import app.giveaway.core.media.VideoGallery
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject

/** What S15 can show: the certificate, or why there is none. */
internal sealed interface CertificateState {
    data object Loading : CertificateState

    /** No real draw yet; S15 is reached only after one. */
    data object NoDraw : CertificateState

    /** The saved result no longer matches its signature, so no certificate is made (plan M-06). */
    data object Broken : CertificateState

    data class Ready(val data: CertificateData) : CertificateState
}

/** The certificate files, private to the app until the organizer shares or saves them. */
internal data class CertificateFiles(val pdf: File, val image: File)

/**
 * S15 Certificate (plan M-08). The record is checked against its signature first; then the PDF and Story image are
 * written and the giveaway is archived ("certificate made", spec: giveaway status). From then on the result is final.
 */
@HiltViewModel
internal class CertificateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val draws: DrawService,
    private val winners: WinnerRepository,
    private val giveaways: GiveawayRepository,
    private val entries: EntryRepository,
    private val media: MediaRepository,
    private val gallery: VideoGallery,
    private val output: CertificateOutput,
) : ViewModel() {
    private val giveawayId = savedStateHandle.toRoute<CertificateRoute>().giveawayId

    private val stateFlow = MutableStateFlow<CertificateState>(CertificateState.Loading)
    val state: StateFlow<CertificateState> = stateFlow.asStateFlow()

    private val filesState = MutableStateFlow<CertificateFiles?>(null)
    val files: StateFlow<CertificateFiles?> = filesState.asStateFlow()

    private val failedState = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = failedState.asStateFlow()

    /** A recording still waiting to be saved: "Save reveal video (if not saved earlier)" (spec: S15). */
    val pendingVideo: StateFlow<MediaFileEntity?> = media.observePendingVideo(giveawayId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val messages = Channel<Message>(Channel.BUFFERED)
    val message: Flow<Message> = messages.receiveAsFlow()

    sealed interface Message {
        data class VideoSaved(val uri: Uri) : Message

        data object VideoFailed : Message
    }

    private var making = false

    init {
        viewModelScope.launch { stateFlow.value = load() }
    }

    private suspend fun load(): CertificateState {
        val signed = draws.signedRecord(giveawayId) ?: return CertificateState.NoDraw
        if (!signed.verifies()) return CertificateState.Broken
        val replacements = winners.observe(giveawayId).first()?.replacements.orEmpty()
            .map { CertificateData.Replacement(it.replaced, it.by, it.reason) }
        val data = CertificateData(
            record = signed.record,
            signatureHex = signed.signature.toHex(),
            publicKeyHex = signed.publicKeySpki.toHex(),
            fingerprint = signed.fingerprint,
            replacements = replacements,
        )
        return CertificateState.Ready(data)
    }

    /**
     * Writes the PDF and the Story image from words the screen wrote in the app's language, then archives the
     * giveaway. Making it again (another visit, another language) rewrites the same files.
     */
    fun make(pdf: CertificateContent, story: CertificateContent, style: CertificateStyle) {
        if (making || stateFlow.value !is CertificateState.Ready) return
        making = true
        viewModelScope.launch {
            val made = runCatching {
                val files = withContext(Dispatchers.IO) {
                    val pdfFile = media.certificateFile(giveawayId, MediaKind.CERTIFICATE_PDF)
                    val imageFile = media.certificateFile(giveawayId, MediaKind.CERTIFICATE_IMAGE)
                    pdfFile.outputStream().use { output.writePdf(pdf, style, it) }
                    imageFile.outputStream().use { output.writePng(story, style, it) }
                    CertificateFiles(pdfFile, imageFile)
                }
                media.certificateMade(giveawayId, MediaKind.CERTIFICATE_PDF, files.pdf)
                media.certificateMade(giveawayId, MediaKind.CERTIFICATE_IMAGE, files.image)
                if (giveaways.get(giveawayId)?.status == GiveawayStatus.DRAWN) {
                    giveaways.transition(giveawayId, GiveawayStatus.ARCHIVED)
                }
                files
            }
            filesState.value = made.getOrNull()
            failedState.value = made.isFailure
            making = false
        }
    }

    /** The exported entry list: exactly the canonical list whose hash the certificate prints (spec: S15). */
    suspend fun entryListBytes(): ByteArray = entries.canonicalList(giveawayId).text.toByteArray(Charsets.UTF_8)

    fun saveVideo(album: String, displayName: String) {
        val video = pendingVideo.value ?: return
        viewModelScope.launch {
            val result = runCatching {
                val uri = gallery.save(File(video.uri), album, displayName)
                media.savedToGallery(video, uri.toString())
                uri
            }
            val message: Message = result.fold(
                onSuccess = { Message.VideoSaved(it) },
                onFailure = { Message.VideoFailed },
            )
            messages.send(message)
        }
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(Locale.ROOT, it) }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
