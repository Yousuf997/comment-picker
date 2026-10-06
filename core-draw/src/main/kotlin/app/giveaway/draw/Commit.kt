package app.giveaway.draw

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The draw's seed and its hash (spec: Verifiable draw algorithm; plan A35). The seed comes from SecureRandom and stays
 * sealed on the phone until the draw; [commitHash] is stored and signed with the draw, so the revealed seed can be
 * checked against it. Nothing is posted in the caption.
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
