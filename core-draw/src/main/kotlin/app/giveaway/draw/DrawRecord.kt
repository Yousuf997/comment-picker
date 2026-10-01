package app.giveaway.draw

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What the device signs for a real draw (plan M-06): everything the certificate states, in one canonical form.
 * Anyone with the certificate's values can rebuild these bytes and check the signature with the printed public key.
 *
 * Canonical form: JSON, keys in alphabetical order, no whitespace, strings escaped per RFC 8259 with non-ASCII
 * characters kept as UTF-8, times as ISO-8601 UTC with millisecond precision, encoded as UTF-8.
 */
data class DrawRecord(
    val algorithmVersion: String,
    val account: String,
    val postId: String,
    val title: String,
    val entriesClosedAt: Instant,
    val commitHash: String,
    val seedHex: String,
    val entryListHash: String,
    val entryCount: Int,
    val winnersRequested: Int,
    val alternatesRequested: Int,
    val picks: List<Pick>,
    val drawnAt: Instant,
    /** FOUND, NOT_FOUND or NOT_CHECKED (plan A7). */
    val captionCheck: String,
    val integrityVerified: Boolean,
    val partialImport: Boolean,
    /** Manual exclusions with the organizer's reason, in comment order (spec: listed on the certificate). */
    val manualExclusions: List<ManualExclusion>,
) {
    data class ManualExclusion(val username: String, val reason: String)

    fun canonicalJson(): String = obj(
        "account" to str(account),
        "algorithmVersion" to str(algorithmVersion),
        "alternatesRequested" to alternatesRequested.toString(),
        "captionCheck" to str(captionCheck),
        "commitHash" to str(commitHash),
        "drawnAt" to str(time(drawnAt)),
        "entriesClosedAt" to str(time(entriesClosedAt)),
        "entryCount" to entryCount.toString(),
        "entryListHash" to str(entryListHash),
        "integrityVerified" to integrityVerified.toString(),
        "manualExclusions" to arr(
            manualExclusions.map { obj("reason" to str(it.reason), "username" to str(it.username)) },
        ),
        "partialImport" to partialImport.toString(),
        "picks" to arr(
            picks.map {
                obj("position" to it.position.toString(), "role" to str(it.role.name), "username" to str(it.username))
            },
        ),
        "postId" to str(postId),
        "seedHex" to str(seedHex),
        "title" to str(title),
        "winnersRequested" to winnersRequested.toString(),
    )

    fun canonicalBytes(): ByteArray = canonicalJson().toByteArray(Charsets.UTF_8)

    private companion object {
        private val ISO_MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC)
        private const val FIRST_PRINTABLE = 0x20

        fun time(instant: Instant): String = ISO_MILLIS.format(instant)

        fun obj(vararg members: Pair<String, String>): String =
            members.joinToString(",", "{", "}") { (key, value) -> "${str(key)}:$value" }

        fun arr(items: List<String>): String = items.joinToString(",", "[", "]")

        fun str(value: String): String = buildString {
            append('"')
            for (c in value) {
                when {
                    c == '"' -> append("\\\"")
                    c == '\\' -> append("\\\\")
                    c == '\n' -> append("\\n")
                    c == '\r' -> append("\\r")
                    c == '\t' -> append("\\t")
                    c.code < FIRST_PRINTABLE -> append(String.format(Locale.ROOT, "\\u%04x", c.code))
                    else -> append(c)
                }
            }
            append('"')
        }
    }
}
