package app.giveaway.core.media

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import app.giveaway.draw.Pick
import kotlin.math.floor

/** Colors (ARGB) and faces for the recorded scene, taken from the app theme by the caller. */
data class SceneStyle(
    val background: Int,
    val text: Int,
    val muted: Int,
    val accent: Int,
    val onAccent: Int,
    val display: Typeface,
    val body: Typeface,
    val code: Typeface,
)

/** The words on the recorded scene, in the app's language, supplied by the caller (spec: Localization). */
interface SceneText {
    fun picking(pick: Pick): String

    fun announcement(pick: Pick): String

    val done: String

    val pickedSoFar: String

    /** The label above the draw code on the closing frame, e.g. "Draw code in the caption". */
    val drawCodeLabel: String

    fun entries(count: Int): String
}

/** Draws one frame of the recorded draw at a point on the timeline. */
fun interface SceneRenderer {
    fun draw(canvas: Canvas, timeline: DrawTimeline, timeMs: Long)
}

/**
 * Draws one frame of the draw (spec: Draw recording, requirement 3): title, entry count, the reel landing on each
 * pick, the picks so far, and a closing summary with the commit hash. Sizes scale with the frame, so 1080 x 1920 and
 * the 720 x 1280 fallback look the same.
 */
class DrawSceneRenderer(
    private val style: SceneStyle,
    private val text: SceneText,
    private val title: String,
    private val entryCount: Int,
    private val commitHash: String,
) : SceneRenderer {
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bar = RectF()

    override fun draw(canvas: Canvas, timeline: DrawTimeline, timeMs: Long) {
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        val unit = w / UNITS_ACROSS
        canvas.drawColor(style.background)
        val frame = timeline.frameAt(timeMs)
        centred(canvas, title, style.body, unit * TITLE_SIZE, style.muted, w / 2, unit * TITLE_Y)
        val entries = text.entries(entryCount)
        centred(canvas, entries, style.body, unit * SMALL_SIZE, style.muted, w / 2, unit * ENTRIES_Y)
        if (timeMs >= timeline.durationMs) {
            summary(canvas, frame.landed, unit, w)
            return
        }
        val heading = frame.current?.let { text.picking(it.pick) } ?: text.done
        centred(canvas, heading, style.display, unit * HEADING_SIZE, style.text, w / 2, unit * HEADING_Y)
        reel(canvas, frame, unit, w, h * REEL_CENTRE)
        picked(canvas, frame.landed, unit, unit * PICKED_Y)
    }

    private fun reel(canvas: Canvas, frame: DrawTimeline.Frame, unit: Float, w: Float, centreY: Float) {
        val row = unit * ROW_HEIGHT
        bar.set(unit * MARGIN, centreY - row / 2, w - unit * MARGIN, centreY + row / 2)
        fill.color = style.accent
        canvas.drawRoundRect(bar, unit * RADIUS, unit * RADIUS, fill)
        val names = frame.current?.reel ?: return
        val base = floor(frame.reelPosition).toInt()
        val fraction = frame.reelPosition - base
        canvas.save()
        canvas.clipRect(0f, centreY - row * VISIBLE_HALF, w, centreY + row * VISIBLE_HALF)
        for (offset in -VISIBLE_ROWS..VISIBLE_ROWS) {
            val name = names.getOrNull(base + offset) ?: continue
            val centred = offset == 0 && fraction < CENTRE_TOLERANCE
            val y = centreY + (offset - fraction) * row
            val color = if (centred) style.onAccent else style.muted
            centred(canvas, "@$name", style.body, unit * NAME_SIZE, color, w / 2, y)
        }
        canvas.restore()
    }

    private fun picked(canvas: Canvas, landed: List<Pick>, unit: Float, top: Float) {
        if (landed.isEmpty()) return
        left(canvas, text.pickedSoFar, style.body, unit * SMALL_SIZE, style.muted, unit * MARGIN, top)
        landed.forEachIndexed { i, pick ->
            val y = top + unit * LIST_STEP * (i + 1)
            left(canvas, text.announcement(pick), style.body, unit * LIST_SIZE, style.text, unit * MARGIN, y)
        }
    }

    /** The closing frame: every pick and the draw code anyone can check (spec: ends on a summary frame). */
    private fun summary(canvas: Canvas, landed: List<Pick>, unit: Float, w: Float) {
        centred(canvas, text.done, style.display, unit * HEADING_SIZE, style.text, w / 2, unit * HEADING_Y)
        landed.forEachIndexed { i, pick ->
            val y = unit * (SUMMARY_TOP + LIST_STEP * i)
            centred(canvas, text.announcement(pick), style.body, unit * LIST_SIZE, style.text, w / 2, y)
        }
        val codeY = unit * (SUMMARY_TOP + LIST_STEP * (landed.size + 1))
        centred(canvas, text.drawCodeLabel, style.body, unit * SMALL_SIZE, style.muted, w / 2, codeY)
        // The full 64-character hash on two lines, so nothing is cut off.
        val half = commitHash.length / 2
        val first = "#draw ${commitHash.take(half)}"
        centred(canvas, first, style.code, unit * CODE_SIZE, style.accent, w / 2, codeY + unit * CODE_STEP)
        val second = codeY + unit * CODE_STEP * 2
        centred(canvas, commitHash.drop(half), style.code, unit * CODE_SIZE, style.accent, w / 2, second)
    }

    private fun centred(canvas: Canvas, s: String, face: Typeface, size: Float, color: Int, x: Float, y: Float) {
        setUp(face, size, color, Paint.Align.CENTER)
        canvas.drawText(ellipsize(s, canvas.width * TEXT_WIDTH), x, y - (paint.ascent() + paint.descent()) / 2, paint)
    }

    private fun left(canvas: Canvas, s: String, face: Typeface, size: Float, color: Int, x: Float, y: Float) {
        setUp(face, size, color, Paint.Align.LEFT)
        canvas.drawText(ellipsize(s, canvas.width - x * 2), x, y - (paint.ascent() + paint.descent()) / 2, paint)
    }

    private fun setUp(face: Typeface, size: Float, color: Int, align: Paint.Align) {
        paint.typeface = face
        paint.textSize = size
        paint.color = color
        paint.textAlign = align
    }

    private fun ellipsize(s: String, width: Float): String =
        TextUtils.ellipsize(s, paint, width, TextUtils.TruncateAt.END).toString()

    private companion object {
        /** Layout in units of 1/100 of the frame width. */
        const val UNITS_ACROSS = 100f
        const val MARGIN = 7f
        const val TITLE_SIZE = 4.2f
        const val TITLE_Y = 14f
        const val SMALL_SIZE = 3.4f
        const val ENTRIES_Y = 20f
        const val HEADING_SIZE = 7f
        const val HEADING_Y = 32f
        const val REEL_CENTRE = 0.42f
        const val ROW_HEIGHT = 12f
        const val RADIUS = 3f
        const val NAME_SIZE = 5.6f
        const val VISIBLE_ROWS = 3
        const val VISIBLE_HALF = 2.5f
        const val CENTRE_TOLERANCE = 0.05f
        const val PICKED_Y = 118f
        const val LIST_STEP = 7f
        const val LIST_SIZE = 4.4f
        const val SUMMARY_TOP = 50f
        const val CODE_SIZE = 3.6f
        const val CODE_STEP = 5.5f
        const val TEXT_WIDTH = 0.86f
    }
}
