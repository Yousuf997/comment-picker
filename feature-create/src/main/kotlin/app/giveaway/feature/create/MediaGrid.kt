package app.giveaway.feature.create

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.designsystem.component.NoticeCard
import app.giveaway.core.designsystem.component.NoticeTone
import app.giveaway.core.instagram.api.IgError
import app.giveaway.core.instagram.api.IgMedia
import coil3.compose.AsyncImage
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import app.giveaway.core.designsystem.R as DesignR

private const val GRID_COLUMNS = 3
private const val SKELETON_TILES = 12
private const val BADGE_SCRIM_ALPHA = 0.72f
private val TileShape = RoundedCornerShape(8.dp)
private val TileGap = 4.dp
private val SelectionRing = 3.dp

@Composable
internal fun MediaGrid(
    filter: MediaFilter,
    selected: IgMedia?,
    media: LazyPagingItems<IgMedia>,
    onSelect: (IgMedia) -> Unit,
) {
    val refresh = media.loadState.refresh
    when {
        media.itemCount > 0 -> LazyVerticalGrid(
            columns = GridCells.Fixed(GRID_COLUMNS),
            horizontalArrangement = Arrangement.spacedBy(TileGap),
            verticalArrangement = Arrangement.spacedBy(TileGap),
            modifier = Modifier.fillMaxSize().testTag("pick_post:grid"),
        ) {
            items(count = media.itemCount, key = media.itemKey { it.id }) { index ->
                media[index]?.let { item -> MediaTile(item, selected = item.id == selected?.id) { onSelect(item) } }
            }
            appendState(media)
        }
        refresh is LoadState.Loading -> SkeletonGrid()
        refresh is LoadState.Error -> LoadError(refresh.error, onRetry = media::retry)
        else -> Text(
            stringResource(emptyMessage(filter)),
            style = GiveawayTheme.typography.body,
            color = GiveawayTheme.colors.onMuted,
            modifier = Modifier.padding(top = 24.dp).testTag("pick_post:empty"),
        )
    }
}

private fun LazyGridScope.appendState(media: LazyPagingItems<IgMedia>) {
    when (val append = media.loadState.append) {
        is LoadState.Loading -> item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GiveawayTheme.colors.accent, modifier = Modifier.size(24.dp))
            }
        }
        is LoadState.Error -> item(span = { GridItemSpan(maxLineSpan) }) {
            LoadError(append.error, onRetry = media::retry)
        }
        is LoadState.NotLoading -> Unit
    }
}

@Composable
private fun MediaTile(media: IgMedia, selected: Boolean, onClick: () -> Unit) {
    val colors = GiveawayTheme.colors
    val description = describe(media, R.string.pick_post_tile)
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(TileShape)
            .background(colors.surfaceMuted)
            .then(if (selected) Modifier.border(SelectionRing, colors.accent, TileShape) else Modifier)
            .testTag("pick_post:tile:${media.id}")
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        AsyncImage(
            model = media.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().padding(if (selected) SelectionRing else 0.dp).clip(TileShape),
        )
        if (media.isReel) {
            Badge(Modifier.align(Alignment.TopStart)) {
                Icon(painterResource(DesignR.drawable.ic_reel), null, Modifier.size(14.dp), colors.onDrawBackground)
            }
        }
        Badge(Modifier.align(Alignment.BottomStart)) {
            Icon(painterResource(DesignR.drawable.ic_comment), null, Modifier.size(12.dp), colors.onDrawBackground)
            Text(
                formatCount(media.commentsCount),
                style = GiveawayTheme.typography.captionSmall,
                color = colors.onDrawBackground,
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .background(colors.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(DesignR.drawable.ic_check), null, Modifier.size(16.dp), colors.onAccent)
            }
        }
    }
}

/** Small dark label over the thumbnail, so it reads on any photo. */
@Composable
private fun Badge(modifier: Modifier, content: @Composable () -> Unit) {
    Row(
        modifier = modifier
            .padding(6.dp)
            .background(GiveawayTheme.colors.drawBackground.copy(alpha = BADGE_SCRIM_ALPHA), GiveawayTheme.shapes.chip)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) { content() }
}

@Composable
private fun SkeletonGrid() {
    val description = stringResource(R.string.pick_post_loading)
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        horizontalArrangement = Arrangement.spacedBy(TileGap),
        verticalArrangement = Arrangement.spacedBy(TileGap),
        userScrollEnabled = false,
        modifier = Modifier
            .fillMaxSize()
            .testTag("pick_post:loading")
            .clearAndSetSemantics { contentDescription = description },
    ) {
        items(SKELETON_TILES) {
            Box(Modifier.aspectRatio(1f).clip(TileShape).background(GiveawayTheme.colors.surfaceMuted))
        }
    }
}

@Composable
private fun LoadError(error: Throwable, onRetry: () -> Unit) {
    val igError = (error as? MediaLoadException)?.error
    NoticeCard(
        title = stringResource(R.string.pick_post_error_title),
        body = stringResource(errorMessage(igError)),
        tone = NoticeTone.Warning,
        actionLabel = stringResource(R.string.pick_post_retry),
        onAction = onRetry,
        modifier = Modifier.fillMaxWidth().testTag("pick_post:error"),
    )
}

private fun errorMessage(error: IgError?): Int = when (error) {
    IgError.Offline -> R.string.pick_post_error_offline
    IgError.TokenExpired -> R.string.pick_post_error_signed_out
    is IgError.RateLimited -> R.string.pick_post_error_busy
    else -> R.string.pick_post_error_other
}

private fun emptyMessage(filter: MediaFilter): Int = when (filter) {
    MediaFilter.ALL -> R.string.pick_post_empty_all
    MediaFilter.POSTS -> R.string.pick_post_empty_posts
    MediaFilter.REELS -> R.string.pick_post_empty_reels
}

/** "Reel from 25 Sep 2026, 312 comments", in the given template (summary line or TalkBack tile label). */
@Composable
internal fun describe(media: IgMedia, template: Int): String {
    val count = formatCount(media.commentsCount)
    val comments = pluralStringResource(R.plurals.pick_post_comments, media.commentsCount, count)
    val kind = stringResource(if (media.isReel) R.string.pick_post_kind_reel else R.string.pick_post_kind_post)
    return stringResource(template, kind, formatDate(media.timestamp), comments)
}

/** Counts and dates follow the app's language (spec: Localization). */
@Composable
internal fun formatCount(count: Int): String =
    NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(count)

@Composable
internal fun formatDate(instant: Instant): String {
    val locale = LocalConfiguration.current.locales[0]
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).withZone(ZoneId.systemDefault())
        .format(instant)
}
