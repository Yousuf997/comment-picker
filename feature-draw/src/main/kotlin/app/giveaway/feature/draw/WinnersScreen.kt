package app.giveaway.feature.draw

import android.content.Intent
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giveaway.core.data.db.ConfirmationStatus
import app.giveaway.core.data.draw.WinnerPlace
import app.giveaway.core.data.draw.WinnersView
import app.giveaway.core.designsystem.GiveawayDimens
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.designsystem.component.ChipTone
import app.giveaway.core.designsystem.component.GiveawayCard
import app.giveaway.core.designsystem.component.NoticeCard
import app.giveaway.core.designsystem.component.NoticeTone
import app.giveaway.core.designsystem.component.PrimaryButton
import app.giveaway.core.designsystem.component.SecondaryButton
import app.giveaway.core.designsystem.component.StatusChip
import app.giveaway.core.designsystem.formatCount
import app.giveaway.core.designsystem.formatDateTime
import app.giveaway.core.designsystem.handle

@Composable
internal fun WinnersScreen(onCreateCertificate: () -> Unit, viewModel: WinnersViewModel = hiltViewModel()) {
    val view by viewModel.view.collectAsStateWithLifecycle()
    val replacing by viewModel.replacing.collectAsStateWithLifecycle()
    val context = LocalContext.current
    WinnersScreen(
        view = view,
        replacing = replacing,
        actions = WinnersActions(
            onConfirm = viewModel::confirm,
            onReplace = { viewModel.startReplace(it) },
            onReplaceConfirmed = viewModel::replace,
            onReplaceCancelled = { viewModel.startReplace(null) },
            // The app never messages anyone: this only opens the profile in Instagram or the browser (spec: S14).
            onOpenProfile = { username ->
                context.startActivity(Intent(Intent.ACTION_VIEW, "https://www.instagram.com/$username/".toUri()))
            },
            onCreateCertificate = onCreateCertificate,
        ),
    )
}

internal data class WinnersActions(
    val onConfirm: (position: Int) -> Unit,
    val onReplace: (position: Int) -> Unit,
    val onReplaceConfirmed: (position: Int, reason: String) -> Unit,
    val onReplaceCancelled: () -> Unit,
    val onOpenProfile: (username: String) -> Unit,
    val onCreateCertificate: () -> Unit,
)

/** S14 Winners (spec): a card per winner with the manual follow check, the alternates, and the certificate. */
@Composable
internal fun WinnersScreen(view: WinnersView?, replacing: Int?, actions: WinnersActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GiveawayTheme.colors.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(GiveawayDimens.screenPadding)
            .testTag("screen:S14"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Confetti()
        Text(
            stringResource(R.string.winners_title),
            style = GiveawayTheme.typography.display,
            color = GiveawayTheme.colors.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        if (view != null) {
            Text(
                stringResource(R.string.winners_subtitle, formatDateTime(view.drawnAt), formatCount(view.entryCount)),
                style = GiveawayTheme.typography.body,
                color = GiveawayTheme.colors.onMuted,
            )
            Text(
                stringResource(R.string.winners_check_note),
                style = GiveawayTheme.typography.caption,
                color = GiveawayTheme.colors.onMuted,
            )
            view.winners.forEach { place -> WinnerCard(place, view.alternatesUsedUp, actions) }
            Alternates(view)
        }
        PrimaryButton(
            text = stringResource(R.string.winners_create_certificate),
            onClick = actions.onCreateCertificate,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (view != null && replacing != null) {
        ReplaceDialog(
            username = view.winners.firstOrNull { it.position == replacing }?.username.orEmpty(),
            onConfirm = { reason -> actions.onReplaceConfirmed(replacing, reason) },
            onDismiss = actions.onReplaceCancelled,
        )
    }
}

@Composable
private fun WinnerCard(place: WinnerPlace, alternatesUsedUp: Boolean, actions: WinnersActions) {
    val colors = GiveawayTheme.colors
    GiveawayCard(modifier = Modifier.fillMaxWidth().testTag("winners:card:${place.rank}")) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).background(colors.accentSoft, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        formatCount(place.rank),
                        style = GiveawayTheme.typography.bodyStrong,
                        color = colors.accentOnSoft,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        handle(place.username),
                        style = GiveawayTheme.typography.bodyStrong,
                        color = colors.onBackground,
                    )
                    place.comment?.let {
                        Text(
                            it,
                            style = GiveawayTheme.typography.caption.copy(textDirection = TextDirection.Content),
                            color = colors.onMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                TextButton(onClick = { actions.onOpenProfile(place.username) }) {
                    Text(stringResource(R.string.winners_open_profile))
                }
            }
            if (place.promoted) {
                Text(
                    stringResource(R.string.winners_promoted),
                    style = GiveawayTheme.typography.caption,
                    color = colors.onMuted,
                )
            }
            FollowCheck(place, alternatesUsedUp, actions)
        }
    }
}

/** Follows and likes are checked by hand (spec: Constraints); then Confirmed, or Replace and Confirm. */
@Composable
private fun FollowCheck(place: WinnerPlace, alternatesUsedUp: Boolean, actions: WinnersActions) {
    if (place.status == ConfirmationStatus.CONFIRMED) {
        StatusChip(stringResource(R.string.winners_confirmed), ChipTone.Success)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryButton(
            text = stringResource(R.string.winners_replace),
            onClick = { actions.onReplace(place.position) },
            enabled = !alternatesUsedUp,
            modifier = Modifier.weight(1f),
        )
        PrimaryButton(
            text = stringResource(R.string.winners_confirm),
            onClick = { actions.onConfirm(place.position) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Alternates(view: WinnersView) {
    if (view.alternatesUsedUp) {
        NoticeCard(
            title = stringResource(R.string.winners_no_alternates_title),
            body = stringResource(R.string.winners_no_alternates_body),
            tone = NoticeTone.Info,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.winners_alternates),
            style = GiveawayTheme.typography.overline,
            color = GiveawayTheme.colors.onMuted,
        )
        Text(
            view.alternates.joinToString("   ") { handle(it) },
            style = GiveawayTheme.typography.body,
            color = GiveawayTheme.colors.onBackground,
        )
    }
}

@Composable
private fun ReplaceDialog(username: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.winners_replace_title, handle(username))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.winners_replace_body))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text(stringResource(R.string.winners_replace_reason)) },
                    modifier = Modifier.fillMaxWidth().testTag("winners:reason"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason) },
                enabled = reason.isNotBlank(),
                modifier = Modifier.testTag("winners:replace_confirm"),
            ) {
                Text(stringResource(R.string.winners_replace_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.winners_replace_cancel)) } },
    )
}

/** Light confetti shapes at the top (spec: S14); decoration only, hidden from TalkBack. */
@Composable
private fun Confetti() {
    val colors = GiveawayTheme.colors
    val palette = listOf(colors.accent, colors.accentSoft, colors.success, colors.info)
    Canvas(modifier = Modifier.fillMaxWidth().height(48.dp).clearAndSetSemantics {}) {
        CONFETTI.forEachIndexed { i, (x, y) ->
            val center = Offset(size.width * x, size.height * y)
            drawCircle(palette[i % palette.size], radius = 4.dp.toPx(), center = center)
        }
    }
}

/** Fixed positions (fractions of the strip), so the screen looks the same every time. */
@Suppress("MagicNumber") // Decorative positions.
private val CONFETTI = listOf(
    0.06f to 0.3f, 0.18f to 0.75f, 0.31f to 0.2f, 0.44f to 0.6f, 0.57f to 0.15f,
    0.68f to 0.8f, 0.79f to 0.35f, 0.9f to 0.65f, 0.97f to 0.2f,
)
