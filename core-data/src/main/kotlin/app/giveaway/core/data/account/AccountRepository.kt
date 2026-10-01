package app.giveaway.core.data.account

import app.giveaway.core.data.db.AccountDao
import app.giveaway.core.data.db.AccountEntity
import app.giveaway.core.instagram.auth.AuthToken
import app.giveaway.core.instagram.auth.IgAccount
import app.giveaway.core.security.KeyPurpose
import app.giveaway.core.security.KeystoreKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** The connected Instagram account and its token (spec: Login flow, step 4). */
interface AccountRepository {
    /** Stores the account and its token, encrypted with the TOKEN Keystore key. Replaces any previous account. */
    suspend fun saveSignIn(token: AuthToken, account: IgAccount)

    /** The decrypted token, or null when signed out. */
    suspend fun token(): AuthToken?

    fun observeUsername(): Flow<String?>

    /** Forgets the account and token; giveaway data is kept (spec: S5 Disconnect). */
    suspend fun signOut()
}

class DefaultAccountRepository @Inject constructor(
    private val dao: AccountDao,
    private val keys: KeystoreKeys,
) : AccountRepository {

    private val box get() = keys.secretBox(KeyPurpose.TOKEN)

    override suspend fun saveSignIn(token: AuthToken, account: IgAccount) {
        dao.clear()
        dao.upsert(
            AccountEntity(
                igUserId = account.igUserId,
                username = account.username,
                encryptedToken = box.encrypt(token.accessToken.toByteArray(), associatedData(account.igUserId)),
                tokenExpiresAt = token.expiresAt,
            ),
        )
    }

    override suspend fun token(): AuthToken? {
        val account = dao.get() ?: return null
        val accessToken = box.decrypt(account.encryptedToken, associatedData(account.igUserId)).decodeToString()
        return AuthToken(accessToken, account.igUserId, account.tokenExpiresAt)
    }

    override fun observeUsername(): Flow<String?> = dao.observe().map { it?.username }

    override suspend fun signOut() = dao.clear()

    /** Binds the ciphertext to its account, so a token copied onto another row fails to decrypt. */
    private fun associatedData(igUserId: String) = "instagram-token:$igUserId".toByteArray()
}
