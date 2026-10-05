package app.giveaway.feature.draw

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giveaway.core.designsystem.GiveawayDimens
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.designsystem.component.NoticeCard
import app.giveaway.core.designsystem.component.NoticeTone
import app.giveaway.core.designsystem.component.PrimaryButton
import app.giveaway.core.designsystem.component.SecondaryButton
import app.giveaway.core.media.CertificateContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CertificateScreen(
    onDone: () -> Unit,
    onRedraw: () -> Unit,
    viewModel: CertificateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val files by viewModel.files.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    val video by viewModel.pendingVideo.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    val writer = remember(context) { CertificateWriter(context, BuildConfig.VERIFIER_URL) }
    val data = (state as? CertificateState.Ready)?.data
    val content = remember(data, writer) { data?.let(writer::pdf) }
    // The files are written in the app's language from the same words S15 shows (spec: Localization).
    val make = {
        if (data != null && content != null) {
            viewModel.make(content, writer.story(data), CertificateWriter.style(context, arabic))
        }
    }
    LaunchedEffect(content) { make() }
    BackHandler(onBack = onDone)
    val snackbar = remember { SnackbarHostState() }
    CertificateMessages(viewModel, snackbar)
    var askingRedraw by remember { mutableStateOf(false) }
    val actions = rememberCertificateActions(viewModel, files, data?.record?.title, snackbar, onDone, make)
        .copy(onRedraw = { askingRedraw = true })
    Box {
        CertificateScreen(state, content, files != null, failed, video != null, actions)
        if (askingRedraw) {
            RedrawDialog(
                onConfirm = {
                    askingRedraw = false
                    onRedraw()
                },
                onDismiss = { askingRedraw = false },
            )
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

internal data class CertificateActions(
    val onShareStory: () -> Unit,
    val onSavePdf: () -> Unit,
    val onSaveVideo: () -> Unit,
    val onExportList: () -> Unit,
    val onRetry: () -> Unit,
    val onDone: () -> Unit,
    val onRedraw: () -> Unit = {},
)

/** Share to Story, Save PDF, Save reveal video and Export entry list (spec: S15 actions). */
@Composable
private fun rememberCertificateActions(
    viewModel: CertificateViewModel,
    files: CertificateFiles?,
    title: String?,
    snackbar: SnackbarHostState,
    onDone: () -> Unit,
    onRetry: () -> Unit,
): CertificateActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val name = title.orEmpty().ifBlank { "giveaway" }
    val pdfSaved = stringResource(R.string.certificate_pdf_saved)
    val listSaved = stringResource(R.string.certificate_list_saved)
    val saveFailed = stringResource(R.string.certificate_save_failed)
    fun save(uri: Uri?, message: String, bytes: suspend () -> ByteArray) {
        if (uri == null) return
        scope.launch {
            val ok = runCatching { write(context, uri, bytes()) }.isSuccess
            snackbar.showSnackbar(if (ok) message else saveFailed)
        }
    }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        files?.let { save(uri, pdfSaved) { it.pdf.readBytes() } }
    }
    // The export is the canonical list itself, byte for byte, so its hash matches the certificate (spec: S15).
    val exportList = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        save(uri, listSaved) { viewModel.entryListBytes() }
    }
    val pdfName = stringResource(R.string.certificate_pdf_file, name)
    val listName = stringResource(R.string.certificate_list_file, name)
    val saveVideo = rememberSaveVideo(title, viewModel::saveVideo)
    return CertificateActions(
        onShareStory = { files?.let { shareToStory(context, it.image) } },
        onSavePdf = { savePdf.launch(pdfName) },
        onSaveVideo = saveVideo,
        onExportList = { exportList.launch(listName) },
        onRetry = onRetry,
        onDone = onDone,
    )
}

private suspend fun write(context: Context, uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
    checkNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "No stream for $uri" }.use { it.write(bytes) }
}

/** The confirmation after saving the reveal video, with View (spec: requirement 7). */
@Composable
private fun CertificateMessages(viewModel: CertificateViewModel, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val saved = stringResource(R.string.save_video_saved)
    val view = stringResource(R.string.save_video_view)
    val failed = stringResource(R.string.save_video_failed)
    LaunchedEffect(viewModel) {
        viewModel.message.collect { message ->
            when (message) {
                is CertificateViewModel.Message.VideoSaved -> {
                    if (snackbar.showSnackbar(saved, actionLabel = view) == SnackbarResult.ActionPerformed) {
                        openVideo(context, message.uri)
                    }
                }
                CertificateViewModel.Message.VideoFailed -> snackbar.showSnackbar(failed)
            }
        }
    }
}

/** S15 Certificate (spec): the certificate card and what can be done with it. */
@Composable
internal fun CertificateScreen(
    state: CertificateState,
    content: CertificateContent?,
    ready: Boolean,
    failed: Boolean,
    hasVideo: Boolean,
    actions: CertificateActions,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GiveawayTheme.colors.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(GiveawayDimens.screenPadding)
            .testTag("screen:S15"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (state) {
            CertificateState.Loading -> Text(
                stringResource(R.string.certificate_loading),
                style = GiveawayTheme.typography.body,
                color = GiveawayTheme.colors.onMuted,
            )
            CertificateState.NoDraw -> NoticeCard(stringResource(R.string.certificate_no_draw), null, NoticeTone.Info)
            CertificateState.Broken -> NoticeCard(
                title = stringResource(R.string.certificate_broken_title),
                body = stringResource(R.string.certificate_broken_body),
                tone = NoticeTone.Warning,
            )
            is CertificateState.Ready -> content?.let { CertificateCard(it) }
        }
        if (failed) {
            NoticeCard(
                title = stringResource(R.string.certificate_failed),
                body = null,
                tone = NoticeTone.Warning,
                actionLabel = stringResource(R.string.certificate_retry),
                onAction = actions.onRetry,
            )
        }
        if (state is CertificateState.Ready) {
            CertificateButtons(ready, hasVideo, actions)
            // Replaces the result with a new draw (plan A30).
            SecondaryButton(
                text = stringResource(R.string.redraw_button),
                onClick = actions.onRedraw,
                modifier = Modifier.fillMaxWidth().testTag("certificate:redraw"),
            )
        }
        SecondaryButton(
            text = stringResource(R.string.certificate_done),
            onClick = actions.onDone,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CertificateButtons(ready: Boolean, hasVideo: Boolean, actions: CertificateActions) {
    PrimaryButton(
        text = stringResource(R.string.certificate_share_story),
        onClick = actions.onShareStory,
        enabled = ready,
        modifier = Modifier.fillMaxWidth(),
    )
    SecondaryButton(
        text = stringResource(R.string.certificate_save_pdf),
        onClick = actions.onSavePdf,
        enabled = ready,
        modifier = Modifier.fillMaxWidth(),
    )
    if (hasVideo) {
        SecondaryButton(
            text = stringResource(R.string.certificate_save_video),
            onClick = actions.onSaveVideo,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    SecondaryButton(
        text = stringResource(R.string.certificate_export_list),
        onClick = actions.onExportList,
        modifier = Modifier.fillMaxWidth(),
    )
}
