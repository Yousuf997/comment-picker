package app.giveaway.core.media

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.giveaway.core.media.CertificateContent.Kind
import app.giveaway.core.media.CertificateContent.Row
import app.giveaway.core.media.CertificateContent.Section
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** M-07 on a device: PdfDocument writes A4 pages that PdfRenderer opens, and the Story PNG is 1080 x 1920. */
@RunWith(AndroidJUnit4::class)
class CertificateRendererTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val style = CertificateStyle(
        paper = Color.WHITE,
        text = Color.BLACK,
        muted = Color.GRAY,
        outline = Color.LTGRAY,
        accent = Color.RED,
        accentSoft = Color.YELLOW,
        accentOnSoft = Color.BLUE,
        display = Typeface.DEFAULT_BOLD,
        body = Typeface.DEFAULT,
        bold = Typeface.DEFAULT_BOLD,
        code = Typeface.MONOSPACE,
    )

    private fun content(picks: Int) = CertificateContent(
        heading = "Certificate of random draw",
        title = "Win a tote bag!",
        sections = listOf(
            Section("Result", (1..picks).map { Row("Winner $it", "@user$it", Kind.STRONG) }),
            Section("Proof", listOf(Row("Revealed seed", "ab".repeat(32), Kind.CODE))),
            Section("Notes", listOf(Row(null, "Device integrity not verified.", Kind.WARNING))),
        ),
        footer = "Anyone can re-run this draw.",
        rtl = false,
    )

    @Test
    fun pdfHasA4PagesAndGrowsWithTheList() {
        val renderer = CertificateRenderer(style)
        listOf(3 to 1, 70 to renderer.pageCount(content(70))).forEach { (picks, pages) ->
            val file = File(context.cacheDir, "certificate-$picks.pdf")
            file.outputStream().use { renderer.writePdf(content(picks), it) }
            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
                assertEquals(pages, pdf.pageCount)
                pdf.openPage(0).use { page ->
                    assertEquals(CertificateRenderer.A4_WIDTH, page.width)
                    assertEquals(CertificateRenderer.A4_HEIGHT, page.height)
                }
            }
            file.delete()
        }
    }

    @Test
    fun storyIsAFullHdPortraitPng() {
        val file = File(context.cacheDir, "certificate.png")
        file.outputStream().use { CertificateRenderer(style).writePng(content(3), it) }
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        assertEquals(CertificateRenderer.STORY_WIDTH, options.outWidth)
        assertEquals(CertificateRenderer.STORY_HEIGHT, options.outHeight)
        assertEquals("image/png", options.outMimeType)
        file.delete()
    }
}
