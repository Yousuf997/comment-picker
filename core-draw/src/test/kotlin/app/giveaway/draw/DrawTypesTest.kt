package app.giveaway.draw

import org.junit.Assert.assertEquals
import org.junit.Test

class DrawTypesTest {

    /** The filter reports the first matching reason in this order (A10); reordering changes certificates. */
    @Test
    fun exclusionReasonPrecedenceIsStable() {
        assertEquals(
            listOf(
                "AFTER_DEADLINE",
                "OWN_ACCOUNT",
                "BLOCKLISTED",
                "PAST_WINNER",
                "TOO_FEW_MENTIONS",
                "MISSING_HASHTAG",
                "MISSING_KEYWORD",
                "DUPLICATE",
                "MANUAL",
            ),
            ExclusionReason.entries.map { it.name },
        )
    }

    @Test
    fun winnersComeBeforeAlternates() {
        assertEquals(listOf(Role.WINNER, Role.ALTERNATE), Role.entries)
    }

    @Test
    fun algorithmVersionIsV1() {
        assertEquals("v1", ALGORITHM_V1)
    }
}
