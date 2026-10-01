package app.giveaway.draw

sealed interface VerificationResult {
    data object Ok : VerificationResult

    data class Mismatch(val reason: String) : VerificationResult
}

/**
 * Re-runs a draw from public inputs (spec: Verification): the commit hash from the caption, the revealed seed and the
 * exported entry list. The same check is published as an open verifier so followers can run it themselves.
 */
object Verifier {
    fun verify(
        commitHash: String,
        revealedSeed: ByteArray,
        list: CanonicalEntryList,
        winners: Int,
        alternates: Int,
        claimed: List<Pick>,
        algorithm: DrawAlgorithm = DrawV1,
    ): VerificationResult {
        if (!Commit.matches(revealedSeed, commitHash)) {
            return VerificationResult.Mismatch("The revealed seed does not match the posted draw code")
        }
        val expected = algorithm.select(revealedSeed, list, winners, alternates).picks
        return if (expected == claimed) {
            VerificationResult.Ok
        } else {
            VerificationResult.Mismatch("Re-running the draw gives ${expected.joinToString { it.username }}")
        }
    }
}
