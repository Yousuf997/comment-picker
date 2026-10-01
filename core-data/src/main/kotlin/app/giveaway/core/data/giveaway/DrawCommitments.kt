package app.giveaway.core.data.giveaway

import app.giveaway.core.security.KeyPurpose
import app.giveaway.core.security.KeystoreKeys
import app.giveaway.draw.Commit
import javax.inject.Inject

/**
 * Seals draw seeds with the Keystore seed key (spec: Storage and keys). Each seed is bound to its giveaway as
 * associated data, so a sealed seed copied onto another giveaway fails to open.
 */
interface SeedVault {
    fun seal(giveawayId: Long, seed: ByteArray): ByteArray

    fun open(giveawayId: Long, sealed: ByteArray): ByteArray
}

internal class KeystoreSeedVault @Inject constructor(private val keys: KeystoreKeys) : SeedVault {
    private val box by lazy { keys.secretBox(KeyPurpose.SEED) }

    override fun seal(giveawayId: Long, seed: ByteArray): ByteArray = box.encrypt(seed, associatedData(giveawayId))

    override fun open(giveawayId: Long, sealed: ByteArray): ByteArray = box.decrypt(sealed, associatedData(giveawayId))

    private fun associatedData(giveawayId: Long) = "giveaway.seed.v1:$giveawayId".toByteArray()
}

/** The commit step of the draw (spec: Verifiable draw algorithm, Commit). */
class DrawCommitments(
    private val giveaways: GiveawayRepository,
    private val vault: SeedVault,
    /** 32 bytes from SecureRandom; tests pass a fixed source so screenshots stay stable. */
    private val newSeed: () -> ByteArray,
) {
    @Inject
    constructor(giveaways: GiveawayRepository, vault: SeedVault) : this(giveaways, vault, { Commit.newSeed() })

    /**
     * The giveaway's commit hash. The first call makes a 32-byte seed, seals it and stores it with its hash while the
     * giveaway is a draft; later calls return the same hash, so the code the user pasted never changes.
     */
    suspend fun commitHashFor(giveawayId: Long): String {
        giveaways.commitment(giveawayId)?.let { return it.commitHash }
        val seed = newSeed()
        try {
            val hash = Commit.commitHash(seed)
            giveaways.saveCommitment(giveawayId, hash, vault.seal(giveawayId, seed))
            return hash
        } finally {
            seed.fill(0)
        }
    }
}
