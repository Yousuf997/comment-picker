package app.giveaway.feature.draw

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.content.res.ResourcesCompat
import app.giveaway.core.designsystem.GiveawayTheme
import app.giveaway.core.designsystem.handle
import app.giveaway.core.media.SceneStyle
import app.giveaway.core.media.SceneText
import app.giveaway.draw.Pick
import app.giveaway.draw.Role
import java.text.NumberFormat
import app.giveaway.core.designsystem.R as DesignR

/** The recorded scene in the app's colors and fonts (spec: Design system), the same as S12 shows. */
@Composable
internal fun rememberSceneStyle(context: Context): SceneStyle {
    val colors = GiveawayTheme.colors
    val arabic = LocalConfiguration.current.locales[0].language == "ar"
    fun font(id: Int, fallback: Typeface) = runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() ?: fallback
    val body = font(if (arabic) DesignR.font.ibm_plex_sans_arabic_medium else DesignR.font.manrope, Typeface.DEFAULT)
    return SceneStyle(
        background = colors.drawBackground.toArgb(),
        text = colors.onDrawBackground.toArgb(),
        muted = colors.onDrawMuted.toArgb(),
        accent = colors.accent.toArgb(),
        onAccent = colors.onAccent.toArgb(),
        display = font(
            if (arabic) DesignR.font.ibm_plex_sans_arabic_bold else DesignR.font.bricolage_grotesque,
            Typeface.DEFAULT_BOLD,
        ),
        body = body,
        code = font(DesignR.font.jetbrains_mono, Typeface.MONOSPACE),
    )
}

/** The scene's words in the app's language; handles stay left to right (spec: Localization). */
internal class AndroidSceneText(
    private val context: Context,
    private val winners: Int,
    private val alternates: Int,
) : SceneText {
    private val numbers = NumberFormat.getIntegerInstance(context.resources.configuration.locales[0])

    private fun n(value: Int) = numbers.format(value)

    override fun picking(pick: Pick): String = if (pick.role == Role.WINNER) {
        context.getString(R.string.drawing_picking_winner, n(pick.position), n(winners))
    } else {
        context.getString(R.string.drawing_picking_alternate, n(pick.position - winners), n(alternates))
    }

    override fun announcement(pick: Pick): String = if (pick.role == Role.WINNER) {
        context.getString(R.string.drawing_announce_winner, n(pick.position), handle(pick.username))
    } else {
        context.getString(R.string.drawing_announce_alternate, n(pick.position - winners), handle(pick.username))
    }

    override val done: String get() = context.getString(R.string.drawing_done)

    override val pickedSoFar: String get() = context.getString(R.string.drawing_picked_so_far)

    override val drawCodeLabel: String get() = context.getString(R.string.drawing_video_code)

    override fun entries(count: Int): String =
        context.resources.getQuantityString(R.plurals.draw_entries, count, n(count))
}
