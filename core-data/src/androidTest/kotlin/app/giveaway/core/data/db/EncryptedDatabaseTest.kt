package app.giveaway.core.data.db

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.giveaway.core.security.KeyPurpose
import app.giveaway.core.security.KeystoreKeys
import app.giveaway.draw.Role
import kotlinx.coroutines.test.runTest
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

/** F-11 acceptance: the database is encrypted on disk, reopens with its wrapped key, and rejects wrong keys. */
@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val keys = KeystoreKeys(context)
    private val factory = DatabaseFactory(context, keys, TEST_DB)
    private var db: GiveawayDatabase? = null

    @Before
    fun clean() = factory.deleteDatabase()

    @After
    fun tearDown() {
        db?.close()
        factory.deleteDatabase()
        keys.deleteAll()
    }

    private fun open() = factory.open().also { db = it }

    @Test
    fun databaseFileIsNotPlainSqlite() = runTest {
        open().giveawayDao().insert(giveaway())
        db!!.close()
        val header = context.getDatabasePath(TEST_DB).readBytes().copyOfRange(0, 16)
        val plainHeader = "SQLite format 3\u0000".toByteArray()
        assertFalse("File starts with the plain SQLite header", header.contentEquals(plainHeader))
    }

    @Test
    fun reopensWithTheStoredPassphrase() = runTest {
        val id = open().giveawayDao().insert(giveaway(title = "Summer drop"))
        db!!.close()
        assertEquals("Summer drop", open().giveawayDao().get(id)?.title)
    }

    @Test
    fun aWrongPassphraseCannotReadTheFile() = runTest {
        open().giveawayDao().insert(giveaway())
        db!!.close()
        DatabaseFactory.loadSqlCipher()
        val path = context.getDatabasePath(TEST_DB).path
        assertThrows(Exception::class.java) {
            SQLiteDatabase.openDatabase(path, ByteArray(32) { 7 }, null, SQLiteDatabase.OPEN_READONLY, null).use {
                it.rawQuery("SELECT count(*) FROM giveaway", null).use { cursor -> cursor.moveToFirst() }
            }
        }
    }

    @Test
    fun thePassphraseIsStoredOnlyWrapped() {
        val passphrase = factory.loadOrCreatePassphrase(keys.secretBox(KeyPurpose.DATABASE))
        val stored = File(context.noBackupFilesDir, "$TEST_DB.key").readBytes()
        assertEquals(32, passphrase.size)
        assertFalse(stored.toList().windowed(passphrase.size).any { it == passphrase.toList() })
        assertTrue(passphrase.contentEquals(factory.loadOrCreatePassphrase(keys.secretBox(KeyPurpose.DATABASE))))
    }

    @Test
    fun aLostKeystoreKeyRaisesATypedError() = runTest {
        open().giveawayDao().insert(giveaway())
        db!!.close()
        db = null
        keys.deleteAll()
        assertThrows(DatabaseKeyLostException::class.java) { factory.open() }
    }

    @Test
    fun onlyOneRealDrawPerGiveawayButAnyNumberOfTestDraws() = runTest {
        val dao = open().drawDao()
        val id = db!!.giveawayDao().insert(giveaway())
        dao.insert(draw(id, isTest = true))
        dao.insert(draw(id, isTest = true))
        val realId = dao.insert(draw(id, isTest = false))
        val second = runCatching { dao.insert(draw(id, isTest = false)) }.exceptionOrNull()
        assertTrue("Expected a unique-constraint failure, got $second", second is SQLiteConstraintException)
        assertEquals(realId, dao.realDraw(id)?.id)
    }

    @Test
    fun deletingAGiveawayRemovesItsRowsButKeepsPastWinners() = runTest {
        val database = open()
        val id = database.giveawayDao().insert(giveaway())
        database.rulesDao().upsert(RulesEntity(id, 1, "#win", null, true, true, true, 3, 2))
        val drawId = database.drawDao().insert(draw(id, isTest = false))
        database.drawDao().insertResults(
            listOf(DrawResultEntity(drawId, 0, "ana", Role.WINNER, ConfirmationStatus.CONFIRMED, null, null)),
        )
        database.pastWinnerDao().insert(PastWinnerEntity("ana", id, NOW))

        database.giveawayDao().delete(id)

        assertNull(database.rulesDao().get(id))
        assertNull(database.drawDao().realDraw(id))
        assertTrue(database.drawDao().results(drawId).isEmpty())
        assertEquals(listOf("ana"), database.pastWinnerDao().usernamesExcept(exceptGiveawayId = -1))
    }

    @Test
    fun settingsDefaultsMatchTheSpec() = runTest {
        val dao = open().settingsDao()
        dao.upsert(SettingsEntity())
        val settings = requireNotNull(dao.get())
        assertEquals(60, settings.lockAfterSeconds)
        assertEquals(90, settings.autoDeleteDays)
        assertTrue(settings.recordDrawsByDefault)
        assertFalse(settings.appLockEnabled)
    }

    private fun giveaway(title: String = "Giveaway") = GiveawayEntity(
        title = title,
        igMediaId = "m1",
        mediaType = "IMAGE",
        thumbnailUrl = null,
        status = GiveawayStatus.DRAFT,
        createdAt = NOW,
        closesAt = NOW.plusSeconds(3600),
        autoDeleteAt = null,
        ownerUsername = "shop",
    )

    private fun draw(giveawayId: Long, isTest: Boolean) = DrawEntity(
        giveawayId = giveawayId,
        isTest = isTest,
        realGiveawayId = if (isTest) null else giveawayId,
        seed = ByteArray(32),
        entryListHash = "hash",
        drawnAt = NOW,
        algorithmVersion = "v1",
        deviceSignature = ByteArray(64),
        signerPublicKey = ByteArray(91),
        signerFingerprint = "fp",
        captionCheck = CaptionCheck.FOUND,
        integrityVerified = false,
        partialImport = false,
        entryCount = 10,
    )

    private companion object {
        const val TEST_DB = "encrypted-database-test.db"
        val NOW: Instant = Instant.parse("2026-10-01T12:00:00Z")
    }
}
