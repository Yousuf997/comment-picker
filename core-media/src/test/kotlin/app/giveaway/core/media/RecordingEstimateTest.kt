package app.giveaway.core.media

import app.giveaway.draw.Pick
import app.giveaway.draw.Role
import org.junit.Assert.assertTrue
import org.junit.Test

/** M-03: the storage estimate behind S11's low-storage warning. */
class RecordingEstimateTest {

    private val timeline = DrawTimeline(
        List(3) { Pick(it + 1, "user$it", Role.WINNER) } + List(2) { Pick(it + 4, "alt$it", Role.ALTERNATE) },
        List(50) { "user$it" },
        reducedMotion = false,
    )

    @Test
    fun aFullHdDrawOfFivePicksNeedsAFewMegabytes() {
        val bytes = DrawRecorder.estimatedBytes(timeline, VideoSize.FULL_HD)
        // About 19 s at 6 Mbit/s: roughly 14 to 16 MB with muxing overhead.
        assertTrue("$bytes", bytes in 12_000_000L..18_000_000L)
    }

    @Test
    fun theHdFallbackNeedsLess() {
        val hd = DrawRecorder.estimatedBytes(timeline, VideoSize.HD)
        assertTrue(hd < DrawRecorder.estimatedBytes(timeline, VideoSize.FULL_HD))
    }
}
