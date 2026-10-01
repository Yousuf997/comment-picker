package app.giveaway.draw

/**
 * Why a comment is not a valid entry. When several apply, the first in declaration order wins
 * (build plan assumption A10), so the reason shown is stable.
 */
enum class ExclusionReason {
    /** Posted after entries closed (by Instagram's timestamp). */
    AFTER_DEADLINE,

    /** Posted by the giveaway account itself. */
    OWN_ACCOUNT,
    BLOCKLISTED,
    PAST_WINNER,
    TOO_FEW_MENTIONS,
    MISSING_HASHTAG,
    MISSING_KEYWORD,

    /** A later comment by someone who already has a valid entry, when "one entry per person" is on. */
    DUPLICATE,

    /** Excluded by hand before the real draw, with a reason printed on the certificate. */
    MANUAL,
}

/** A picked username's role in the draw result. */
enum class Role { WINNER, ALTERNATE }
