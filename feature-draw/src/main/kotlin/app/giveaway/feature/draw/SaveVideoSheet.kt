package app.giveaway.feature.draw

import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.giveaway.core.designsystem.GiveawayDimens
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.designsystem.component.GiveawayBottomSheet
import app.giveaway.core.designsystem.component.PrimaryButton
import app.giveaway.core.designsystem.component.SecondaryButton
import app.giveaway.core.designsystem.formatCount
import app.giveaway.core.media.VideoInfo
import java.util.Locale
import app.giveaway.core.designsystem.R as DesignR

private val PreviewHeight = 220.dp
private const val PORTRAIT_RATIO = 9f / 16f
private const val MS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L

/**
 * S13 Save the draw video (spec): over the winners, it can't be dismissed by tapping outside; the user must choose.
 * Saving copies the video into the gallery; not saving deletes it at once.
 */
@Composable
internal fun SaveVideoSheet(
    path: String,
    info: VideoInfo?,
    saving: Boolean,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    GiveawayBottomSheet(onDismissRequest = {}, dismissible = false, modifier = Modifier.testTag("screen:S13")) {
        Column(
            modifier = Modifier.padding(horizontal = GiveawayDimens.screenPadding).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Preview(path)
            info?.let {
                Text(
                    stringResource(
                        R.string.save_video_format,
                        duration(it.durationMs),
                        formatCount(it.width, grouping = false),
                        formatCount(it.height, grouping = false),
                    ),
                    style = GiveawayTheme.typography.caption,
                    color = GiveawayTheme.colors.onMuted,
                )
            }
            Text(
                stringResource(R.string.save_video_title),
                style = GiveawayTheme.typography.titleLarge,
                color = GiveawayTheme.colors.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.save_video_body),
                style = GiveawayTheme.typography.body,
                color = GiveawayTheme.colors.onMuted,
            )
            PrimaryButton(
                text = stringResource(R.string.save_video_save),
                onClick = onSave,
                enabled = !saving,
                onStage = false,
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                text = stringResource(R.string.save_video_discard),
                onClick = onDiscard,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.save_video_note),
                style = GiveawayTheme.typography.caption,
                color = GiveawayTheme.colors.onMuted,
            )
        }
    }
}

/** A small portrait preview with a play button (spec: S13). */
@Composable
private fun Preview(path: String) {
    var playing by remember { mutableStateOf(false) }
    val play = stringResource(R.string.save_video_play)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PreviewHeight)
            .testTag("save_video:preview"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(PORTRAIT_RATIO)
                .clip(GiveawayTheme.shapes.card)
                .background(GiveawayTheme.colors.drawBackground)
                .clickable(role = Role.Button, onClickLabel = play) { playing = true },
            contentAlignment = Alignment.Center,
        ) {
            if (playing) {
                AndroidView(
                    factory = { context -> VideoView(context).apply { setVideoPath(path); start() } },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier.size(48.dp).background(GiveawayTheme.colors.accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(DesignR.drawable.ic_play),
                        contentDescription = play,
                        tint = GiveawayTheme.colors.onAccent,
                    )
                }
            }
        }
    }
}

private fun duration(ms: Long): String {
    val seconds = ms / MS_PER_SECOND
    return "%d:%02d".format(Locale.ROOT, seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
}
