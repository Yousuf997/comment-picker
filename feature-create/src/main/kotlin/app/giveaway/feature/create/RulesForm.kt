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
    val winners: Int = 1,
    val alternates: Int = 2,
) {
    fun toRules() = Rules(
        minMentions = minMentions,
        requiredHashtag = hashtag.trim().takeIf { it.isNotEmpty() },
        keyword = keyword.trim().takeIf { it.isNotEmpty() },
        onePerPerson = onePerPerson,
        excludePastWinners = excludePastWinners,
        excludeBlocklist = excludeBlocklist,
        closesAt = closesAt,
        winnersCount = winners,
        alternatesCount = alternates,
    )

    companion object {
        val MENTIONS_RANGE = 0..10
        val WINNERS_RANGE = 1..50
        val ALTERNATES_RANGE = 0..20
    }
}

enum class RulesError {
    /** Entries must close in the future (spec: S7 validation). */
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

    fun validate(form: RulesForm, now: Instant): Set<RulesError> = buildSet {
        if (!form.closesAt.isAfter(now)) add(RulesError.CLOSES_IN_PAST)
        val tag = form.hashtag.trim()
        if (tag.isNotEmpty() && !hashtag.matches(tag)) add(RulesError.HASHTAG_FORMAT)
        if (form.winners + form.alternates < 1) add(RulesError.NOBODY_TO_PICK)
        val inRange = form.minMentions in RulesForm.MENTIONS_RANGE &&
            form.winners in RulesForm.WINNERS_RANGE &&
            form.alternates in RulesForm.ALTERNATES_RANGE
        if (!inRange) add(RulesError.OUT_OF_RANGE)
    }
}
