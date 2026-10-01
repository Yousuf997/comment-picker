package app.giveaway.core.media

import app.giveaway.draw.Pick
import app.giveaway.draw.Role
import kotlin.math.pow
import kotlin.random.Random

/**
 * The draw animation as a function of time (plan C-22): the S12 screen and the recorder (M-02) both read frames from
 * it, so the video shows exactly what was on screen. It only replays the saved result; it never picks anything.
 *
 * Each pick spins a reel of entrants that slows down and lands on the picked person (spec: Motion, 3 to 5 seconds
 * per winner). Alternates spin faster. With reduced motion every pick is a quick reveal.
 */
class DrawTimeline(
    picks: List<Pick>,
    entrants: List<String>,
    reducedMotion: Boolean,
) {
    /** One pick's stretch of the timeline. */
    class Segment(val pick: Pick, val reel: List<String>, val startMs: Long, val spinMs: Long) {
        val landMs: Long get() = startMs + spinMs
    }

    val segments: List<Segment>
    val durationMs: Long

    init {
        var t = 0L
        segments = picks.map { pick ->
            val spin = when {
                reducedMotion -> REDUCED_SPIN_MS
                pick.role == Role.WINNER -> WINNER_SPIN_MS
                else -> ALTERNATE_SPIN_MS
            }
            Segment(pick, reelFor(pick, entrants), t, spin).also { t += spin + PAUSE_MS }
        }
        durationMs = t
    }

    /** What is on screen at [timeMs] after the start. */
    data class Frame(
        /** The pick being spun, or null once all have landed. */
        val current: Segment?,
        /** Position in the current reel: whole numbers are names centred in the bar. */
        val reelPosition: Float,
        /** Picks that have landed, in order; each one is announced as it lands. */
        val landed: List<Pick>,
        val finished: Boolean,
    )

    fun frameAt(timeMs: Long): Frame {
        val landed = segments.filter { timeMs >= it.landMs }.map { it.pick }
        val current = segments.firstOrNull { timeMs < it.landMs }
        if (current == null) return Frame(null, 0f, landed, finished = timeMs >= durationMs)
        val progress = ((timeMs - current.startMs).coerceAtLeast(0).toFloat() / current.spinMs).coerceIn(0f, 1f)
        return Frame(current, easeOut(progress) * (current.reel.size - 1), landed, finished = false)
    }

    companion object {
        const val WINNER_SPIN_MS = 3_600L
        const val ALTERNATE_SPIN_MS = 1_600L
        const val REDUCED_SPIN_MS = 300L
        const val PAUSE_MS = 700L
        private const val REEL_LENGTH = 30
        private const val EASE_POWER = 3.0

        /** Fast at first, settling on the result: cubic ease-out. */
        fun easeOut(x: Float): Float = 1f - (1f - x).toDouble().pow(EASE_POWER).toFloat()

        /**
         * The names that pass by before the picked one, ending on it. Fillers come from the entrants in a fixed
         * pseudo-random order per position, so the same draw always animates the same way.
         */
        fun reelFor(pick: Pick, entrants: List<String>): List<String> {
            val others = entrants.distinct().filter { it != pick.username }
            if (others.isEmpty()) return listOf(pick.username)
            val random = Random(pick.position)
            val fillers = List(REEL_LENGTH - 1) { others[random.nextInt(others.size)] }
            return fillers + pick.username
        }
    }
}
