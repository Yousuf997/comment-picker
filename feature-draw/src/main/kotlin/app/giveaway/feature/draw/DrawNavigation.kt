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
data class DrawingRoute(val giveawayId: Long, val record: Boolean = false)

/** S14 Winners. */
@Serializable
data class WinnersRoute(val giveawayId: Long)

/** S15 Certificate. */
@Serializable
data class CertificateRoute(val giveawayId: Long)

fun NavGraphBuilder.drawScreen(
    onBack: () -> Unit,
    onDrawn: (giveawayId: Long, record: Boolean) -> Unit,
    onAlreadyDrawn: (giveawayId: Long) -> Unit,
) {
    composable<DrawRoute> { entry ->
        val giveawayId = entry.toRoute<DrawRoute>().giveawayId
        DrawStageScreen(
            onBack = onBack,
            onDrawn = { record -> onDrawn(giveawayId, record) },
            onAlreadyDrawn = { onAlreadyDrawn(giveawayId) },
        )
    }
}

fun NavGraphBuilder.drawingScreen(onDrawFinished: (giveawayId: Long) -> Unit) {
    composable<DrawingRoute> { entry ->
        val giveawayId = entry.toRoute<DrawingRoute>().giveawayId
        DrawingScreen(onFinished = { onDrawFinished(giveawayId) })
    }
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
internal fun CertificateScreen(onDone: () -> Unit) = PlaceholderScreen(
    screenId = "S15",
    title = stringResource(R.string.certificate_title),
    actions = listOf(PlaceholderAction(stringResource(R.string.certificate_done), primary = false, onClick = onDone)),
)
