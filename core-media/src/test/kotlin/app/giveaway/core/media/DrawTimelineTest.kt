package app.giveaway.core.media

import app.giveaway.draw.Pick
import app.giveaway.draw.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawTimelineTest {

    private val picks = listOf(
        Pick(1, "amy", Role.WINNER),
        Pick(2, "bob", Role.WINNER),
        Pick(3, "cat", Role.ALTERNATE),
    )
    private val entrants = listOf("amy", "bob", "cat", "dan", "eve", "fay")

    @Test
    fun eachPickLandsOnItsPersonInOrder() {
        val timeline = DrawTimeline(picks, entrants, reducedMotion = false)
        timeline.segments.forEach { segment ->
            assertEquals(segment.pick.username, segment.reel.last())
            val atLanding = timeline.frameAt(segment.landMs - 1)
            assertEquals(segment, atLanding.current)
            assertTrue("almost settled", atLanding.reelPosition > segment.reel.size - 2)
        }
        val end = timeline.frameAt(timeline.durationMs)
        assertNull(end.current)
        assertEquals(picks, end.landed)
        assertTrue(end.finished)
    }

    @Test
    fun winnersSpinLongerThanAlternatesAndPicksLandOneAtATime() {
        val timeline = DrawTimeline(picks, entrants, reducedMotion = false)
        val (first, second, third) = timeline.segments
        assertEquals(DrawTimeline.WINNER_SPIN_MS, first.spinMs)
        assertEquals(DrawTimeline.ALTERNATE_SPIN_MS, third.spinMs)
        assertTrue("spec: 3 to 5 seconds per winner", first.spinMs in 3_000L..5_000L)
        assertEquals(listOf(picks[0]), timeline.frameAt(first.landMs).landed)
        assertEquals(picks.take(2), timeline.frameAt(second.landMs + 1).landed)
    }

    @Test
    fun reducedMotionIsAQuickReveal() {
        val timeline = DrawTimeline(picks, entrants, reducedMotion = true)
        assertTrue(timeline.segments.all { it.spinMs == DrawTimeline.REDUCED_SPIN_MS })
        assertTrue(timeline.durationMs < DrawTimeline(picks, entrants, reducedMotion = false).durationMs / 3)
    }

    @Test
    fun theReelSlowsDownTowardsTheEnd() {
        val segment = DrawTimeline(picks, entrants, reducedMotion = false).segments.first()
        val timeline = DrawTimeline(picks, entrants, reducedMotion = false)
        val quarter = segment.spinMs / 4
        val early = timeline.frameAt(quarter).reelPosition - timeline.frameAt(0).reelPosition
        val late = timeline.frameAt(segment.spinMs - 1).reelPosition -
            timeline.frameAt(segment.spinMs - 1 - quarter).reelPosition
        assertTrue("faster at first ($early) than at the end ($late)", early > late)
    }

    @Test
    fun theSameDrawAlwaysAnimatesTheSameWay() {
        val a = DrawTimeline(picks, entrants, reducedMotion = false).segments.map { it.reel }
        val b = DrawTimeline(picks, entrants, reducedMotion = false).segments.map { it.reel }
        assertEquals(a, b)
    }

    @Test
    fun aSoleEntrantStillLands() {
        val only = listOf(Pick(1, "amy", Role.WINNER))
        val timeline = DrawTimeline(only, listOf("amy"), reducedMotion = false)
        assertEquals(listOf("amy"), timeline.segments.single().reel)
        assertEquals(only, timeline.frameAt(timeline.durationMs).landed)
    }
}
