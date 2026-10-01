package app.giveaway.core.media

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristic
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.graphics.withTranslation
import app.giveaway.core.media.CertificateContent.Kind
import app.giveaway.core.media.CertificateContent.Row
import java.util.Locale

/** A piece of the certificate, drawn at the content's top-left corner, in points. */
internal class Block(val height: Float, val keepWithNext: Boolean = false, val draw: Canvas.() -> Unit)

/**
 * Lays a certificate out in blocks [contentWidth] points wide and splits them into pages. Text goes through
 * StaticLayout in the content's direction, so Arabic paragraphs and columns mirror (spec: Localization).
 */
internal class CertificateLayout(
    private val style: CertificateStyle,
    private val content: CertificateContent,
    private val contentWidth: Float,
) {
    private val direction = if (content.rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR
    private val headingPaint = paint(style.display, HEADING_SIZE, style.text, EXTRA_BOLD)
    private val titlePaint = paint(style.bold, TITLE_SIZE, style.text, BOLD)
    private val overlinePaint = paint(style.bold, OVERLINE_SIZE, style.accentOnSoft, BOLD).apply {
        letterSpacing = OVERLINE_TRACKING
    }
    private val labelPaint = paint(style.body, LABEL_SIZE, style.muted, MEDIUM)
    private val warningPaint = paint(style.bold, LABEL_SIZE, style.accentOnSoft, SEMI_BOLD)
    private val paints = mapOf(
        Kind.TEXT to paint(style.body, BODY_SIZE, style.text, MEDIUM),
        Kind.STRONG to paint(style.bold, BODY_SIZE, style.text, BOLD),
        Kind.CODE to paint(style.code, CODE_SIZE, style.text, MEDIUM),
        Kind.WARNING to warningPaint,
    )
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

    /** The blocks split into pages of [height] points; a section heading never ends a page. */
    fun pages(height: Float): List<List<Block>> {
        val blocks = blocks()
        val pages = mutableListOf(mutableListOf<Block>())
        var used = 0f
        blocks.forEachIndexed { i, block ->
            val next = if (block.keepWithNext) blocks.getOrNull(i + 1)?.height ?: 0f else 0f
            if (used + block.height + next > height && pages.last().isNotEmpty()) {
                pages += mutableListOf<Block>()
                used = 0f
            }
            pages.last() += block
            used += block.height
        }
        return pages
    }

    private fun blocks(): List<Block> = buildList {
        add(header())
        content.sections.forEach { section ->
            add(sectionHeading(section.heading))
            section.rows.forEach { add(row(it)) }
        }
        add(footer())
    }

    private fun header(): Block {
        val textWidth = contentWidth - SEAL_SIZE - GAP
        val heading = layout(content.heading, headingPaint, textWidth)
        val title = layout(content.title, titlePaint, textWidth)
        val height = maxOf(heading.height + TITLE_GAP + title.height, SEAL_SIZE)
        return Block(height + SECTION_GAP) {
            val textX = if (content.rtl) SEAL_SIZE + GAP else 0f
            text(heading, textX, 0f)
            text(title, textX, heading.height + TITLE_GAP)
            seal(if (content.rtl) 0f else contentWidth - SEAL_SIZE)
        }
    }

    private fun sectionHeading(heading: String): Block {
        val text = layout(heading.uppercase(Locale.ROOT), overlinePaint, contentWidth)
        return Block(RULE + SECTION_GAP + text.height + ROW_GAP, keepWithNext = true) {
            fill.style = Paint.Style.FILL
            fill.color = style.outline
            drawRect(0f, 0f, contentWidth, RULE, fill)
            text(text, 0f, RULE + SECTION_GAP)
        }
    }

    private fun row(row: Row): Block = if (row.kind == Kind.WARNING) warning(row.value) else field(row)

    /** A label column and a value column, mirrored in Arabic; a row without a label spans the width. */
    private fun field(row: Row): Block {
        val paint = paints.getValue(row.kind)
        if (row.label == null) {
            val text = layout(row.value, paint, contentWidth)
            return Block(text.height + ROW_GAP) { text(text, 0f, 0f) }
        }
        val labelText = layout(row.label, labelPaint, LABEL_WIDTH)
        val valueText = layout(row.value, paint, contentWidth - LABEL_WIDTH - GAP)
        return Block(maxOf(labelText.height, valueText.height) + ROW_GAP) {
            text(labelText, if (content.rtl) contentWidth - LABEL_WIDTH else 0f, LABEL_NUDGE)
            text(valueText, if (content.rtl) 0f else LABEL_WIDTH + GAP, 0f)
        }
    }

    private fun warning(value: String): Block {
        val text = layout(value, warningPaint, contentWidth - 2 * WARNING_PAD)
        val box = text.height + 2 * WARNING_PAD
        return Block(box + ROW_GAP) {
            fill.style = Paint.Style.FILL
            fill.color = style.accentSoft
            drawRoundRect(0f, 0f, contentWidth, box, WARNING_RADIUS, WARNING_RADIUS, fill)
            text(text, WARNING_PAD, WARNING_PAD)
        }
    }

    private fun footer(): Block {
        val text = layout(content.footer, labelPaint, contentWidth)
        return Block(SECTION_GAP + text.height) { text(text, 0f, SECTION_GAP) }
    }

    /** A round seal with a check mark in the accent color (spec: S15). */
    @Suppress("MagicNumber") // The check mark's proportions inside the seal.
    private fun Canvas.seal(left: Float) {
        val r = SEAL_SIZE / 2
        val cx = left + r
        fill.style = Paint.Style.FILL
        fill.color = style.accentSoft
        drawCircle(cx, r, r, fill)
        fill.style = Paint.Style.STROKE
        fill.color = style.accent
        fill.strokeWidth = SEAL_STROKE
        drawCircle(cx, r, r - SEAL_INSET, fill)
        fill.strokeCap = Paint.Cap.ROUND
        fill.strokeJoin = Paint.Join.ROUND
        fill.strokeWidth = SEAL_STROKE * 1.6f
        val check = Path().apply {
            moveTo(cx - r * 0.34f, r + r * 0.02f)
            lineTo(cx - r * 0.08f, r + r * 0.26f)
            lineTo(cx + r * 0.36f, r - r * 0.22f)
        }
        drawPath(check, fill)
    }

    private fun layout(text: String, paint: TextPaint, width: Float) = staticLayout(text, paint, width, direction)

    private companion object {
        const val LINE_SPACING = 1.15f

        fun staticLayout(text: String, paint: TextPaint, width: Float, direction: TextDirectionHeuristic) =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(direction)
                .setIncludePad(false)
                .setLineSpacing(0f, LINE_SPACING)
                .build()

        fun Canvas.text(layout: StaticLayout, x: Float, y: Float) = withTranslation(x, y) { layout.draw(this) }

        /** The weight goes to variable fonts (Manrope, Bricolage); fixed-weight faces ignore it. */
        fun paint(face: Typeface, size: Float, color: Int, weight: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face
            textSize = size
            this.color = color
            fontVariationSettings = "'wght' $weight"
        }

        const val MEDIUM = 500
        const val SEMI_BOLD = 600
        const val BOLD = 700
        const val EXTRA_BOLD = 800

        const val HEADING_SIZE = 22f
        const val TITLE_SIZE = 14f
        const val OVERLINE_SIZE = 8f
        const val OVERLINE_TRACKING = 0.06f
        const val LABEL_SIZE = 8.5f
        const val BODY_SIZE = 10f
        const val CODE_SIZE = 7.5f
        const val LABEL_WIDTH = 132f
        const val LABEL_NUDGE = 1f
        const val GAP = 12f
        const val TITLE_GAP = 4f
        const val SECTION_GAP = 14f
        const val ROW_GAP = 7f
        const val RULE = 0.75f
        const val SEAL_SIZE = 44f
        const val SEAL_INSET = 4f
        const val SEAL_STROKE = 1.5f
        const val WARNING_PAD = 7f
        const val WARNING_RADIUS = 8f
    }
}
