package app.giveaway.core.data.cleanup

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.giveaway.core.data.db.DatabaseFactory
import app.giveaway.core.data.db.SettingsEntity
import app.giveaway.core.security.KeyPurpose
import app.giveaway.core.security.KeystoreKeys
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.time.Clock

/** M-14 acceptance on a device: after "Delete everything" there is no database file, Keystore alias or app file. */
@RunWith(AndroidJUnit4::class)
class DeleteEverythingTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun ourAliases(): List<String> = KeyStore.getInstance("AndroidKeyStore").run {
        load(null)
        aliases().toList().filter { it.startsWith("giveaway.") }
    }

    @Test
    fun nothingIsLeft() = runTest {
        val keys = KeystoreKeys(context)
        val db = DatabaseFactory(context, keys, DatabaseFactory.DATABASE_NAME).open()
        db.settingsDao().upsert(SettingsEntity(onboardingComplete = true))
        KeyPurpose.entries.forEach { keys.secretBox(it) }
        keys.signer()
        File(context.filesDir, "videos/draw-1.mp4").apply { parentFile?.mkdirs() }.writeText("video")
        assertEquals(KeyPurpose.entries.size + 1, ourAliases().size)

        DataWiper(context, db, Clock.systemUTC(), keys).deleteEverything()

        assertFalse(context.getDatabasePath(DatabaseFactory.DATABASE_NAME).exists())
        assertTrue("no Keystore aliases", ourAliases().isEmpty())
        assertTrue("no app files", context.filesDir.listFiles().isNullOrEmpty())
    }
}
