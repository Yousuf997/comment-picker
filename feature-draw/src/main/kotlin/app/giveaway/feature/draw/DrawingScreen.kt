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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giveaway.core.data.draw.SavedDraw
import app.giveaway.core.designsystem.GiveawayDimens
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.designsystem.component.DrawStage
import app.giveaway.core.designsystem.component.PrimaryButton
import app.giveaway.core.designsystem.formatCount
import app.giveaway.core.designsystem.handle
import app.giveaway.core.media.DrawTimeline
import app.giveaway.draw.Pick
import app.giveaway.draw.Role
import kotlin.math.floor

private val ReelRowHeight = 56.dp
private const val VISIBLE_ROWS = 5
private const val FINISH_HOLD_MS = 1_500L

@Composable
internal fun DrawingScreen(onFinished: () -> Unit, viewModel: DrawingViewModel = hiltViewModel()) {
    val loaded by viewModel.draw.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // "Remove animations" in system settings shortens the spin to a quick reveal (spec: Motion).
    val reducedMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    DrawingScreen(loaded = loaded, reducedMotion = reducedMotion, onFinished = onFinished)
}

/** S12 Drawing (spec): a reel per pick landing in the accent bar, picked-so-far list, announced as each lands. */
@Composable
internal fun DrawingScreen(loaded: DrawingViewModel.Loaded?, reducedMotion: Boolean, onFinished: () -> Unit) {
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
                else -> Playback(draw, reducedMotion, onFinished)
            }
        }
    }
}

@Composable
private fun Playback(draw: SavedDraw, reducedMotion: Boolean, onFinished: () -> Unit) {
    val timeline = remember(draw) { DrawTimeline(draw.picks, draw.entrants, reducedMotion) }
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(timeline) {
        val start = withFrameMillis { it }
        while (elapsed < timeline.durationMs + FINISH_HOLD_MS) {
            elapsed = withFrameMillis { it } - start
        }
        onFinished()
    }
    val frame = timeline.frameAt(elapsed)
    val winners = draw.picks.count { it.role == Role.WINNER }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(frame.landed.size) {
        if (frame.landed.isNotEmpty()) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
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
                Box(Modifier.size(8.dp).background(roleColor(pick), CircleShape))
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
private fun roleColor(pick: Pick) =
    if (pick.role == Role.WINNER) GiveawayTheme.colors.accent else GiveawayTheme.colors.onDrawMuted

@Composable
private fun progressLabel(pick: Pick, draw: SavedDraw): String {
    val winners = draw.picks.count { it.role == Role.WINNER }
    return if (pick.role == Role.WINNER) {
        stringResource(R.string.drawing_picking_winner, formatCount(pick.position), formatCount(winners))
    } else {
        val alternates = draw.picks.size - winners
        val index = formatCount(pick.position - winners)
        stringResource(R.string.drawing_picking_alternate, index, formatCount(alternates))
    }
}

@Composable
private fun announcement(pick: Pick, winners: Int): String = if (pick.role == Role.WINNER) {
    stringResource(R.string.drawing_announce_winner, formatCount(pick.position), handle(pick.username))
} else {
    stringResource(R.string.drawing_announce_alternate, formatCount(pick.position - winners), handle(pick.username))
}

private const val CENTRE_TOLERANCE = 0.05f
