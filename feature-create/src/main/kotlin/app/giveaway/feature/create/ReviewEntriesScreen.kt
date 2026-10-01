package app.giveaway.feature.create

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import app.giveaway.core.data.db.EntryCounts
import app.giveaway.core.data.db.EntryRow
import app.giveaway.core.data.review.EntryListFilter
import app.giveaway.core.designsystem.GiveawayDimens
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.designsystem.component.ChipTone
import app.giveaway.core.designsystem.component.NoticeCard
import app.giveaway.core.designsystem.component.NoticeTone
import app.giveaway.core.designsystem.component.PrimaryButton
import app.giveaway.core.designsystem.component.SegmentedControl
import app.giveaway.core.designsystem.component.StatusChip
import app.giveaway.core.designsystem.component.WizardHeader
import app.giveaway.core.designsystem.formatCount
import app.giveaway.core.designsystem.handle
import kotlinx.coroutines.launch
import app.giveaway.core.designsystem.R as DesignR

@Composable
internal fun ReviewEntriesScreen(
    onBack: () -> Unit,
    onContinueToDraw: () -> Unit,
    viewModel: ReviewEntriesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The export is the canonical list itself, byte for byte (spec: S10, Verification).
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = viewModel.exportBytes()
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            }
        }
    }
    val title by viewModel.title.collectAsStateWithLifecycle()
    val fileName = stringResource(R.string.review_export_file, title.orEmpty().ifBlank { "giveaway" })
    ReviewEntriesScreen(
        state = ReviewState(
            title = title,
            counts = viewModel.counts.collectAsStateWithLifecycle().value,
            filter = viewModel.filter.collectAsStateWithLifecycle().value,
            query = viewModel.query.collectAsStateWithLifecycle().value,
            selected = viewModel.selected.collectAsStateWithLifecycle().value,
        ),
        rows = viewModel.rows.collectAsLazyPagingItems(),
        actions = ReviewActions(
            onBack = onBack,
            onFilter = viewModel::onFilter,
            onQuery = viewModel::onQuery,
            onOpen = viewModel::onOpen,
            onExclude = viewModel::exclude,
            onInclude = viewModel::include,
            onBlocklist = viewModel::addToBlocklist,
            onExport = { export.launch(fileName) },
            onContinue = onContinueToDraw,
        ),
    )
}

internal data class ReviewState(
    val title: String?,
    val counts: EntryCounts?,
    val filter: EntryListFilter,
    val query: String,
    val selected: EntryRow?,
)

internal data class ReviewActions(
    val onBack: () -> Unit,
    val onFilter: (EntryListFilter) -> Unit,
    val onQuery: (String) -> Unit,
    val onOpen: (EntryRow?) -> Unit,
    val onExclude: (EntryRow, String) -> Unit,
    val onInclude: (EntryRow, String) -> Unit,
    val onBlocklist: (EntryRow) -> Unit,
    val onExport: () -> Unit,
    val onContinue: () -> Unit,
)

/** S10 Review entries (spec): tiles, filters and search, the entry list, export, and Continue to draw. */
@Composable
internal fun ReviewEntriesScreen(state: ReviewState, rows: LazyPagingItems<EntryRow>, actions: ReviewActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GiveawayTheme.colors.background)
            .safeDrawingPadding()
            .padding(GiveawayDimens.screenPadding)
            .testTag("screen:S10"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WizardHeader(
            title = state.title ?: stringResource(R.string.wizard_new_giveaway),
            step = 5,
            onBack = actions.onBack,
        )
        Text(
            stringResource(R.string.review_title),
            style = GiveawayTheme.typography.display,
            color = GiveawayTheme.colors.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Tiles(state.counts)
        SegmentedControl(
            options = listOf(
                stringResource(R.string.review_filter_all),
                stringResource(R.string.review_filter_valid),
                stringResource(R.string.review_filter_excluded),
            ),
            selectedIndex = state.filter.ordinal,
            onSelect = { actions.onFilter(EntryListFilter.entries[it]) },
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = actions.onQuery,
            placeholder = { Text(stringResource(R.string.review_search)) },
            leadingIcon = { Icon(painterResource(DesignR.drawable.ic_search), contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("review:search"),
        )
        EntryList(rows, onOpen = { actions.onOpen(it) }, modifier = Modifier.weight(1f))
        Footer(counts = state.counts, onExport = actions.onExport, onContinue = actions.onContinue)
    }
    state.selected?.let { row ->
        EntrySheet(
            row = row,
            onDismiss = { actions.onOpen(null) },
            onExclude = { reason -> actions.onExclude(row, reason) },
            onInclude = { note -> actions.onInclude(row, note) },
            onBlocklist = { actions.onBlocklist(row) },
        )
    }
}

/** Zero valid entries blocks the draw and says why (spec: S10 states). */
@Composable
private fun Footer(counts: EntryCounts?, onExport: () -> Unit, onContinue: () -> Unit) {
    val validCount = counts?.valid ?: 0
    if (counts != null && validCount == 0) {
        NoticeCard(
            title = stringResource(R.string.review_no_valid_title),
            body = stringResource(R.string.review_no_valid_body),
            tone = NoticeTone.Warning,
        )
    }
    TextButton(onClick = onExport, enabled = validCount > 0, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.review_export))
    }
    PrimaryButton(
        text = stringResource(R.string.review_continue),
        onClick = onContinue,
        enabled = validCount > 0,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Tiles(counts: EntryCounts?) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tile(R.string.review_tile_comments, counts?.total, Modifier.weight(1f))
        Tile(R.string.review_tile_valid, counts?.valid, Modifier.weight(1f))
        Tile(R.string.review_tile_excluded, counts?.excluded, Modifier.weight(1f))
    }
}

@Composable
private fun Tile(label: Int, value: Int?, modifier: Modifier) {
    Column(
        modifier = modifier
            .background(GiveawayTheme.colors.surface, GiveawayTheme.shapes.card)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(
            value?.let { formatCount(it) } ?: "–",
            style = GiveawayTheme.typography.titleLarge,
            color = GiveawayTheme.colors.onBackground,
        )
        Text(stringResource(label), style = GiveawayTheme.typography.caption, color = GiveawayTheme.colors.onMuted)
    }
}

@Composable
private fun EntryList(rows: LazyPagingItems<EntryRow>, onOpen: (EntryRow) -> Unit, modifier: Modifier) {
    if (rows.itemCount == 0) {
        Box(modifier = modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.review_no_results),
                style = GiveawayTheme.typography.body,
                color = GiveawayTheme.colors.onMuted,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth().testTag("review:list"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(count = rows.itemCount, key = rows.itemKey { it.commentId }) { index ->
            rows[index]?.let { row -> EntryRowItem(row, onClick = { onOpen(row) }) }
        }
    }
}

@Composable
private fun EntryRowItem(row: EntryRow, onClick: () -> Unit) {
    val colors = GiveawayTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp)
            .testTag("review:row:${row.commentId}"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(colors.surfaceMuted, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(row.username.take(1).uppercase(), style = GiveawayTheme.typography.bodyStrong, color = colors.onMuted)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(handle(row.username), style = GiveawayTheme.typography.bodyStrong, color = colors.onBackground)
            Text(
                row.text,
                style = GiveawayTheme.typography.caption.copy(textDirection = TextDirection.Content),
                color = colors.onMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StatusChip(statusLabel(row), if (row.isValid) ChipTone.Success else ChipTone.Neutral)
    }
}

@Composable
internal fun statusLabel(row: EntryRow): String =
    stringResource(row.exclusionReason?.let(::reasonLabel) ?: R.string.review_status_valid)
