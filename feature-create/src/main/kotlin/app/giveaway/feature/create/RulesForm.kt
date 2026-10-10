package app.giveaway.feature.create

import app.giveaway.draw.Rules
import java.time.Instant

/** What S7 edits (spec: S7). Defaults are the common case: one mention, one winner, two alternates (plan A27). */
data class RulesForm(
    val minMentions: Int = 1,
    val hashtag: String = "",
    val keyword: String = "",
    val onePerPerson: Boolean = true,
    val excludePastWinners: Boolean = true,
    val excludeBlocklist: Boolean = true,
    val closesAt: Instant,
    /** Entries close the moment the rules are saved, for an instant draw; [closesAt] is then unused. */
    val closesNow: Boolean = false,
    val winners: Int = 1,
    val alternates: Int = 2,
) {
    /** [now] is when the rules are saved: the closing time when entries close "Now". */
    fun toRules(now: Instant) = Rules(
        minMentions = minMentions,
        requiredHashtag = hashtag.trim().takeIf { it.isNotEmpty() },
        keyword = keyword.trim().takeIf { it.isNotEmpty() },
        onePerPerson = onePerPerson,
        excludePastWinners = excludePastWinners,
        excludeBlocklist = excludeBlocklist,
        closesAt = if (closesNow) now else closesAt,
        winnersCount = winners,
        alternatesCount = alternates,
    )

    companion object {
        /** The form for a giveaway's saved rules, for editing them (plan A31). */
        fun from(rules: Rules) = RulesForm(
            minMentions = rules.minMentions,
            hashtag = rules.requiredHashtag.orEmpty(),
            keyword = rules.keyword.orEmpty(),
            onePerPerson = rules.onePerPerson,
            excludePastWinners = rules.excludePastWinners,
            excludeBlocklist = rules.excludeBlocklist,
            closesAt = rules.closesAt,
            winners = rules.winnersCount,
            alternates = rules.alternatesCount,
        )

        val MENTIONS_RANGE = 0..10
        val WINNERS_RANGE = 1..50
        val ALTERNATES_RANGE = 0..20
    }
}

enum class RulesError {
    /** Entries must close in the future, unless they close "Now" (spec: S7 validation). */
    CLOSES_IN_PAST,

    /** A hashtag starts with # and is one word (spec: S7 validation). */
    HASHTAG_FORMAT,

    /** At least one person must be picked: winners + alternates ≥ 1 (spec: S7 validation). */
    NOBODY_TO_PICK,

    /** A stepper value outside its range, e.g. from restored state. */
    OUT_OF_RANGE,
}

object RulesValidation {
    /** Letters (any script), digits and underscores, as Instagram hashtags allow. */
    private val hashtag = Regex("^#[\\p{L}\\p{N}_]+$")

    /**
     * [requireFutureClose] is off when editing a giveaway whose entries already closed, or when the closing time is
     * unchanged: only a new deadline for open entries has to be in the future.
     */
    fun validate(form: RulesForm, now: Instant, requireFutureClose: Boolean = true): Set<RulesError> = buildSet {
        if (requireFutureClose && !form.closesNow && !form.closesAt.isAfter(now)) add(RulesError.CLOSES_IN_PAST)
        val tag = form.hashtag.trim()
        if (tag.isNotEmpty() && !hashtag.matches(tag)) add(RulesError.HASHTAG_FORMAT)
        if (form.winners + form.alternates < 1) add(RulesError.NOBODY_TO_PICK)
        val inRange = form.minMentions in RulesForm.MENTIONS_RANGE &&
            form.winners in RulesForm.WINNERS_RANGE &&
            form.alternates in RulesForm.ALTERNATES_RANGE
        if (!inRange) add(RulesError.OUT_OF_RANGE)
    }
}
