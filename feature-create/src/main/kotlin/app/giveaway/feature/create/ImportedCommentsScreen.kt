package app.giveaway.feature.create

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import app.giveaway.core.data.db.CommentEntity
import app.giveaway.core.data.importing.ImportRepository
import app.giveaway.core.designsystem.GiveawayDimens
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.designsystem.component.NoticeCard
import app.giveaway.core.designsystem.component.NoticeTone
import app.giveaway.core.designsystem.formatCount
import app.giveaway.core.designsystem.formatDateTime
import app.giveaway.core.designsystem.handle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import app.giveaway.core.designsystem.R as DesignR

/** The comments imported so far, opened from S9: what Instagram returned, before any rule is applied. */
@HiltViewModel
class ImportedCommentsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    imports: ImportRepository,
) : ViewModel() {
    private val giveawayId = savedStateHandle.toRoute<ImportedCommentsRoute>().giveawayId

    val comments: Flow<PagingData<CommentEntity>> = imports.comments(giveawayId).cachedIn(viewModelScope)

    /** Null until counted, so an empty list doesn't flash while loading. */
    val count: StateFlow<Int?> = imports.commentCount(giveawayId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

@Composable
internal fun ImportedCommentsScreen(onBack: () -> Unit, viewModel: ImportedCommentsViewModel = hiltViewModel()) {
    val count by viewModel.count.collectAsStateWithLifecycle()
    ImportedCommentsScreen(count = count, comments = viewModel.comments.collectAsLazyPagingItems(), onBack = onBack)
}

/** Who wrote what, and when, oldest first. Read-only: entries are reviewed and changed on S10. */
@Composable
internal fun ImportedCommentsScreen(count: Int?, comments: LazyPagingItems<CommentEntity>, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GiveawayTheme.colors.background)
            .safeDrawingPadding()
            .padding(GiveawayDimens.screenPadding)
            .testTag("screen:comments"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(DesignR.drawable.ic_arrow_back), stringResource(DesignR.string.navigate_back))
            }
            Text(
                stringResource(R.string.comments_title),
                style = GiveawayTheme.typography.titleLarge,
                color = GiveawayTheme.colors.onBackground,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        }
        when (count) {
            null -> Unit
            0 -> NoticeCard(
                title = stringResource(R.string.comments_empty_title),
                body = stringResource(R.string.comments_empty_body),
                tone = NoticeTone.Info,
            )
            else -> {
                Text(
                    pluralStringResource(R.plurals.comments_count, count, formatCount(count)),
                    style = GiveawayTheme.typography.caption,
                    color = GiveawayTheme.colors.onMuted,
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f).testTag("comments:list")) {
                    items(count = comments.itemCount, key = comments.itemKey { it.id }) { index ->
                        comments[index]?.let { CommentRow(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(comment: CommentEntity) {
    val colors = GiveawayTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .semantics(mergeDescendants = true) {}
            .testTag("comments:row:${comment.id}"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                handle(comment.username),
                style = GiveawayTheme.typography.bodyStrong,
                color = colors.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(formatDateTime(comment.timestamp), style = GiveawayTheme.typography.caption, color = colors.onMuted)
        }
        Text(
            comment.text,
            style = GiveawayTheme.typography.body.copy(textDirection = TextDirection.Content),
            color = colors.onBackground,
        )
    }
    HorizontalDivider(color = colors.outline)
}
