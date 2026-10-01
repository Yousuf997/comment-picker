package app.giveaway.draw

/** One picked person. [position] starts at 1; winners come first, in order, then alternates. */
data class Pick(val position: Int, val username: String, val role: Role)

data class DrawOutcome(
    val algorithmVersion: String,
    val entryListHashHex: String,
    /** Winners plus alternates asked for; [picks] has fewer when there aren't enough different people. */
    val requested: Int,
    val picks: List<Pick>,
) {
    val winners: List<Pick> get() = picks.filter { it.role == Role.WINNER }
    val alternates: List<Pick> get() = picks.filter { it.role == Role.ALTERNATE }
}

interface DrawAlgorithm {
    val version: String

    fun select(seed: ByteArray, list: CanonicalEntryList, winners: Int, alternates: Int): DrawOutcome
}

/**
 * Draw algorithm v1 (docs/draw-spec-v1.md). A partial Fisher-Yates shuffle of the canonical list driven by
 * [DrawStream]: for i = 0, 1, 2... swap position i with i + uniform(n - i), then take the name now at i unless that
 * person was already picked (plan A12: with "one entry per person" off, someone with several entries has more chances
 * but can't win twice). It stops once winners + alternates different people are picked or the list runs out.
 */
object DrawV1 : DrawAlgorithm {
    override val version: String = ALGORITHM_V1

    override fun select(seed: ByteArray, list: CanonicalEntryList, winners: Int, alternates: Int): DrawOutcome {
        require(seed.size == Commit.SEED_BYTES) { "seed must be ${Commit.SEED_BYTES} bytes" }
        require(winners >= 0 && alternates >= 0) { "counts can't be negative" }
        val requested = winners + alternates
        val target = minOf(requested, list.distinctCount)
        val stream = DrawStream(seed, list.hash)
        val pool = list.usernames.toMutableList()
        val picked = LinkedHashSet<String>()
        var i = 0
        while (picked.size < target) {
            val j = i + stream.uniform(pool.size - i)
            pool[i] = pool[j].also { pool[j] = pool[i] }
            picked += pool[i]
            i++
        }
        val picks = picked.mapIndexed { index, name ->
            Pick(position = index + 1, username = name, role = if (index < winners) Role.WINNER else Role.ALTERNATE)
        }
        return DrawOutcome(version, list.hashHex, requested, picks)
    }
}
