package app.giveaway.core.data.account

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.giveaway.core.data.db.DatabaseFactory
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.instagram.auth.AccountType
import app.giveaway.core.instagram.auth.AuthToken
import app.giveaway.core.instagram.auth.IgAccount
import app.giveaway.core.security.KeystoreKeys
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** F-12: the Instagram token is stored only encrypted with the TOKEN Keystore key. */
@RunWith(AndroidJUnit4::class)
class AccountRepositoryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val keys = KeystoreKeys(context)
    private val factory = DatabaseFactory(context, keys, "account-repository-test.db")
    private lateinit var db: GiveawayDatabase
    private lateinit var repository: DefaultAccountRepository

    @Before
    fun setUp() {
        factory.deleteDatabase()
        db = factory.open()
        repository = DefaultAccountRepository(db.accountDao(), keys)
    }

    @After
    fun tearDown() {
        db.close()
        factory.deleteDatabase()
        keys.deleteAll()
    }

    @Test
    fun tokenRoundTripsButIsNeverStoredInPlainText() = runTest {
        repository.saveSignIn(TOKEN, ACCOUNT)

        assertEquals(TOKEN, repository.token())
        assertEquals("shop", repository.observeUsername().first())
        val stored = requireNotNull(db.accountDao().get()).encryptedToken
        assertFalse(String(stored, Charsets.ISO_8859_1).contains(TOKEN.accessToken))
    }

    @Test
    fun signingInAgainReplacesThePreviousAccount() = runTest {
        repository.saveSignIn(TOKEN, ACCOUNT)
        val second = TOKEN.copy(accessToken = "second", igUserId = "2")
        repository.saveSignIn(second, IgAccount("2", "other", AccountType.CREATOR))
        assertEquals("other", repository.observeUsername().first())
        assertEquals("second", repository.token()?.accessToken)
    }

    @Test
    fun signOutForgetsTheToken() = runTest {
        repository.saveSignIn(TOKEN, ACCOUNT)
        repository.signOut()
        assertNull(repository.token())
    }

    private companion object {
        val TOKEN = AuthToken("IGQVJ-long-lived-token", "1789", Instant.parse("2026-12-01T00:00:00Z"))
        val ACCOUNT = IgAccount("1789", "shop", AccountType.BUSINESS)
    }
}
