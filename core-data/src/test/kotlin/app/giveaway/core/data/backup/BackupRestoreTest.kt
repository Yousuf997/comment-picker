package app.giveaway.core.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giveaway.core.data.db.AccountEntity
import app.giveaway.core.data.db.AppLockMethod
import app.giveaway.core.data.db.CaptionCheck
import app.giveaway.core.data.db.CommentEntity
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.db.SettingsEntity
import app.giveaway.core.data.draw.DrawChecks
import app.giveaway.core.data.draw.DrawService
import app.giveaway.core.data.draw.RecordSigner
import app.giveaway.core.data.draw.WinnerRepository
import app.giveaway.core.data.giveaway.DefaultGiveawayRepository
import app.giveaway.core.data.giveaway.SeedVault
import app.giveaway.core.data.importing.EntryBuilder
import app.giveaway.core.data.media.MediaRepository
import app.giveaway.core.data.review.EntryRepository
import app.giveaway.core.data.settings.DefaultSettingsRepository
import app.giveaway.core.instagram.api.IgMedia
import app.giveaway.core.instagram.api.MediaKind
import app.giveaway.core.security.DeviceSigner
import app.giveaway.core.security.backup.BackupCipher
import app.giveaway.core.security.backup.BackupFormatException
import app.giveaway.core.security.backup.PasswordKdf
import app.giveaway.draw.Commit
import app.giveaway.draw.Rules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.zip.GZIPOutputStream

/** M-12 acceptance: a restore brings everything back and certificates still verify; a bad file changes nothing. */
@RunWith(RobolectricTestRunner::class)
class BackupRestoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val closesAt = Instant.parse("2026-10-08T10:00:00Z")
    private val clock = Clock.fixed(closesAt.plusSeconds(3_600), ZoneOffset.UTC)
    private val seed = ByteArray(Commit.SEED_BYTES) { (it * 13).toByte() }
    private val laterSeed = ByteArray(Commit.SEED_BYTES) { (it + 1).toByte() }
    private val password = "a long backup password".toCharArray()
    private val cipher = BackupCipher(
        PasswordKdf { pw, salt, _ -> MessageDigest.getInstance("SHA-256").digest(pw + salt) },
        BackupCipher.DEFAULT_PARAMS,
    )

    /** Each phone has its own Keystore: sealed seeds from one can't be opened by the other. */
    private class PhoneVault(private val key: Byte) : SeedVault {
        override fun seal(giveawayId: Long, seed: ByteArray) = seed.map { (it.toInt() xor key.toInt()).toByte() }
            .toByteArray()

        override fun open(giveawayId: Long, sealed: ByteArray) = seal(giveawayId, sealed)
    }
    private val oldVault = PhoneVault(0x11)
    private val newVault = PhoneVault(0x5a)

    private val keyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        .generateKeyPair()
    private val signer = object : RecordSigner {
        override fun sign(record: ByteArray): RecordSigner.Signature {
            val bytes = Signature.getInstance("SHA256withECDSA").run {
                initSign(keyPair.private)
                update(record)
                sign()
            }
            val spki = keyPair.public.encoded
            return RecordSigner.Signature(bytes, spki, DeviceSigner.fingerprintOf(spki))
        }
    }

    private lateinit var oldPhone: GiveawayDatabase
    private lateinit var newPhone: GiveawayDatabase
    private val scheduled = mutableListOf<Long>()
    private var drawn = 0L
    private var waiting = 0L

    private fun database() =
        Room.inMemoryDatabaseBuilder(context, GiveawayDatabase::class.java).allowMainThreadQueries().build()

    private fun manager(db: GiveawayDatabase, vault: SeedVault) =
        BackupManager(db, cipher, vault, clock, MediaRepository(context, db, clock)) { id, _ -> scheduled += id }

    @Before
    fun setUp() = runTest {
        oldPhone = database()
        newPhone = database()
        val giveaways = DefaultGiveawayRepository(oldPhone, DefaultSettingsRepository(oldPhone.settingsDao()), clock)
        val rules = Rules(1, null, null, true, true, true, closesAt, 2, 1)
        val post = IgMedia("m1", MediaKind.IMAGE, false, null, null, closesAt, 0, null)
        drawn = giveaways.createDraft(post, "Win a tote bag!", "tote.shop", rules)
        giveaways.saveCommitment(drawn, Commit.commitHash(seed), oldVault.seal(drawn, seed))
        giveaways.commit(drawn)
        giveaways.transition(drawn, GiveawayStatus.IMPORTING)
        oldPhone.commentDao().insertAll(
            listOf("amy", "bob", "cat", "dan", "eve").mapIndexed { i, user ->
                CommentEntity(drawn, "c$i", user, "In @friend", closesAt.minusSeconds(60L + i))
            },
        )
        val builder = EntryBuilder(oldPhone)
        builder.rebuild(drawn)
        EntryRepository(oldPhone, builder, clock).exclude(drawn, "c4", "Fake account")
        DrawService(oldPhone, oldVault, signer, clock).realDraw(drawn, DrawChecks(CaptionCheck.FOUND, true))
        WinnerRepository(oldPhone, clock).replace(drawn, position = 2, reason = "Didn't follow")
        // A second giveaway still waiting for its deadline, so the restore must schedule it again.
        val later = rules.copy(closesAt = closesAt.plusSeconds(86_400))
        waiting = giveaways.createDraft(post.copy(id = "m2"), "Second", "tote.shop", later)
        giveaways.saveCommitment(waiting, Commit.commitHash(laterSeed), oldVault.seal(waiting, laterSeed))
        giveaways.commit(waiting)

        // The new phone already has an account, app lock on, and a giveaway of its own.
        newPhone.accountDao().upsert(AccountEntity("ig9", "new.phone", byteArrayOf(1), closesAt))
        newPhone.settingsDao().upsert(
            SettingsEntity(
                appLockEnabled = true,
                appLockMethod = AppLockMethod.PIN,
                lockAfterSeconds = 30,
                blockScreenshots = true,
            ),
        )
        // The backup asks for a very long idle time and no screenshot blocking; it must not get them.
        oldPhone.settingsDao().upsert(SettingsEntity(lockAfterSeconds = Int.MAX_VALUE, blockScreenshots = false))
        DefaultGiveawayRepository(newPhone, DefaultSettingsRepository(newPhone.settingsDao()), clock)
            .createDraft(post.copy(id = "local"), "Local draft", "new.phone", rules)
    }

    @After
    fun tearDown() {
        oldPhone.close()
        newPhone.close()
    }

    private suspend fun backup(): ByteArray {
        val out = ByteArrayOutputStream()
        manager(oldPhone, oldVault).export(out, password)
        return out.toByteArray()
    }

    private fun titles(db: GiveawayDatabase) =
        runBlocking { db.giveawayDao().observeAll().first().map { it.title }.toSet() }

    @Test
    fun everythingComesBackAndTheCertificateStillVerifies() = runTest {
        manager(newPhone, newVault).restore(ByteArrayInputStream(backup()), password)

        assertEquals(setOf("Win a tote bag!", "Second"), titles(newPhone))
        assertEquals(5, newPhone.commentDao().count(drawn))
        val signed = DrawService(newPhone, newVault, signer, clock).signedRecord(drawn)!!
        assertTrue("signed on the old phone, verified on the new one", signed.verifies())
        assertEquals("Fake account", signed.record.manualExclusions.single().reason)
        assertEquals(1, WinnerRepository(newPhone, clock).observe(drawn).first()!!.replacements.size)
        // The committed seed is sealed again with this phone's key.
        val sealed = newPhone.commitmentDao().get(waiting)!!.encryptedSeed
        assertArrayEquals(laterSeed, newVault.open(waiting, sealed))
        assertEquals(listOf(waiting), scheduled)
    }

    @Test
    fun theUserSignsInAgainAndKeepsThisPhonesAppLock() = runTest {
        manager(newPhone, newVault).restore(ByteArrayInputStream(backup()), password)
        assertNull(newPhone.accountDao().get())
        val settings = newPhone.settingsDao().get()!!
        assertTrue(settings.appLockEnabled)
        assertEquals(AppLockMethod.PIN, settings.appLockMethod)
        assertEquals(30, settings.lockAfterSeconds)
        assertTrue(settings.blockScreenshots)
        assertEquals(clock.instant(), settings.lastBackupAt)
    }

    @Test
    fun inspectSaysWhatTheBackupHolds() = runTest {
        val summary = manager(newPhone, newVault).inspect(ByteArrayInputStream(backup()), password)
        assertEquals(BackupSummary(clock.instant(), giveaways = 2), summary)
        assertEquals(setOf("Local draft"), titles(newPhone))
    }

    @Test
    fun aWrongPasswordChangesNothing() = runTest {
        val file = backup()
        val result = runCatching {
            manager(newPhone, newVault).restore(ByteArrayInputStream(file), "nope".toCharArray())
        }
        assertTrue(result.exceptionOrNull() is IOException)
        assertUntouched()
    }

    @Test
    fun aTruncatedFileChangesNothing() = runTest {
        val file = backup()
        val result = runCatching {
            manager(newPhone, newVault).restore(ByteArrayInputStream(file.copyOf(file.size - 20)), password)
        }
        assertTrue(result.exceptionOrNull() is IOException)
        assertUntouched()
    }

    @Test
    fun columnsTheDatabaseDoesntHaveAreRefused() = runTest {
        val out = ByteArrayOutputStream()
        val crafted = """{"format":"giveaway-backup","schemaVersion":1,"tables":{"blocklist":""" +
            """{"columns":["username","addedAt","note","x) VALUES (1); DROP TABLE giveaway; --"],""" +
            """"rows":[["amy",1,null,1]]}}}"""
        GZIPOutputStream(cipher.encrypt(out, password)).use { it.write(crafted.toByteArray()) }
        val result = runCatching {
            manager(newPhone, newVault).restore(ByteArrayInputStream(out.toByteArray()), password)
        }
        val error = result.exceptionOrNull() as BackupFormatException
        assertEquals(BackupFormatException.Reason.NOT_A_BACKUP, error.reason)
        assertUntouched()
    }

    @Test
    fun aBackupFromANewerAppIsRefused() = runTest {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(cipher.encrypt(out, password)).use {
            it.write("""{"format":"giveaway-backup","schemaVersion":99,"tables":{}}""".toByteArray())
        }
        val result = runCatching {
            manager(newPhone, newVault).restore(ByteArrayInputStream(out.toByteArray()), password)
        }
        val error = result.exceptionOrNull() as BackupFormatException
        assertEquals(BackupFormatException.Reason.NEWER_VERSION, error.reason)
        assertUntouched()
    }

    private suspend fun assertUntouched() {
        assertEquals(setOf("Local draft"), titles(newPhone))
        assertEquals("new.phone", newPhone.accountDao().get()?.username)
        assertFalse(scheduled.isNotEmpty())
    }
}
