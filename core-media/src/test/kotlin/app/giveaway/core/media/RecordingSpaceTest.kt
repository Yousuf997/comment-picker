package app.giveaway.core.media

import app.giveaway.draw.Pick
import app.giveaway.draw.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** M-03: the space check before the draw. */
class RecordingSpaceTest {

    @Test
    fun theEstimatedDurationMatchesTheTimeline() {
        val picks = List(3) { Pick(it + 1, "w$it", Role.WINNER) } +
            List(2) { Pick(it + 4, "a$it", Role.ALTERNATE) }
        val timeline = DrawTimeline(picks, List(20) { "u$it" }, reducedMotion = false)
        assertEquals(timeline.durationMs, DrawTimeline.estimatedDurationMs(winners = 3, alternates = 2))
    }

    @Test
    fun shortStorageIsCaught() {
        val needed = RecordingSpace.neededBytes(winners = 3, alternates = 2)
        assertTrue(RecordingSpace.enough({ needed }, 3, 2))
        assertFalse(RecordingSpace.enough({ needed - 1 }, 3, 2))
    }
}
