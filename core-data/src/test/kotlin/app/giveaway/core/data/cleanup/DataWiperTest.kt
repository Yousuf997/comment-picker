package app.giveaway.core.data.cleanup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giveaway.core.data.db.DatabaseFactory
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.db.GiveawayStatus
import app.giveaway.core.data.db.MediaFileEntity
import app.giveaway.core.data.db.MediaKind
import app.giveaway.core.data.db.PastWinnerEntity
import app.giveaway.core.data.giveaway.DefaultGiveawayRepository
import app.giveaway.core.data.settings.DefaultSettingsRepository
import app.giveaway.core.instagram.api.IgMedia
import app.giveaway.draw.Rules
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import app.giveaway.core.instagram.api.MediaKind as IgMediaKind

/** M-14 acceptance: auto-delete drops expired giveaways and their files; "Delete everything" leaves nothing behind. */
@RunWith(RobolectricTestRunner::class)
class DataWiperTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val start = Instant.parse("2026-10-01T09:00:00Z")
    private var now = start
    private val clock = object : Clock() {
        override fun instant() = now

        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId?) = this
    }
    private lateinit var db: GiveawayDatabase
    private var keysErased = 0
    private var workCancelled = 0

    @Before
    fun setUp() {
        // A real file, like the app's, so deleting it can be checked.
        db = Room.databaseBuilder(context, GiveawayDatabase::class.java, DatabaseFactory.DATABASE_NAME)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        if (db.isOpen) db.close()
        context.deleteDatabase(DatabaseFactory.DATABASE_NAME)
    }

    private fun wiper() = DataWiper(context, db, clock, { keysErased++ }, { workCancelled++ })

    private suspend fun archived(title: String, autoDeleteDays: Int): Long {
        val settings = DefaultSettingsRepository(db.settingsDao())
        settings.update { it.copy(autoDeleteDays = autoDeleteDays) }
        val giveaways = DefaultGiveawayRepository(db, settings, clock)
        val post = IgMedia(title, IgMediaKind.IMAGE, false, null, null, now, 0, null)
        val id = giveaways.createDraft(post, title, "shop", Rules(1, null, null, true, true, true, now, 1, 0))
        // Straight to ARCHIVED for the test; the state machine is covered elsewhere.
        db.giveawayDao().updateStatus(id, GiveawayStatus.DRAWN)
        giveaways.transition(id, GiveawayStatus.ARCHIVED)
        return id
    }

    @Test
    fun autoDeleteRemovesOnlyExpiredGiveawaysAndTheirFiles() = runTest {
        val old = archived("Old", autoDeleteDays = 30)
        val recent = archived("Recent", autoDeleteDays = 90)
        val certificate = File(context.filesDir, "certificates/certificate-$old.pdf").apply {
            parentFile?.mkdirs()
            writeText("pdf")
        }
        val file = MediaFileEntity(0, old, MediaKind.CERTIFICATE_PDF, certificate.path, false, now, false)
        db.mediaFileDao().insert(file)
        db.pastWinnerDao().insert(PastWinnerEntity("amy", old, now))

        now = start.plus(Duration.ofDays(31))
        assertEquals(1, wiper().deleteExpired())

        assertNull(db.giveawayDao().get(old))
        assertNotNull(db.giveawayDao().get(recent))
        assertFalse(certificate.exists())
        val pastWinners = db.pastWinnerDao().usernamesExcept(-1)
        assertEquals("past winners outlive the giveaway", listOf("amy"), pastWinners)
        assertEquals(0, wiper().deleteExpired())
    }

    @Test
    fun deleteEverythingLeavesNoDatabaseKeysFilesOrPreferences() = runTest {
        archived("Kept until now", autoDeleteDays = 90)
        File(context.filesDir, "videos/draw-1.mp4").apply { parentFile?.mkdirs() }.writeText("video")
        File(context.noBackupFilesDir, "app-lock.pin").writeText("pin")
        File(context.noBackupFilesDir, "giveaway.db.key").writeText("key")
        context.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit().putString("a", "b").commit()

        wiper().deleteEverything()

        assertFalse(context.getDatabasePath(DatabaseFactory.DATABASE_NAME).exists())
        assertEquals(1, keysErased)
        assertEquals(1, workCancelled)
        for (dir in listOf(context.filesDir, context.noBackupFilesDir, File(context.dataDir, "shared_prefs"))) {
            assertTrue("$dir is empty", dir.listFiles().isNullOrEmpty())
        }
    }
}
