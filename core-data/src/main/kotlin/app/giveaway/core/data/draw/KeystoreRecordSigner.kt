package app.giveaway.core.data.draw

import app.giveaway.core.security.KeystoreKeys
import javax.inject.Inject

/** The non-exportable EC P-256 device key from the Android Keystore (spec: Storage and keys). */
internal class KeystoreRecordSigner @Inject constructor(private val keys: KeystoreKeys) : RecordSigner {
    override fun sign(record: ByteArray): RecordSigner.Signature {
        val signer = keys.signer()
        return RecordSigner.Signature(signer.sign(record), signer.publicKeySpki, signer.fingerprint)
    }
}
