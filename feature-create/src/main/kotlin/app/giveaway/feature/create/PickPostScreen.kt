package app.giveaway.feature.create

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import app.giveaway.core.designsystem.GiveawayDimens
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.designsystem.component.PrimaryButton
import app.giveaway.core.designsystem.component.SegmentedControl
import app.giveaway.core.designsystem.component.WizardHeader
import app.giveaway.core.instagram.api.IgMedia

@Composable
internal fun PickPostScreen(
    onBack: () -> Unit,
    onContinue: (IgMedia) -> Unit,
    viewModel: PickPostViewModel = hiltViewModel(),
) {
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    PickPostScreen(
        filter = filter,
        selected = selected,
        media = viewModel.media.collectAsLazyPagingItems(),
        onBack = onBack,
        onFilter = viewModel::onFilter,
        onSelect = viewModel::onSelect,
        onContinue = onContinue,
    )
}

/** S6 Pick post (spec): segmented filter, three-column grid of the account's media, Continue once one is picked. */
@Composable
internal fun PickPostScreen(
    filter: MediaFilter,
    selected: IgMedia?,
    media: LazyPagingItems<IgMedia>,
    onBack: () -> Unit,
    onFilter: (MediaFilter) -> Unit,
    onSelect: (IgMedia) -> Unit,
    onContinue: (IgMedia) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GiveawayTheme.colors.background)
            .safeDrawingPadding()
            .padding(GiveawayDimens.screenPadding)
            .testTag("screen:S6"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WizardHeader(title = stringResource(R.string.wizard_new_giveaway), step = 1, onBack = onBack)
        Text(
            stringResource(R.string.pick_post_title),
            style = GiveawayTheme.typography.display,
            color = GiveawayTheme.colors.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        SegmentedControl(
            options = listOf(
                stringResource(R.string.pick_post_filter_all),
                stringResource(R.string.pick_post_filter_posts),
                stringResource(R.string.pick_post_filter_reels),
            ),
            selectedIndex = filter.ordinal,
            onSelect = { onFilter(MediaFilter.entries[it]) },
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            MediaGrid(filter, selected, media, onSelect)
        }
        Text(
            selectionSummary(selected),
            style = GiveawayTheme.typography.caption,
            color = GiveawayTheme.colors.onMuted,
        )
        PrimaryButton(
            text = stringResource(R.string.wizard_continue),
            onClick = { selected?.let(onContinue) },
            enabled = selected != null,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun selectionSummary(selected: IgMedia?): String =
    selected?.let { describe(it, R.string.pick_post_selected) } ?: stringResource(R.string.pick_post_none_selected)
