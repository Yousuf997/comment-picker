package app.giveaway.draw

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Deterministic random numbers for a draw (spec: Select). Block k is HMAC-SHA256(key = seed,
 * message = entryListHash || k as 8-byte big-endian); blocks are concatenated and read as 4-byte big-endian
 * unsigned integers. The same seed and entry list always give the same numbers.
 */
class DrawStream internal constructor(private val nextBlock: (blockIndex: Long) -> ByteArray) {

    constructor(seed: ByteArray, entryListHash: ByteArray) : this(hmacBlocks(seed, entryListHash))

    private var blockIndex = 0L
    private var buffer = ByteArray(0)
    private var offset = 0

    /** The next unsigned 32-bit integer from the stream, as a Long in 0..2^32-1. */
    fun nextUInt32(): Long {
        if (offset + UINT32_BYTES > buffer.size) {
            buffer = buffer.copyOfRange(offset, buffer.size) + nextBlock(blockIndex++)
            offset = 0
        }
        var value = 0L
        repeat(UINT32_BYTES) { value = (value shl Byte.SIZE_BITS) or (buffer[offset + it].toLong() and BYTE_MASK) }
        offset += UINT32_BYTES
        return value
    }

    /**
     * A uniform integer in 0 until [bound], by rejection sampling: values at or above the largest multiple of
     * [bound] below 2^32 are discarded, so no result is more likely than another (no modulo bias).
     * Always consumes at least one value, even when [bound] is 1.
     */
    fun uniform(bound: Int): Int {
        require(bound > 0) { "bound must be positive, was $bound" }
        val limit = TWO_POW_32 - TWO_POW_32 % bound
        while (true) {
            val value = nextUInt32()
            if (value < limit) return (value % bound).toInt()
        }
    }

    private companion object {
        const val UINT32_BYTES = 4
        const val BLOCK_COUNTER_BYTES = 8
        const val BYTE_MASK = 0xFFL
        const val TWO_POW_32 = 1L shl 32

        fun hmacBlocks(seed: ByteArray, entryListHash: ByteArray): (Long) -> ByteArray {
            val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(seed, "HmacSHA256")) }
            return { k ->
                // k as 8-byte big-endian.
                val counter = ByteArray(BLOCK_COUNTER_BYTES) { i ->
                    (k ushr (Byte.SIZE_BITS * (BLOCK_COUNTER_BYTES - 1 - i))).toByte()
                }
                mac.doFinal(entryListHash + counter)
            }
        }
    }
}
