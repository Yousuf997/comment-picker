package app.giveaway.core.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giveaway.core.data.db.AccountEntity
import app.giveaway.core.data.db.BlocklistEntity
import app.giveaway.core.data.db.CommentEntity
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.giveaway.DefaultGiveawayRepository
import app.giveaway.core.data.giveaway.SeedVault
import app.giveaway.core.data.importing.EntryBuilder
import app.giveaway.core.data.settings.DefaultSettingsRepository
import app.giveaway.core.instagram.api.IgMedia
import app.giveaway.core.instagram.api.MediaKind
import app.giveaway.core.security.backup.BackupCipher
import app.giveaway.core.security.backup.PasswordKdf
import app.giveaway.draw.Commit
import app.giveaway.draw.Rules
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.zip.GZIPInputStream

/** M-11 acceptance: the backup holds every giveaway table, no account or token, and no readable usernames. */
@RunWith(RobolectricTestRunner::class)
class BackupExportTest {

    private val closesAt = Instant.parse("2026-10-08T10:00:00Z")
    private val clock = Clock.fixed(closesAt.plusSeconds(3_600), ZoneOffset.UTC)
    private val seed = ByteArray(Commit.SEED_BYTES) { (it * 11).toByte() }
    private val password = "a long backup password".toCharArray()
    private val cipher = BackupCipher(
        PasswordKdf { pw, salt, _ -> MessageDigest.getInstance("SHA-256").digest(pw + salt) },
        BackupCipher.DEFAULT_PARAMS,
    )

    /** Stands in for the Keystore: "sealed" seeds are reversed, so the test can tell them from the real seed. */
    private val vault = object : SeedVault {
        override fun seal(giveawayId: Long, seed: ByteArray) = seed.reversedArray()

        override fun open(giveawayId: Long, sealed: ByteArray) = sealed.reversedArray()
    }
    private lateinit var db: GiveawayDatabase
    private var id = 0L

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, GiveawayDatabase::class.java).allowMainThreadQueries().build()
        val giveaways = DefaultGiveawayRepository(db, DefaultSettingsRepository(db.settingsDao()), clock)
        val media = IgMedia("m1", MediaKind.IMAGE, false, null, null, closesAt, 0, null)
        val rules = Rules(1, null, null, true, true, true, closesAt, 1, 1)
        id = giveaways.createDraft(media, "Win a tote bag!", "tote.shop", rules)
        giveaways.saveCommitment(id, Commit.commitHash(seed), vault.seal(id, seed))
        giveaways.commit(id)
        giveaways.transition(id, GiveawayStatus.IMPORTING)
        db.commentDao().insertAll(
            listOf("maya.k", "sam_r", "lina.art").mapIndexed { i, user ->
                CommentEntity(id, "c$i", user, "Count me in @friend", closesAt.minusSeconds(60L + i))
            },
        )
        EntryBuilder(db).rebuild(id)
        db.blocklistDao().upsert(BlocklistEntity("spam.bot", closesAt, "Fake"))
        db.accountDao().upsert(AccountEntity("ig1", "tote.shop", byteArrayOf(4, 2), closesAt.plusSeconds(86_400)))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun export(): ByteArray {
        val out = ByteArrayOutputStream()
        BackupManager(db, cipher, vault, clock).export(out, password)
        return out.toByteArray()
    }

    private fun open(file: ByteArray): JSONObject {
        val plain = GZIPInputStream(cipher.decrypt(ByteArrayInputStream(file), password)).use { it.readBytes() }
        return JSONObject(String(plain, Charsets.UTF_8))
    }

    private fun JSONObject.rows(table: String) = getJSONObject("tables").getJSONObject(table).getJSONArray("rows")

    private fun JSONObject.columns(table: String) =
        getJSONObject("tables").getJSONObject(table).getJSONArray("columns")
            .let { a -> List(a.length()) { a.getString(it) } }

    @Test
    fun theFileHasNoReadableUsernamesOrTitles() = runTest {
        val file = String(export(), Charsets.ISO_8859_1)
        assertTrue(file.startsWith("GWBK"))
        listOf("maya.k", "sam_r", "lina.art", "spam.bot", "tote.shop", "Win a tote").forEach {
            assertFalse("$it is readable", file.contains(it))
        }
    }

    @Test
    fun everyGiveawayTableIsInsideButNotTheAccount() = runTest {
        val backup = open(export())
        assertEquals(BackupManager.FORMAT, backup.getString("format"))
        val tables = backup.getJSONObject("tables")
        assertEquals(BackupManager.TABLES.toSet(), tables.keys().asSequence().toSet())
        assertFalse(tables.has("account"))
        assertEquals(3, backup.rows("comment").length())
        assertEquals(3, backup.rows("entry").length())
        assertEquals(1, backup.rows("blocklist").length())
        val token = Base64.getEncoder().encodeToString(byteArrayOf(4, 2))
        assertFalse("no token anywhere", backup.toString().contains(token))
    }

    @Test
    fun theSeedIsWrittenDecryptedSoANewPhoneCanRevealIt() = runTest {
        val backup = open(export())
        val columns = backup.columns("commitment")
        assertTrue(columns.contains("seed"))
        assertFalse(columns.contains("encryptedSeed"))
        val row = backup.rows("commitment").getJSONArray(0)
        val stored = Base64.getDecoder().decode(row.getJSONObject(columns.indexOf("seed")).getString("b64"))
        assertArrayEquals(seed, stored)
    }

    @Test
    fun exportingRecordsWhenForTheReminder() = runTest {
        export()
        assertEquals(clock.instant(), db.settingsDao().get()?.lastBackupAt)
    }
}
