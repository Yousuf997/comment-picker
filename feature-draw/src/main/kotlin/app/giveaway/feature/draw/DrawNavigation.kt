package app.giveaway.feature.draw

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.toRoute
import app.giveaway.core.designsystem.component.PlaceholderAction
import app.giveaway.core.designsystem.component.PlaceholderScreen
import kotlinx.serialization.Serializable

/** S11 Draw (wizard step 6, dark stage). */
@Serializable
data class DrawRoute(val giveawayId: Long)

/** S12 Drawing and recording (dark stage). */
@Serializable
data class DrawingRoute(val giveawayId: Long)

/** S13 Save video popup, shown over S14. The user must choose; it can't be dismissed by tapping outside. */
@Serializable
data class SaveVideoRoute(val giveawayId: Long)

/** S14 Winners. */
@Serializable
data class WinnersRoute(val giveawayId: Long)

/** S15 Certificate. */
@Serializable
data class CertificateRoute(val giveawayId: Long)

fun NavGraphBuilder.drawScreen(onDrawWinners: (giveawayId: Long) -> Unit) {
    composable<DrawRoute> { entry ->
        val giveawayId = entry.toRoute<DrawRoute>().giveawayId
        DrawScreen(onDrawWinners = { onDrawWinners(giveawayId) })
    }
}

fun NavGraphBuilder.drawingScreen(onDrawFinished: (giveawayId: Long) -> Unit) {
    composable<DrawingRoute> { entry ->
        val giveawayId = entry.toRoute<DrawingRoute>().giveawayId
        DrawingScreen(onDrawFinished = { onDrawFinished(giveawayId) })
    }
}

fun NavGraphBuilder.saveVideoSheet(onChoiceMade: () -> Unit) {
    dialog<SaveVideoRoute>(
        dialogProperties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) { SaveVideoSheet(onChoiceMade) }
}

fun NavGraphBuilder.winnersScreen(onCreateCertificate: (giveawayId: Long) -> Unit) {
    composable<WinnersRoute> { entry ->
        val giveawayId = entry.toRoute<WinnersRoute>().giveawayId
        WinnersScreen(onCreateCertificate = { onCreateCertificate(giveawayId) })
    }
}

fun NavGraphBuilder.certificateScreen(onDone: () -> Unit) {
    composable<CertificateRoute> { CertificateScreen(onDone) }
}

@Composable
internal fun DrawScreen(onDrawWinners: () -> Unit) = PlaceholderScreen(
    screenId = "S11",
    title = stringResource(R.string.draw_title),
    onStage = true,
    actions = listOf(
        PlaceholderAction(stringResource(R.string.draw_winners), onClick = onDrawWinners),
        PlaceholderAction(stringResource(R.string.draw_test_first), primary = false, onClick = {}),
    ),
)

@Composable
internal fun DrawingScreen(onDrawFinished: () -> Unit) = PlaceholderScreen(
    screenId = "S12",
    title = stringResource(R.string.drawing_title),
    onStage = true,
    actions = listOf(PlaceholderAction(stringResource(R.string.drawing_finish), onClick = onDrawFinished)),
)

@Composable
internal fun SaveVideoSheet(onChoiceMade: () -> Unit) = PlaceholderScreen(
    screenId = "S13",
    title = stringResource(R.string.save_video_title),
    actions = listOf(
        PlaceholderAction(stringResource(R.string.save_video_save), onClick = onChoiceMade),
        PlaceholderAction(stringResource(R.string.save_video_discard), primary = false, onClick = onChoiceMade),
    ),
)

@Composable
internal fun WinnersScreen(onCreateCertificate: () -> Unit) = PlaceholderScreen(
    screenId = "S14",
    title = stringResource(R.string.winners_title),
    actions = listOf(
        PlaceholderAction(stringResource(R.string.winners_create_certificate), onClick = onCreateCertificate),
    ),
)

@Composable
internal fun CertificateScreen(onDone: () -> Unit) = PlaceholderScreen(
    screenId = "S15",
    title = stringResource(R.string.certificate_title),
    actions = listOf(PlaceholderAction(stringResource(R.string.certificate_done), primary = false, onClick = onDone)),
)
