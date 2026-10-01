package app.giveaway.feature.create

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.giveaway.core.designsystem.component.PlaceholderAction
import app.giveaway.core.designsystem.component.PlaceholderScreen
import app.giveaway.core.instagram.api.IgMedia
import app.giveaway.core.instagram.api.MediaKind
import kotlinx.serialization.Serializable
import java.time.Instant

/** S6 Pick post (wizard step 1). */
@Serializable
data object PickPostRoute

/**
 * S7 Set rules (wizard step 2) for the post picked on S6. The post's details travel with the route, so S7 creates the
 * draft without asking Instagram again.
 */
@Serializable
data class SetRulesRoute(
    val mediaId: String,
    // The kind travels by name: an enum argument would need its serializer kept from R8.
    val mediaKind: String = MediaKind.IMAGE.name,
    val isReel: Boolean = false,
    val thumbnailUrl: String? = null,
    val caption: String? = null,
    val postedAtEpochSecond: Long = 0,
    val commentsCount: Int = 0,
    val permalink: String? = null,
) {
    constructor(media: IgMedia) : this(
        mediaId = media.id,
        mediaKind = media.kind.name,
        isReel = media.isReel,
        thumbnailUrl = media.thumbnailUrl,
        caption = media.caption,
        postedAtEpochSecond = media.timestamp.epochSecond,
        commentsCount = media.commentsCount,
        permalink = media.permalink,
    )

    fun toMedia() = IgMedia(
        id = mediaId,
        kind = MediaKind.valueOf(mediaKind),
        isReel = isReel,
        thumbnailUrl = thumbnailUrl,
        caption = caption,
        timestamp = Instant.ofEpochSecond(postedAtEpochSecond),
        commentsCount = commentsCount,
        permalink = permalink,
    )
}

/** S8 Lock in the draw (wizard step 3). */
@Serializable
data class LockInDrawRoute(val giveawayId: Long)

/** S9 Import comments (wizard step 4). */
@Serializable
data class ImportCommentsRoute(val giveawayId: Long)

/** S10 Review entries (wizard step 5). */
@Serializable
data class ReviewEntriesRoute(val giveawayId: Long)

fun NavGraphBuilder.pickPostScreen(onBack: () -> Unit, onPostPicked: (SetRulesRoute) -> Unit) {
    composable<PickPostRoute> { PickPostScreen(onBack = onBack, onContinue = { onPostPicked(SetRulesRoute(it)) }) }
}

fun NavGraphBuilder.setRulesScreen(onBack: () -> Unit, onRulesSaved: (giveawayId: Long) -> Unit) {
    composable<SetRulesRoute> { SetRulesScreen(onBack = onBack, onSaved = onRulesSaved) }
}

fun NavGraphBuilder.lockInDrawScreen(onBack: () -> Unit, onDone: () -> Unit) {
    composable<LockInDrawRoute> { LockInScreen(onBack = onBack, onDone = onDone) }
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
