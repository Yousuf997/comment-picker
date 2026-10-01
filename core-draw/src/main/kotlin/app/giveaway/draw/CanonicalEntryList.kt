package app.giveaway.draw

import java.util.Locale

/**
 * The entry list exactly as the draw sees it (spec: Build the entry list): usernames lowercased without locale
 * rules, sorted by Unicode code point, joined with "\n" (no trailing newline). Duplicates stay when "one entry per
 * person" is off. [hash] is SHA-256 of the UTF-8 text and is printed on the certificate.
 */
class CanonicalEntryList private constructor(val usernames: List<String>) {

    val text: String = usernames.joinToString("\n")
    val hash: ByteArray = sha256(text.toByteArray(Charsets.UTF_8))
    val hashHex: String = hash.toHex()

    val size: Int get() = usernames.size

    /** Number of different people, which bounds how many can be picked. */
    val distinctCount: Int = usernames.toSet().size

    companion object {
        fun of(validUsernames: List<String>): CanonicalEntryList =
            CanonicalEntryList(validUsernames.map { it.lowercase(Locale.ROOT) }.sortedWith(CodePointOrder))

        /** Rebuilds a list from its exported text, for verification. */
        fun fromText(text: String): CanonicalEntryList = of(if (text.isEmpty()) emptyList() else text.split("\n"))

        /**
         * Compares by Unicode code point. Kotlin's default String order compares UTF-16 units, which sorts characters
         * outside the Basic Multilingual Plane (surrogate pairs) before U+E000..U+FFFF and would disagree with
         * verifiers in other languages.
         */
        internal val CodePointOrder = Comparator<String> { a, b ->
            val left = a.codePoints().iterator()
            val right = b.codePoints().iterator()
            while (left.hasNext() && right.hasNext()) {
                val diff = left.nextInt().compareTo(right.nextInt())
                if (diff != 0) return@Comparator diff
            }
            left.hasNext().compareTo(right.hasNext())
        }
    }
}
