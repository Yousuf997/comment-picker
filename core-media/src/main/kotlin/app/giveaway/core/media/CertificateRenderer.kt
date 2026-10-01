package app.giveaway.core.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import java.io.OutputStream

/**
 * Draws certificates (plan M-07): an A4 PDF that runs onto more pages when the lists are long, and a 1080 x 1920 PNG
 * for Stories (spec: S15). Both use the same layout, in points; the Story image lays out on a narrower page scaled up
 * to fill the frame, and shows only what fits there, so the caller keeps its content short.
 */
class CertificateRenderer(private val style: CertificateStyle) {
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.style = Paint.Style.STROKE
        strokeWidth = BORDER
    }

    fun pageCount(content: CertificateContent): Int = a4Pages(content).size

    /** Draws A4 page [index] in points; the PDF and the golden tests share this drawing. */
    fun drawPage(canvas: Canvas, content: CertificateContent, index: Int) =
        draw(canvas, a4Pages(content)[index], A4_WIDTH.toFloat(), A4_HEIGHT.toFloat())

    fun writePdf(content: CertificateContent, out: OutputStream) {
        val pages = a4Pages(content)
        val document = PdfDocument()
        try {
            pages.forEachIndexed { i, blocks ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, i + 1).create())
                draw(page.canvas, blocks, A4_WIDTH.toFloat(), A4_HEIGHT.toFloat())
                document.finishPage(page)
            }
            document.writeTo(out)
        } finally {
            document.close()
        }
    }

    /** The 1080 x 1920 Story image. */
    fun story(content: CertificateContent): Bitmap {
        val bitmap = createBitmap(STORY_WIDTH, STORY_HEIGHT)
        val scale = STORY_WIDTH / STORY_POINTS
        val height = STORY_HEIGHT / scale
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        val first = CertificateLayout(style, content, STORY_POINTS - 2 * MARGIN).pages(height - 2 * MARGIN).first()
        draw(canvas, first, STORY_POINTS, height)
        return bitmap
    }

    fun writePng(content: CertificateContent, out: OutputStream) {
        val bitmap = story(content)
        try {
            bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
        } finally {
            bitmap.recycle()
        }
    }

    private fun a4Pages(content: CertificateContent) =
        CertificateLayout(style, content, A4_WIDTH - 2 * MARGIN).pages(A4_HEIGHT - 2 * MARGIN)

    private fun draw(canvas: Canvas, blocks: List<Block>, width: Float, height: Float) {
        canvas.drawColor(style.paper)
        border.color = style.outline
        canvas.drawRoundRect(FRAME, FRAME, width - FRAME, height - FRAME, RADIUS, RADIUS, border)
        var y = MARGIN
        blocks.forEach { block ->
            canvas.withTranslation(MARGIN, y) { block.draw(this) }
            y += block.height
        }
    }

    companion object {
        /** A4 in PostScript points, the unit PdfDocument pages use. */
        const val A4_WIDTH = 595
        const val A4_HEIGHT = 842
        const val STORY_WIDTH = 1080
        const val STORY_HEIGHT = 1920
        private const val MARGIN = 48f

        /** The Story's width in points: narrower than A4, so its text is larger on the 1080-pixel frame. */
        private const val STORY_POINTS = 470f
        private const val FRAME = 22f
        private const val RADIUS = 18f
        private const val BORDER = 1f
        private const val PNG_QUALITY = 100
    }
}
