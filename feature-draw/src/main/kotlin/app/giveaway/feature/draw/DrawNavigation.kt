package app.giveaway.feature.draw

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
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

/** [onRedrawn] runs once the result is cleared and the giveaway can be drawn again (plan A30). */
fun NavGraphBuilder.winnersScreen(
    onCreateCertificate: (giveawayId: Long) -> Unit,
    onRedrawn: (giveawayId: Long) -> Unit,
) {
    composable<WinnersRoute> { entry ->
        val giveawayId = entry.toRoute<WinnersRoute>().giveawayId
        WinnersScreen(
            onCreateCertificate = { onCreateCertificate(giveawayId) },
            onRedraw = rememberRedraw(giveawayId, onRedrawn),
        )
    }
}

fun NavGraphBuilder.certificateScreen(onDone: () -> Unit, onRedrawn: (giveawayId: Long) -> Unit) {
    composable<CertificateRoute> { entry ->
        val giveawayId = entry.toRoute<CertificateRoute>().giveawayId
        CertificateScreen(onDone = onDone, onRedraw = rememberRedraw(giveawayId, onRedrawn))
    }
}
