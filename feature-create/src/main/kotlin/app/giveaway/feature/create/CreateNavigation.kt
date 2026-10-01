package app.giveaway.feature.create

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.giveaway.core.designsystem.component.PlaceholderAction
import app.giveaway.core.designsystem.component.PlaceholderScreen
import kotlinx.serialization.Serializable

/** S6 Pick post (wizard step 1). */
@Serializable
data object PickPostRoute

/** S7 Set rules (wizard step 2) for the chosen Instagram media. */
@Serializable
data class SetRulesRoute(val mediaId: String)

/** S8 Lock in the draw (wizard step 3). */
@Serializable
data class LockInDrawRoute(val giveawayId: Long)

/** S9 Import comments (wizard step 4). */
@Serializable
data class ImportCommentsRoute(val giveawayId: Long)

/** S10 Review entries (wizard step 5). */
@Serializable
data class ReviewEntriesRoute(val giveawayId: Long)

fun NavGraphBuilder.pickPostScreen(onPostPicked: (mediaId: String) -> Unit) {
    composable<PickPostRoute> { PickPostScreen(onPostPicked) }
}

fun NavGraphBuilder.setRulesScreen(onRulesSaved: (giveawayId: Long) -> Unit) {
    composable<SetRulesRoute> { SetRulesScreen(onRulesSaved) }
}

fun NavGraphBuilder.lockInDrawScreen(onDone: () -> Unit) {
    composable<LockInDrawRoute> { LockInDrawScreen(onDone) }
}

fun NavGraphBuilder.importCommentsScreen(onReviewEntries: (giveawayId: Long) -> Unit) {
    composable<ImportCommentsRoute> { entry ->
        val giveawayId = entry.toRoute<ImportCommentsRoute>().giveawayId
        ImportCommentsScreen(onReviewEntries = { onReviewEntries(giveawayId) })
    }
}

fun NavGraphBuilder.reviewEntriesScreen(onContinueToDraw: (giveawayId: Long) -> Unit) {
    composable<ReviewEntriesRoute> { entry ->
        val giveawayId = entry.toRoute<ReviewEntriesRoute>().giveawayId
        ReviewEntriesScreen(onContinueToDraw = { onContinueToDraw(giveawayId) })
    }
}

/** Placeholder media ID until S6 lists real posts (C-11). */
private const val PLACEHOLDER_MEDIA_ID = "placeholder"

/** Placeholder giveaway ID until S7 creates a real draft (C-12). */
private const val PLACEHOLDER_GIVEAWAY_ID = 0L

@Composable
internal fun PickPostScreen(onPostPicked: (String) -> Unit) = PlaceholderScreen(
    screenId = "S6",
    title = stringResource(R.string.pick_post_title),
    actions = listOf(
        PlaceholderAction(stringResource(R.string.wizard_continue), onClick = { onPostPicked(PLACEHOLDER_MEDIA_ID) }),
    ),
)

@Composable
internal fun SetRulesScreen(onRulesSaved: (Long) -> Unit) = PlaceholderScreen(
    screenId = "S7",
    title = stringResource(R.string.set_rules_title),
    actions = listOf(
        PlaceholderAction(
            stringResource(R.string.wizard_continue),
            onClick = { onRulesSaved(PLACEHOLDER_GIVEAWAY_ID) },
        ),
    ),
)

@Composable
internal fun LockInDrawScreen(onDone: () -> Unit) = PlaceholderScreen(
    screenId = "S8",
    title = stringResource(R.string.lock_in_title),
    actions = listOf(PlaceholderAction(stringResource(R.string.lock_in_done), onClick = onDone)),
)

@Composable
internal fun ImportCommentsScreen(onReviewEntries: () -> Unit) = PlaceholderScreen(
    screenId = "S9",
    title = stringResource(R.string.import_title),
    actions = listOf(PlaceholderAction(stringResource(R.string.import_review), onClick = onReviewEntries)),
)

@Composable
internal fun ReviewEntriesScreen(onContinueToDraw: () -> Unit) = PlaceholderScreen(
    screenId = "S10",
    title = stringResource(R.string.review_title),
    actions = listOf(PlaceholderAction(stringResource(R.string.review_continue), onClick = onContinueToDraw)),
)
