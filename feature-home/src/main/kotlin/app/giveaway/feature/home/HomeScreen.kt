package app.giveaway.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giveaway.core.designsystem.GiveawayDimens
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.designsystem.handle
import app.giveaway.core.designsystem.component.ChipTone
import app.giveaway.core.designsystem.component.DrawStage
import app.giveaway.core.designsystem.component.GiveawayCard as CardSurface
import app.giveaway.core.designsystem.component.NoticeCard
import app.giveaway.core.designsystem.component.NoticeTone
import app.giveaway.core.designsystem.component.PrimaryButton
import app.giveaway.core.designsystem.component.StatusChip
import coil3.compose.AsyncImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import app.giveaway.core.designsystem.R as DesignR

/** Callbacks from S4 to the rest of the app. */
data class HomeActions(
    val onNewGiveaway: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenGiveaway: (id: Long, destination: GiveawayDestination) -> Unit,
    val onSignInAgain: () -> Unit,
    val onExportBackup: () -> Unit,
)

@Composable
internal fun HomeScreen(actions: HomeActions, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(state, actions)
}

/** S4 Home (spec): the giveaway list and the way to start a new one. */
@Composable
internal fun HomeScreen(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GiveawayTheme.colors.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(GiveawayDimens.screenPadding)
            .testTag("screen:S4"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(state.username, actions.onOpenSettings)
        if (state.signInExpired) {
            NoticeCard(
                title = stringResource(R.string.home_expired_title),
                body = stringResource(R.string.home_expired_body),
                tone = NoticeTone.Warning,
                actionLabel = stringResource(R.string.home_expired_action),
                onAction = actions.onSignInAgain,
            )
        }
        Hero(actions.onNewGiveaway)
        if (state.showBackupReminder) {
            NoticeCard(
                title = stringResource(R.string.home_backup_title),
                body = stringResource(R.string.home_backup_body),
                tone = NoticeTone.Info,
                actionLabel = stringResource(R.string.home_backup_action),
                onAction = actions.onExportBackup,
            )
        }
        if (state.isEmpty) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.home_empty_title), style = GiveawayTheme.typography.title)
                Text(
                    stringResource(R.string.home_empty_body),
                    style = GiveawayTheme.typography.body,
                    color = GiveawayTheme.colors.onMuted,
                )
            }
        }
        Section(stringResource(R.string.home_in_progress), state.inProgress, actions)
        Section(stringResource(R.string.home_completed), state.completed, actions)
    }
}

@Composable
private fun Header(username: String?, onOpenSettings: () -> Unit) {
    val colors = GiveawayTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // Initials, not a profile photo: the app stores no profile pictures (spec: Data model).
        Box(
            modifier = Modifier.size(40.dp).background(colors.accentSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                username?.take(1)?.uppercase().orEmpty(),
                style = GiveawayTheme.typography.title,
                color = colors.accentOnSoft,
            )
        }
        Text(
            username?.let(::handle).orEmpty(),
            style = GiveawayTheme.typography.title,
            color = colors.onBackground,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onOpenSettings) {
            Icon(
                painterResource(DesignR.drawable.ic_settings),
                contentDescription = stringResource(R.string.home_settings),
                tint = colors.onBackground,
            )
        }
    }
}

@Composable
private fun Hero(onNewGiveaway: () -> Unit) {
    DrawStage(modifier = Modifier.fillMaxWidth().clip(GiveawayTheme.shapes.card)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.home_hero_title),
                style = GiveawayTheme.typography.display,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.home_hero_body),
                style = GiveawayTheme.typography.body,
                color = GiveawayTheme.colors.onDrawMuted,
            )
            PrimaryButton(
                stringResource(R.string.home_new_giveaway),
                onClick = onNewGiveaway,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Section(title: String, cards: List<GiveawayCard>, actions: HomeActions) {
    if (cards.isEmpty()) return
    Text(
        title.uppercase(),
        style = GiveawayTheme.typography.overline,
        color = GiveawayTheme.colors.onMuted,
        modifier = Modifier.semantics { heading() },
    )
    cards.forEach { card -> GiveawayRow(card) { actions.onOpenGiveaway(card.id, card.destination) } }
}

@Composable
private fun GiveawayRow(card: GiveawayCard, onClick: () -> Unit) {
    val colors = GiveawayTheme.colors
    CardSurface(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(
                model = card.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(GiveawayTheme.shapes.button)
                    .background(colors.surfaceMuted),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(card.title, style = GiveawayTheme.typography.bodyStrong, color = colors.onBackground, maxLines = 2)
                Text(dateLine(card), style = GiveawayTheme.typography.caption, color = colors.onMuted)
                countLine(card)?.let { Text(it, style = GiveawayTheme.typography.caption, color = colors.onMuted) }
            }
            StatusChip(stringResource(card.status.label), card.status.tone)
        }
    }
}

@Composable
private fun dateLine(card: GiveawayCard): String {
    val completed = card.status == CardStatus.COMPLETED
    val date = formatDate(if (completed) card.createdAt else card.closesAt)
    return stringResource(if (completed) R.string.home_created else R.string.home_closes, date)
}

@Composable
private fun countLine(card: GiveawayCard): String? = when {
    card.validEntryCount > 0 -> pluralStringResource(R.plurals.home_entries, card.validEntryCount, card.validEntryCount)
    card.commentCount > 0 -> pluralStringResource(R.plurals.home_comments, card.commentCount, card.commentCount)
    else -> null
}

/** Dates follow the app's language (spec: Localization). */
@Composable
private fun formatDate(instant: Instant): String {
    val locale = LocalConfiguration.current.locales[0]
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(ZoneId.systemDefault())
        .format(instant)
}

private val CardStatus.label: Int
    get() = when (this) {
        CardStatus.DRAFT -> R.string.status_draft
        CardStatus.WAITING -> R.string.status_waiting
        CardStatus.READY_TO_IMPORT -> R.string.status_ready_to_import
        CardStatus.IMPORTING -> R.string.status_importing
        CardStatus.REVIEW -> R.string.status_review
        CardStatus.DRAWN -> R.string.status_drawn
        CardStatus.COMPLETED -> R.string.status_completed
    }

private val CardStatus.tone: ChipTone
    get() = when (this) {
        CardStatus.DRAFT -> ChipTone.Neutral
        CardStatus.WAITING, CardStatus.IMPORTING -> ChipTone.Info
        CardStatus.READY_TO_IMPORT, CardStatus.REVIEW -> ChipTone.Accent
        CardStatus.DRAWN, CardStatus.COMPLETED -> ChipTone.Success
    }
