package app.giveaway.draw

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The commit step of commit-reveal (spec: Verifiable draw algorithm). Before entries close, the organizer posts
 * [commitHash] of a secret seed in the caption; after the draw the seed is revealed and anyone can check it hashes to
 * the posted value, so the seed can't have been chosen after seeing the entries.
 */
object Commit {
    const val SEED_BYTES = 32

    /** A fresh 32-byte seed. Store it only encrypted (SEED Keystore key) and never show it before the draw. */
    fun newSeed(random: SecureRandom = SecureRandom()): ByteArray = ByteArray(SEED_BYTES).also(random::nextBytes)

    /** SHA-256 of the seed as 64 lowercase hex characters. */
    fun commitHash(seed: ByteArray): String {
        require(seed.size == SEED_BYTES) { "seed must be $SEED_BYTES bytes, was ${seed.size}" }
        return sha256(seed).toHex()
    }

    /** What the organizer pastes into the caption (plan A8). */
    fun drawCode(commitHash: String): String = "#draw $commitHash"

    /** Whether the caption contains the commit hash, ignoring case and anything around it. */
    fun captionContains(caption: String?, commitHash: String): Boolean =
        caption != null && caption.contains(commitHash, ignoreCase = true)

    /** True when [seed] is the one behind [commitHash]. */
    fun matches(seed: ByteArray, commitHash: String): Boolean =
        seed.size == SEED_BYTES &&
            MessageDigest.isEqual(commitHash(seed).toByteArray(), commitHash.lowercase().toByteArray())
}

private const val HEX_RADIX = 16

internal fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

internal fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0 && all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) { "not a hex string" }
    return chunked(2).map { it.toInt(HEX_RADIX).toByte() }.toByteArray()
}
