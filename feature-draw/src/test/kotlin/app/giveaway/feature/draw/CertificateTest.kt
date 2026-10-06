package app.giveaway.feature.draw

import android.app.Application
import android.graphics.Canvas
import androidx.core.graphics.createBitmap
import androidx.test.core.app.ApplicationProvider
import app.giveaway.core.media.CertificateContent
import app.giveaway.core.media.CertificateData
import app.giveaway.core.media.CertificateRenderer
import app.giveaway.draw.DrawRecord
import app.giveaway.draw.Pick
import app.giveaway.draw.Role
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneOffset

/** M-07 acceptance: the certificate states every proof value and warning, in English and Arabic. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CertificateTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val commitHash = "3f9a" + "0".repeat(56) + "b7c1"
    private val seedHex = "a1b2c3d4".repeat(8)
    private val listHash = "e5f6".repeat(16)
    private val fingerprint = "9c".repeat(32)

    private val record = DrawRecord(
        algorithmVersion = "v1",
        account = "tote.shop",
        postId = "17890012345",
        title = "Win a tote bag!",
        entriesClosedAt = Instant.parse("2026-10-08T10:00:00Z"),
        commitHash = commitHash,
        seedHex = seedHex,
        entryListHash = listHash,
        entryCount = 1_204,
        winnersRequested = 2,
        alternatesRequested = 2,
        picks = listOf(
            Pick(1, "maya.k", Role.WINNER),
            Pick(2, "sam_r", Role.WINNER),
            Pick(3, "lina.art", Role.ALTERNATE),
            Pick(4, "omar99", Role.ALTERNATE),
        ),
        drawnAt = Instant.parse("2026-10-08T12:30:15.250Z"),
        captionCheck = "NOT_CHECKED",
        integrityVerified = true,
        partialImport = false,
        manualExclusions = emptyList(),
    )
    private val clean = CertificateData(record, "30".repeat(36), "3059".repeat(23), fingerprint, emptyList())
    private val flagged = clean.copy(
        record = record.copy(
            integrityVerified = false,
            partialImport = true,
            manualExclusions = listOf(DrawRecord.ManualExclusion("spam.bot", "Fake account")),
        ),
        replacements = listOf(CertificateData.Replacement("sam_r", "lina.art", "Didn't follow")),
    )

    private fun writer() = CertificateWriter(app, "https://verify.example/v1", ZoneOffset.UTC)

    private fun renderer() = CertificateRenderer(CertificateWriter.style(app, arabic = isArabic()))

    private fun isArabic() = app.resources.configuration.locales[0].language == "ar"

    @Test
    fun theCertificateStatesEveryProofValue() {
        val text = writer().pdf(clean).text()
        listOf(
            seedHex, listHash, "v1", fingerprint, "30".repeat(36), "3059".repeat(23),
            "@tote.shop", "17890012345", "1,204", "2026-10-08T10:00:00Z", "2026-10-08T12:30:15Z",
            "@maya.k", "@sam_r", "@lina.art", "@omar99", "https://verify.example/v1",
        ).forEach { assertTrue("prints $it", text.contains(it)) }
        assertFalse("no notes on a clean draw", text.contains(app.getString(R.string.certificate_section_notes)))
        // No draw code anywhere (plan A35); the seed's hash stays inside the signed record.
        assertFalse(text.contains("#draw"))
        assertFalse(text.contains(commitHash))
    }

    @Test
    fun everyWarningAndReplacementIsListed() {
        val text = writer().pdf(flagged).text()
        listOf(
            app.getString(R.string.certificate_partial),
            app.getString(R.string.certificate_integrity),
            "@spam.bot", "Fake account", "@lina.art", "Didn't follow",
        ).forEach { assertTrue("prints $it", text.contains(it)) }
    }

    @Test
    fun theStoryShowsTheFirstPicksAndTheProofWithoutTheKeys() {
        val many = clean.copy(
            record = record.copy(
                winnersRequested = 10,
                alternatesRequested = 2,
                picks = (1..12).map { Pick(it, "user$it", if (it <= 10) Role.WINNER else Role.ALTERNATE) },
            ),
        )
        val text = writer().story(many).text()
        assertTrue(text.contains("@user8"))
        assertFalse(text.contains("@user9"))
        assertTrue(text.contains("and 4 more picks"))
        assertTrue(text.contains(seedHex))
        assertFalse(text.contains("3059".repeat(23)))
    }

    @Test
    fun longListsRunOntoMorePages() {
        val big = clean.copy(
            record = record.copy(
                winnersRequested = 50,
                alternatesRequested = 20,
                picks = (1..70).map { Pick(it, "entrant_$it", if (it <= 50) Role.WINNER else Role.ALTERNATE) },
            ),
        )
        assertTrue(renderer().pageCount(writer().pdf(big)) > 1)
        assertEquals(1, renderer().pageCount(writer().pdf(clean)))
    }

    @Test
    @Config(qualifiers = "ar")
    fun arabicMirrorsButKeepsCodesLeftToRight() {
        val content = writer().pdf(flagged)
        assertTrue(content.rtl)
        assertEquals("شهادة سحب عشوائي", content.heading)
        val code = content.sections.flatMap { it.rows }.first { it.kind == CertificateContent.Kind.CODE }.value
        assertEquals("\u2066$seedHex\u2069", code)
    }

    @Test
    fun goldenPdfPage() = pdfGolden("certificate_pdf_en")

    @Test
    @Config(qualifiers = "ar")
    fun goldenPdfPageArabic() = pdfGolden("certificate_pdf_ar")

    @Test
    fun goldenStory() = storyGolden("certificate_story_en")

    @Test
    @Config(qualifiers = "ar")
    fun goldenStoryArabic() = storyGolden("certificate_story_ar")

    private fun pdfGolden(name: String) {
        val bitmap = createBitmap(CertificateRenderer.A4_WIDTH * 2, CertificateRenderer.A4_HEIGHT * 2)
        val canvas = Canvas(bitmap).apply { scale(2f, 2f) }
        renderer().drawPage(canvas, writer().pdf(flagged), 0)
        bitmap.captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun storyGolden(name: String) {
        renderer().story(writer().story(flagged)).captureRoboImage("src/test/screenshots/$name.png")
    }
}
