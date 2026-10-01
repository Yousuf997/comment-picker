package app.giveaway.core.media

import android.graphics.Typeface
import app.giveaway.draw.DrawRecord

/** A real draw's certificate facts (plan M-07): the signed record, its signature, and replacements made on S14. */
data class CertificateData(
    val record: DrawRecord,
    /** The DER signature over the record's canonical bytes, lowercase hex. */
    val signatureHex: String,
    /** The signing key's X.509 SubjectPublicKeyInfo, lowercase hex. */
    val publicKeyHex: String,
    val fingerprint: String,
    val replacements: List<Replacement>,
) {
    /** A winner replaced from the alternates, with the organizer's reason (spec: S14). */
    data class Replacement(val position: Int, val replacedByPosition: Int, val reason: String)
}

/**
 * What a certificate says, already in the app's language with handles and hashes isolated left to right. The
 * renderer only lays it out, so the same words go into the PDF and the Story image.
 */
data class CertificateContent(
    val heading: String,
    val title: String,
    val sections: List<Section>,
    val footer: String,
    val rtl: Boolean,
) {
    data class Section(val heading: String, val rows: List<Row>)

    /** A labelled value, or a full-width line when [label] is null. */
    data class Row(val label: String?, val value: String, val kind: Kind = Kind.TEXT)

    enum class Kind { TEXT, STRONG, CODE, WARNING }

    /** Every word the certificate prints, in order. */
    fun text(): String = buildList {
        add(heading)
        add(title)
        sections.forEach { section ->
            add(section.heading)
            section.rows.forEach { row -> addAll(listOfNotNull(row.label, row.value)) }
        }
        add(footer)
    }.joinToString("\n")
}

/** Paper, ink and faces for certificates, taken from the light theme by the caller (spec: Design system). */
data class CertificateStyle(
    val paper: Int,
    val text: Int,
    val muted: Int,
    val outline: Int,
    val accent: Int,
    val accentSoft: Int,
    val accentOnSoft: Int,
    val display: Typeface,
    val body: Typeface,
    val bold: Typeface,
    val code: Typeface,
)
