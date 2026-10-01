package app.giveaway.core.data.account

import app.giveaway.core.data.db.AccountDao
import app.giveaway.core.data.db.AccountEntity
import app.giveaway.core.instagram.api.TokenProvider
import app.giveaway.core.instagram.auth.AuthToken
import app.giveaway.core.instagram.auth.IgAccount
import app.giveaway.core.security.KeyPurpose
import app.giveaway.core.security.KeystoreKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/** Whether the app can call Instagram (drives the S4 "sign in again" banner). */
sealed interface SignInState {
    data object SignedOut : SignInState

    data class Active(val username: String) : SignInState

    /** The token expired or Instagram revoked it. Giveaway data is kept (spec: Edge cases). */
    data class Expired(val username: String) : SignInState
}

/** The connected Instagram account and its token (spec: Login flow, step 4). */
interface AccountRepository {
    /** Stores the account and its token, encrypted with the TOKEN Keystore key. Replaces any previous account. */
    suspend fun saveSignIn(token: AuthToken, account: IgAccount)

    /** The decrypted token, or null when signed out. */
    suspend fun token(): AuthToken?

    /** Replaces the token after a refresh, keeping the account, and clears any revoked flag. */
    suspend fun updateToken(accessToken: String, expiresAt: Instant)

    /** Records that Instagram rejected the token (error 190). */
    suspend fun markTokenRevoked()

    suspend fun isTokenRevoked(): Boolean

    fun observeSignInState(): Flow<SignInState>

    fun observeUsername(): Flow<String?>

    /** Forgets the account and token; giveaway data is kept (spec: S5 Disconnect). */
    suspend fun signOut()
}

/** Also the token source for Instagram calls, so core-instagram never depends on core-data. */
class DefaultAccountRepository @Inject constructor(
    private val dao: AccountDao,
    private val keys: KeystoreKeys,
    private val clock: Clock,
) : AccountRepository, TokenProvider {

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

    override suspend fun updateToken(accessToken: String, expiresAt: Instant) {
        val account = dao.get() ?: return
        dao.updateToken(box.encrypt(accessToken.toByteArray(), associatedData(account.igUserId)), expiresAt)
    }

    override suspend fun markTokenRevoked() = dao.markTokenRevoked()

    override suspend fun isTokenRevoked(): Boolean = dao.get()?.tokenRevoked == true

    override fun observeSignInState(): Flow<SignInState> = dao.observe().map { account ->
        when {
            account == null -> SignInState.SignedOut
            account.tokenRevoked || !account.tokenExpiresAt.isAfter(clock.instant()) ->
                SignInState.Expired(account.username)
            else -> SignInState.Active(account.username)
        }
    }

    override fun observeUsername(): Flow<String?> = dao.observe().map { it?.username }

    override suspend fun signOut() = dao.clear()

    override suspend fun accessToken(): String? = token()?.accessToken

    /** Binds the ciphertext to its account, so a token copied onto another row fails to decrypt. */
    private fun associatedData(igUserId: String) = "instagram-token:$igUserId".toByteArray()
}
