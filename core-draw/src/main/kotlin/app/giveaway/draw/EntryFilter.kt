package app.giveaway.draw

import java.time.Instant
import java.util.Locale

/** A top-level comment as imported from Instagram. Replies are never entries. */
data class RawComment(val id: String, val username: String, val text: String, val timestamp: Instant)

/** What makes a valid entry (spec: S7). Frozen once the giveaway is committed (plan A9). */
data class Rules(
    val minMentions: Int,
    /** With or without the leading '#'. Null or blank means no hashtag is required. */
    val requiredHashtag: String?,
    /** Null or blank means no keyword is required. */
    val keyword: String?,
    val onePerPerson: Boolean,
    val excludePastWinners: Boolean,
    val excludeBlocklist: Boolean,
    val closesAt: Instant,
    val winnersCount: Int,
    val alternatesCount: Int,
)

/** Who the giveaway belongs to and the organizer's lists and choices. Usernames compare case-insensitively. */
data class FilterContext(
    val ownerUsername: String,
    val blocklist: Set<String> = emptySet(),
    val pastWinners: Set<String> = emptySet(),
    /** Comment ID -> the organizer's reason. Printed on the certificate. */
    val manualExclusions: Map<String, String> = emptyMap(),
    /** Comment IDs the organizer accepted despite failing a content check (see [EntryFilter]). */
    val manualInclusions: Set<String> = emptySet(),
)

data class EntryDecision(
    val commentId: String,
    val username: String,
    val isValid: Boolean,
    val reason: ExclusionReason?,
    val manualNote: String?,
)

/** Turns imported comments into entry decisions (spec: Build the entry list). */
fun interface EntryFilter {
    /** [comments] must be in (timestamp, id) ascending order: the earliest valid comment is a person's entry. */
    fun evaluate(comments: Sequence<RawComment>, rules: Rules, context: FilterContext): Sequence<EntryDecision>
}

/**
 * The rules from S7, applied in the order of [ExclusionReason] (plan A10); the first failing check is the reason shown.
 *
 * - Mentions (A11): distinct `@handles` (Instagram's characters: letters, digits, '.', '_'; up to 30), not counting
 *   the commenter or the giveaway account, and not inside e-mail addresses.
 * - Hashtag: a whole hashtag token, ignoring case. Keyword: a substring, ignoring case.
 * - Late: posted strictly after `closesAt`, by Instagram's timestamp.
 * - One entry per person: a person's first comment that passes every other check is their entry; later ones are
 *   DUPLICATE. A manually excluded comment doesn't use up the person's entry; the blocklist excludes a person.
 * - Manual inclusion (plan A26) can override only the content checks (mentions, hashtag, keyword), never a late,
 *   own-account, blocklisted, past-winner or duplicate comment, so it can't be used to slip someone in.
 */
object RulesFilterV1 : EntryFilter {

    override fun evaluate(
        comments: Sequence<RawComment>,
        rules: Rules,
        context: FilterContext,
    ): Sequence<EntryDecision> {
        val checks = Checks(rules, context)
        val entered = HashSet<String>()
        return comments.map { comment ->
            val user = comment.username.normalized()
            val manualNote = context.manualExclusions[comment.id]
            val reason = checks.automaticReason(comment, user)
                ?: manualNote?.let { ExclusionReason.MANUAL }
                ?: ExclusionReason.DUPLICATE.takeIf { rules.onePerPerson && !entered.add(user) }
            EntryDecision(
                commentId = comment.id,
                username = comment.username,
                isValid = reason == null,
                reason = reason,
                manualNote = manualNote.takeIf { reason == ExclusionReason.MANUAL },
            )
        }
    }

    /** Usernames of the valid entries, ready for [CanonicalEntryList.of]. */
    fun validUsernames(decisions: Sequence<EntryDecision>): List<String> =
        decisions.filter { it.isValid }.map { it.username }.toList()

    /** The rules prepared once per run: normalized lists and compiled patterns. */
    private class Checks(private val rules: Rules, private val context: FilterContext) {
        private val owner = context.ownerUsername.normalized()
        private val blocklist = context.blocklist.mapTo(HashSet()) { it.normalized() }
        private val pastWinners = context.pastWinners.mapTo(HashSet()) { it.normalized() }
        private val hashtag = rules.requiredHashtag?.trim()?.removePrefix("#")
            ?.takeIf { it.isNotEmpty() }
            ?.let(::hashtagPattern)
        private val keyword = rules.keyword?.trim()?.takeIf { it.isNotEmpty() }

        /** The first failing rule in [ExclusionReason] order, after manual inclusions; null if none fails. */
        fun automaticReason(comment: RawComment, user: String): ExclusionReason? {
            val reason = accountReason(comment, user) ?: contentReason(comment.text, user)
            return reason.takeUnless { it in CONTENT_REASONS && comment.id in context.manualInclusions }
        }

        private fun accountReason(comment: RawComment, user: String) = when {
            comment.timestamp.isAfter(rules.closesAt) -> ExclusionReason.AFTER_DEADLINE
            user == owner -> ExclusionReason.OWN_ACCOUNT
            rules.excludeBlocklist && user in blocklist -> ExclusionReason.BLOCKLISTED
            rules.excludePastWinners && user in pastWinners -> ExclusionReason.PAST_WINNER
            else -> null
        }

        private fun contentReason(text: String, user: String) = when {
            mentionCount(text, user) < rules.minMentions -> ExclusionReason.TOO_FEW_MENTIONS
            hashtag != null && !hashtag.containsMatchIn(text) -> ExclusionReason.MISSING_HASHTAG
            keyword != null && !text.contains(keyword, ignoreCase = true) -> ExclusionReason.MISSING_KEYWORD
            else -> null
        }

        private fun mentionCount(text: String, commenter: String): Int =
            MENTION.findAll(text)
                .map { it.groupValues[1].trimEnd('.').normalized() }
                .filter { it.isNotEmpty() && it != commenter && it != owner }
                .toSet()
                .size
    }

    private val MENTION = Regex("""(?<![A-Za-z0-9._])@([A-Za-z0-9._]{1,30})""")

    /** Reasons a manual inclusion may override (plan A26). */
    private val CONTENT_REASONS = setOf(
        ExclusionReason.TOO_FEW_MENTIONS,
        ExclusionReason.MISSING_HASHTAG,
        ExclusionReason.MISSING_KEYWORD,
    )

    private fun hashtagPattern(tag: String) =
        Regex("""(?<![\p{L}\p{N}_])#${Regex.escape(tag)}(?![\p{L}\p{N}_])""", RegexOption.IGNORE_CASE)

    private fun String.normalized() = trim().removePrefix("@").lowercase(Locale.ROOT)
}
