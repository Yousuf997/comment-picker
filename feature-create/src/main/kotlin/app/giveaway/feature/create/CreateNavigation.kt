package app.giveaway.feature.create

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.giveaway.core.instagram.api.IgMedia
import app.giveaway.core.instagram.api.MediaKind
import kotlinx.serialization.Serializable
import java.time.Instant

/** S6 Pick post (wizard step 1). */
@Serializable
data object PickPostRoute

/**
 * S7 Set rules (wizard step 2) for the post picked on S6. The post's details travel with the route, so S7 opens the
 * giveaway without asking Instagram again.
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

/** S6 for an existing giveaway: change its post at any stage (plan A32). */
@Serializable
data class ChangePostRoute(val giveawayId: Long)

/** S7 for an existing giveaway: edit its rules at any stage (plan A31). */
@Serializable
data class EditRulesRoute(val giveawayId: Long)

/** S9 Import comments (wizard step 3). */
@Serializable
data class ImportCommentsRoute(val giveawayId: Long)

/** S10 Review entries (wizard step 4). */
@Serializable
data class ReviewEntriesRoute(val giveawayId: Long)

fun NavGraphBuilder.pickPostScreen(onBack: () -> Unit, onPostPicked: (SetRulesRoute) -> Unit) {
    composable<PickPostRoute> { PickPostScreen(onBack = onBack, onContinue = { onPostPicked(SetRulesRoute(it)) }) }
}

/** [onRulesSaved] runs once Done has opened the new giveaway (plan A35), saying whether entries closed "Now". */
fun NavGraphBuilder.setRulesScreen(
    onBack: () -> Unit,
    onRulesSaved: (giveawayId: Long, closedNow: Boolean) -> Unit,
) {
    composable<SetRulesRoute> { SetRulesScreen(onBack = onBack, onSaved = onRulesSaved) }
}

fun NavGraphBuilder.importCommentsScreen(onBack: () -> Unit, onReviewEntries: (giveawayId: Long) -> Unit) {
    composable<ImportCommentsRoute> { entry ->
        val giveawayId = entry.toRoute<ImportCommentsRoute>().giveawayId
        ImportCommentsScreen(onBack = onBack, onReviewEntries = { onReviewEntries(giveawayId) })
    }
}

fun NavGraphBuilder.reviewEntriesScreen(onBack: () -> Unit, onContinueToDraw: (giveawayId: Long) -> Unit) {
    composable<ReviewEntriesRoute> { entry ->
        val giveawayId = entry.toRoute<ReviewEntriesRoute>().giveawayId
        ReviewEntriesScreen(onBack = onBack, onContinueToDraw = { onContinueToDraw(giveawayId) })
    }
}

/** [onChanged] runs once the new post is saved (or the same post was picked again). */
fun NavGraphBuilder.changePostScreen(onBack: () -> Unit, onChanged: (giveawayId: Long) -> Unit) {
    composable<ChangePostRoute> { entry ->
        val giveawayId = entry.toRoute<ChangePostRoute>().giveawayId
        ChangePostScreen(onBack = onBack, onChanged = { onChanged(giveawayId) })
    }
}

/** [onSaved] gets the step to open next (see [app.giveaway.core.data.giveaway.WizardProgress]). */
fun NavGraphBuilder.editRulesScreen(onBack: () -> Unit, onSaved: (giveawayId: Long, nextStep: Int) -> Unit) {
    composable<EditRulesRoute> { entry ->
        val giveawayId = entry.toRoute<EditRulesRoute>().giveawayId
        EditRulesScreen(onBack = onBack, onSaved = { step -> onSaved(giveawayId, step) })
    }
}
