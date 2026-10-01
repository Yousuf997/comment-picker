package app.giveaway.feature.draw

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giveaway.core.data.draw.SavedDraw
import app.giveaway.core.designsystem.GiveawayDimens
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.designsystem.component.DrawStage
import app.giveaway.core.designsystem.component.PrimaryButton
import app.giveaway.core.designsystem.formatCount
import app.giveaway.core.designsystem.handle
import app.giveaway.core.media.DrawSceneRenderer
import app.giveaway.core.media.DrawTimeline
import app.giveaway.core.media.SceneRenderer
import app.giveaway.draw.Pick
import app.giveaway.draw.Role
import java.util.Locale
import kotlin.math.floor

private val ReelRowHeight = 56.dp
private const val VISIBLE_ROWS = 5
private const val FINISH_HOLD_MS = 1_500L

/** A long gap between frames means the app was in the background: the animation doesn't jump ahead. */
private const val MAX_FRAME_STEP_MS = 100L
private const val CENTRE_TOLERANCE = 0.05f
private const val SECONDS_PER_MINUTE = 60
private const val MS_PER_SECOND = 1_000

@Composable
internal fun DrawingScreen(onFinished: () -> Unit, viewModel: DrawingViewModel = hiltViewModel()) {
    val loaded by viewModel.draw.collectAsStateWithLifecycle()
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // "Remove animations" in system settings shortens the spin to a quick reveal (spec: Motion).
    val reducedMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.pauseRecording() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.resumeRecording() }
    DrawingScreen(
        loaded = loaded,
        reducedMotion = reducedMotion,
        recording = recording,
        onStart = viewModel::startRecording,
        onStopRecording = viewModel::stopRecording,
        onFinished = onFinished,
    )
}

/** S12 Drawing (spec): a reel per pick landing in the accent bar, picked-so-far list, announced as each lands. */
@Composable
internal fun DrawingScreen(
    loaded: DrawingViewModel.Loaded?,
    reducedMotion: Boolean,
    onFinished: () -> Unit,
    recording: RecordingState = RecordingState.OFF,
    onStart: (SceneRenderer, DrawTimeline) -> Unit = { _, _ -> },
    onStopRecording: () -> Unit = {},
) {
    // The draw always completes: back does nothing here, and the screen stays awake (spec: S12 rules).
    BackHandler {}
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    DrawStage(modifier = Modifier.fillMaxSize().testTag("screen:S12")) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(GiveawayDimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val draw = loaded?.draw
            when {
                loaded == null -> Unit
                draw == null ->
                    PrimaryButton(stringResource(R.string.drawing_finish), onFinished, Modifier.fillMaxWidth())
                else -> Playback(draw, reducedMotion, recording, onStart, onStopRecording, onFinished)
            }
        }
    }
}

@Composable
private fun Playback(
    draw: SavedDraw,
    reducedMotion: Boolean,
    recording: RecordingState,
    onStart: (SceneRenderer, DrawTimeline) -> Unit,
    onStopRecording: () -> Unit,
    onFinished: () -> Unit,
) {
    val timeline = remember(draw) { DrawTimeline(draw.picks, draw.entrants, reducedMotion) }
    val winners = draw.picks.count { it.role == Role.WINNER }
    val context = LocalContext.current
    val style = rememberSceneStyle(context)
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(timeline) {
        val text = AndroidSceneText(context, winners, draw.picks.size - winners)
        onStart(DrawSceneRenderer(style, text, draw.title, draw.entryCount, draw.commitHash), timeline)
        var last = withFrameMillis { it }
        while (elapsed < timeline.durationMs + FINISH_HOLD_MS) {
            val now = withFrameMillis { it }
            elapsed += (now - last).coerceAtMost(MAX_FRAME_STEP_MS)
            last = now
        }
        onFinished()
    }
    val frame = timeline.frameAt(elapsed)
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(frame.landed.size) {
        if (frame.landed.isNotEmpty()) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    RecordingBar(recording, elapsed, onStopRecording)
    Text(
        draw.title,
        style = GiveawayTheme.typography.title.copy(textDirection = TextDirection.Content),
        color = GiveawayTheme.colors.onDrawMuted,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        frame.current?.let { progressLabel(it.pick, draw) } ?: stringResource(R.string.drawing_done),
        style = GiveawayTheme.typography.display,
        color = GiveawayTheme.colors.onDrawBackground,
        textAlign = TextAlign.Center,
    )
    Reel(frame.current?.reel.orEmpty(), frame.reelPosition)
    PickedSoFar(frame.landed, winners)
    // TalkBack hears each pick as it lands (spec: Accessibility); the list above already shows it.
    val latest = frame.landed.lastOrNull()?.let { announcement(it, winners) }.orEmpty()
    Box(
        Modifier
            .size(1.dp)
            .semantics {
                contentDescription = latest
                liveRegion = LiveRegionMode.Assertive
            }
            .testTag("drawing:announcement"),
    )
}

/** The red REC badge with a running timer, and Stop recording (spec: Draw recording, requirements 2 and 5). */
@Composable
private fun RecordingBar(recording: RecordingState, elapsedMs: Long, onStop: () -> Unit) {
    val colors = GiveawayTheme.colors
    when (recording) {
        RecordingState.RECORDING -> Row(
            modifier = Modifier.fillMaxWidth().testTag("drawing:rec"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(10.dp).background(colors.recording, CircleShape))
            Text(
                stringResource(R.string.drawing_rec),
                style = GiveawayTheme.typography.bodyStrong,
                color = colors.onDrawBackground,
            )
            Text(
                timer(elapsedMs),
                style = GiveawayTheme.typography.code,
                color = colors.onDrawMuted,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onStop) {
                Text(stringResource(R.string.drawing_stop_recording), color = colors.onDrawBackground)
            }
        }
        RecordingState.FAILED -> Text(
            stringResource(R.string.drawing_recording_failed),
            style = GiveawayTheme.typography.caption,
            color = colors.onDrawMuted,
        )
        RecordingState.OFF, RecordingState.STOPPED, RecordingState.SAVED -> Unit
    }
    if (recording == RecordingState.RECORDING) {
        Text(
            stringResource(R.string.drawing_only_screen),
            style = GiveawayTheme.typography.caption,
            color = colors.onDrawMuted,
        )
    }
}

private fun timer(ms: Long): String {
    val seconds = ms / MS_PER_SECOND
    return "%d:%02d".format(Locale.ROOT, seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
}

/** The slot-style reel: names scroll up through a fixed accent bar and settle on the pick. */
@Composable
private fun Reel(names: List<String>, position: Float) {
    val colors = GiveawayTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ReelRowHeight * VISIBLE_ROWS)
            .clipToBounds()
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxWidth().height(ReelRowHeight).background(colors.accent, GiveawayTheme.shapes.button))
        val base = floor(position).toInt()
        val fraction = position - base
        for (offset in -(VISIBLE_ROWS / 2 + 1)..(VISIBLE_ROWS / 2 + 1)) {
            val name = names.getOrNull(base + offset) ?: continue
            val centred = offset == 0 && fraction < CENTRE_TOLERANCE
            Text(
                handle(name),
                style = GiveawayTheme.typography.titleLarge,
                color = if (centred) colors.onAccent else colors.onDrawMuted,
                modifier = Modifier.graphicsLayer { translationY = (offset - fraction) * ReelRowHeight.toPx() },
            )
        }
    }
}

@Composable
private fun PickedSoFar(landed: List<Pick>, winners: Int) {
    if (landed.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(R.string.drawing_picked_so_far),
            style = GiveawayTheme.typography.overline,
            color = GiveawayTheme.colors.onDrawMuted,
        )
        landed.forEach { pick ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val colors = GiveawayTheme.colors
                val dot = if (pick.role == Role.WINNER) colors.accent else colors.onDrawMuted
                Box(Modifier.size(8.dp).background(dot, CircleShape))
                Text(
                    announcement(pick, winners),
                    style = GiveawayTheme.typography.body,
                    color = GiveawayTheme.colors.onDrawBackground,
                )
            }
        }
    }
}

@Composable
private fun progressLabel(pick: Pick, draw: SavedDraw): String {
    val winners = draw.picks.count { it.role == Role.WINNER }
    return if (pick.role == Role.WINNER) {
        stringResource(R.string.drawing_picking_winner, formatCount(pick.position), formatCount(winners))
    } else {
        val index = formatCount(pick.position - winners)
        stringResource(R.string.drawing_picking_alternate, index, formatCount(draw.picks.size - winners))
    }
}

@Composable
private fun announcement(pick: Pick, winners: Int): String = if (pick.role == Role.WINNER) {
    stringResource(R.string.drawing_announce_winner, formatCount(pick.position), handle(pick.username))
} else {
    stringResource(R.string.drawing_announce_alternate, formatCount(pick.position - winners), handle(pick.username))
}
