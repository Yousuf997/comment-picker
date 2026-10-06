package app.giveaway.feature.draw

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.View
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import app.giveaway.core.designsystem.LightGiveawayColors
import app.giveaway.core.designsystem.firstStrongIsolated
import app.giveaway.core.designsystem.handle
import app.giveaway.core.designsystem.ltrIsolated
import app.giveaway.core.media.CertificateContent
import app.giveaway.core.media.CertificateContent.Kind
import app.giveaway.core.media.CertificateContent.Row
import app.giveaway.core.media.CertificateContent.Section
import app.giveaway.core.media.CertificateData
import app.giveaway.core.media.CertificateStyle
import app.giveaway.draw.Pick
import app.giveaway.draw.Role
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import app.giveaway.core.designsystem.R as DesignR

/**
 * Writes what a certificate says, in the app's language (spec: S15, Localization). Dates show the local form next to
 * ISO 8601; handles, hashes and codes stay left to right. Every warning in plan A16 becomes a note.
 */
internal class CertificateWriter(
    private val context: Context,
    private val verifierUrl: String,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val locale = context.resources.configuration.locales[0]
    private val numbers = NumberFormat.getIntegerInstance(locale)
    private val rtl = TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL
    private val localTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale)
        .withDecimalStyle(DecimalStyle.of(locale))
        .withZone(zone)

    /** The full certificate for the PDF: every pick, the signature and the public key. */
    fun pdf(data: CertificateData) = content(data, listOf(giveaway(data), result(data, data.record.picks.size)), true)

    /** The Story image: the first picks and the proof, without the long signature (spec: S15). */
    fun story(data: CertificateData) = content(data, listOf(result(data, STORY_PICKS)), false)

    private fun content(data: CertificateData, first: List<Section>, full: Boolean) = CertificateContent(
        heading = s(R.string.certificate_heading),
        title = data.record.title.firstStrongIsolated(),
        sections = first + proof(data, full) + listOfNotNull(notes(data)),
        footer = context.getString(R.string.certificate_rerun, verifierUrl.ltrIsolated()),
        rtl = rtl,
    )

    private fun giveaway(data: CertificateData) = Section(
        s(R.string.certificate_section_giveaway),
        listOf(
            Row(s(R.string.certificate_account), handle(data.record.account)),
            Row(s(R.string.certificate_post), data.record.postId.ltrIsolated()),
            Row(s(R.string.certificate_entries_closed), time(data.record.entriesClosedAt)),
            Row(s(R.string.certificate_valid_entries), numbers.format(data.record.entryCount)),
        ),
    )

    private fun result(data: CertificateData, limit: Int): Section {
        val picks = data.record.picks
        val rows = picks.take(limit).map { Row(place(it, data.record.winnersRequested), pick(it, data), Kind.STRONG) }
        val more = picks.size - limit
        val tail = if (more > 0) {
            val text = context.resources.getQuantityString(R.plurals.certificate_more, more, numbers.format(more))
            listOf(Row(null, text))
        } else {
            emptyList()
        }
        return Section(s(R.string.certificate_section_result), rows + tail)
    }

    private fun place(pick: Pick, winners: Int) = if (pick.role == Role.WINNER) {
        context.getString(R.string.certificate_winner, numbers.format(pick.position))
    } else {
        context.getString(R.string.certificate_alternate, numbers.format(pick.position - winners))
    }

    /** The handle, and for a replaced winner who took the place and why (spec: S14). */
    private fun pick(pick: Pick, data: CertificateData): String {
        val replacement = data.replacements.firstOrNull { it.username == pick.username } ?: return handle(pick.username)
        val reason = replacement.reason.firstStrongIsolated()
        val note = context.getString(R.string.certificate_replaced, handle(replacement.replacedBy), reason)
        return handle(pick.username) + "\n" + note
    }

    private fun proof(data: CertificateData, full: Boolean): Section {
        val record = data.record
        val rows = listOf(
            Row(s(R.string.certificate_seed), record.seedHex.ltrIsolated(), Kind.CODE),
            Row(s(R.string.certificate_list_hash), record.entryListHash.ltrIsolated(), Kind.CODE),
            Row(s(R.string.certificate_algorithm), record.algorithmVersion.ltrIsolated()),
            Row(s(R.string.certificate_drawn_at), time(record.drawnAt)),
            Row(s(R.string.certificate_signed), data.fingerprint.ltrIsolated(), Kind.CODE),
        )
        val keys = listOf(
            Row(s(R.string.certificate_signature), data.signatureHex.ltrIsolated(), Kind.CODE),
            Row(s(R.string.certificate_public_key), data.publicKeyHex.ltrIsolated(), Kind.CODE),
        )
        return Section(s(R.string.certificate_section_proof), if (full) rows + keys else rows)
    }

    /** The warning lines of plan A16, or null when there are none. */
    private fun notes(data: CertificateData): Section? {
        val record = data.record
        val lines = buildList {
            if (record.partialImport) add(s(R.string.certificate_partial))
            if (!record.integrityVerified) add(s(R.string.certificate_integrity))
            record.manualExclusions.forEach {
                val reason = it.reason.firstStrongIsolated()
                add(context.getString(R.string.certificate_manual, handle(it.username), reason))
            }
        }
        if (lines.isEmpty()) return null
        return Section(s(R.string.certificate_section_notes), lines.map { Row(null, it, Kind.WARNING) })
    }

    /** The local date and time, with ISO 8601 in UTC beneath it (spec: Localization). */
    private fun time(instant: Instant): String =
        localTime.format(instant) + "\n" + instant.truncatedTo(ChronoUnit.SECONDS).toString().ltrIsolated()

    private fun s(id: Int) = context.getString(id)

    companion object {
        /** Picks that fit on the Story image; the rest are counted. */
        const val STORY_PICKS = 8

        /** Certificates are paper: always the light colors, whatever the phone's theme. */
        fun style(context: Context, arabic: Boolean): CertificateStyle {
            val colors = LightGiveawayColors
            fun font(id: Int, fallback: Typeface) =
                runCatching { ResourcesCompat.getFont(context, id) }.getOrNull() ?: fallback
            return CertificateStyle(
                paper = colors.surface.toArgb(),
                text = colors.onBackground.toArgb(),
                muted = colors.onMuted.toArgb(),
                outline = colors.outline.toArgb(),
                accent = colors.accent.toArgb(),
                accentSoft = colors.accentSoft.toArgb(),
                accentOnSoft = colors.accentOnSoft.toArgb(),
                display = font(
                    if (arabic) DesignR.font.ibm_plex_sans_arabic_bold else DesignR.font.bricolage_grotesque,
                    Typeface.DEFAULT_BOLD,
                ),
                body = font(
                    if (arabic) DesignR.font.ibm_plex_sans_arabic_medium else DesignR.font.manrope,
                    Typeface.DEFAULT,
                ),
                bold = font(
                    if (arabic) DesignR.font.ibm_plex_sans_arabic_bold else DesignR.font.manrope,
                    Typeface.DEFAULT_BOLD,
                ),
                code = font(DesignR.font.jetbrains_mono, Typeface.MONOSPACE),
            )
        }
    }
}
