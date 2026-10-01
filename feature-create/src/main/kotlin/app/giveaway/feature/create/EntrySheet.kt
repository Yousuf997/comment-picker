package app.giveaway.feature.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import app.giveaway.core.data.db.EntryRow
import app.giveaway.core.data.review.EntryRepository
import app.giveaway.core.designsystem.GiveawayDimens
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.designsystem.component.ChipTone
import app.giveaway.core.designsystem.component.GiveawayBottomSheet
import app.giveaway.core.designsystem.component.PrimaryButton
import app.giveaway.core.designsystem.component.SecondaryButton
import app.giveaway.core.designsystem.component.StatusChip
import app.giveaway.core.designsystem.formatDateTime
import app.giveaway.core.designsystem.handle
import app.giveaway.draw.ExclusionReason

private enum class SheetMode { VIEW, EXCLUDE, INCLUDE }

/** S10 entry detail: the full comment, why it counts or not, and the organizer's actions (spec: S10). */
@Composable
internal fun EntrySheet(
    row: EntryRow,
    onDismiss: () -> Unit,
    onExclude: (String) -> Unit,
    onInclude: (String) -> Unit,
    onBlocklist: () -> Unit,
) {
    var mode by remember(row.commentId) { mutableStateOf(SheetMode.VIEW) }
    var text by remember(row.commentId) { mutableStateOf("") }
    GiveawayBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("review:sheet")) {
        Column(
            modifier = Modifier
                .padding(horizontal = GiveawayDimens.screenPadding)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                handle(row.username),
                style = GiveawayTheme.typography.title,
                color = GiveawayTheme.colors.onBackground,
            )
            Text(
                formatDateTime(row.timestamp),
                style = GiveawayTheme.typography.caption,
                color = GiveawayTheme.colors.onMuted,
            )
            Text(
                row.text,
                style = GiveawayTheme.typography.body.copy(textDirection = TextDirection.Content),
                color = GiveawayTheme.colors.onBackground,
            )
            StatusChip(statusLabel(row), if (row.isValid) ChipTone.Success else ChipTone.Neutral)
            row.manualNote?.takeIf { it.isNotBlank() }?.let {
                Text(stringResource(R.string.review_manual_note, it), style = GiveawayTheme.typography.caption)
            }
            when (mode) {
                SheetMode.VIEW -> Actions(row, onExclude = { mode = SheetMode.EXCLUDE }, onInclude = {
                    if (row.exclusionReason == ExclusionReason.MANUAL) onInclude("") else mode = SheetMode.INCLUDE
                })
                SheetMode.EXCLUDE -> NoteForm(
                    value = text,
                    onValueChange = { text = it },
                    label = R.string.review_exclude_reason,
                    confirm = R.string.review_exclude_confirm,
                    required = true,
                    onConfirm = { onExclude(text) },
                )
                SheetMode.INCLUDE -> NoteForm(
                    value = text,
                    onValueChange = { text = it },
                    label = R.string.review_include_note,
                    confirm = R.string.review_include_confirm,
                    required = false,
                    onConfirm = { onInclude(text) },
                )
            }
            TextButton(onClick = onBlocklist, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.review_add_blocklist), color = GiveawayTheme.colors.danger)
            }
        }
    }
}

@Composable
private fun Actions(row: EntryRow, onExclude: () -> Unit, onInclude: () -> Unit) {
    when {
        row.isValid -> SecondaryButton(stringResource(R.string.review_exclude), onExclude, Modifier.fillMaxWidth())
        row.exclusionReason == ExclusionReason.MANUAL ->
            SecondaryButton(stringResource(R.string.review_undo_exclusion), onInclude, Modifier.fillMaxWidth())
        row.exclusionReason in EntryRepository.INCLUDABLE ->
            SecondaryButton(stringResource(R.string.review_include), onInclude, Modifier.fillMaxWidth())
        else -> Text(
            stringResource(R.string.review_cannot_include),
            style = GiveawayTheme.typography.caption,
            color = GiveawayTheme.colors.onMuted,
        )
    }
}

@Composable
private fun NoteForm(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    confirm: Int,
    required: Boolean,
    onConfirm: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        modifier = Modifier.fillMaxWidth().testTag("review:note"),
    )
    PrimaryButton(
        text = stringResource(confirm),
        onClick = onConfirm,
        enabled = !required || value.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    )
}

internal fun reasonLabel(reason: ExclusionReason): Int = when (reason) {
    ExclusionReason.AFTER_DEADLINE -> R.string.review_reason_after_deadline
    ExclusionReason.OWN_ACCOUNT -> R.string.review_reason_own_account
    ExclusionReason.BLOCKLISTED -> R.string.review_reason_blocklisted
    ExclusionReason.PAST_WINNER -> R.string.review_reason_past_winner
    ExclusionReason.TOO_FEW_MENTIONS -> R.string.review_reason_too_few_mentions
    ExclusionReason.MISSING_HASHTAG -> R.string.review_reason_missing_hashtag
    ExclusionReason.MISSING_KEYWORD -> R.string.review_reason_missing_keyword
    ExclusionReason.DUPLICATE -> R.string.review_reason_duplicate
    ExclusionReason.MANUAL -> R.string.review_reason_manual
}
