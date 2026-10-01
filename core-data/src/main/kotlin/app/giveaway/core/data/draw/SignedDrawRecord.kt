package app.giveaway.core.data.draw

import app.giveaway.core.security.DeviceSigner
import app.giveaway.draw.DrawRecord

/**
 * A real draw's record with its device signature, as the certificate prints it (plan M-06). The public key travels
 * with it, so it still verifies after a restore on another phone (spec: Edge cases).
 */
class SignedDrawRecord(
    val record: DrawRecord,
    val signature: ByteArray,
    val publicKeySpki: ByteArray,
    /** SHA-256 of [publicKeySpki], lowercase hex. */
    val fingerprint: String,
) {
    /** True when the signature matches the record and the fingerprint matches the key: nothing changed since. */
    fun verifies(): Boolean = fingerprint == DeviceSigner.fingerprintOf(publicKeySpki) &&
        DeviceSigner.verify(record.canonicalBytes(), signature, publicKeySpki)
}
