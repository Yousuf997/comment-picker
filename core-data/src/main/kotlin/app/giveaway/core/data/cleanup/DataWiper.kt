package app.giveaway.core.data.cleanup

import android.content.Context
import androidx.room.withTransaction
import androidx.work.WorkManager
import app.giveaway.core.data.db.DatabaseFactory
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.security.KeystoreKeys
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import javax.inject.Inject

/** "Delete everything on this phone" (spec: S5, Privacy). Replaced in UI tests. */
fun interface EverythingWiper {
    /** Wipes all app data; the caller restarts the app at S1 afterwards, since nothing it held is valid any more. */
    suspend fun deleteEverything()
}

/**
 * Removes data (plan M-14). Auto-delete drops archived giveaways once their date passes, with their private files;
 * past winners stay, so they can still be excluded later. "Delete everything" stops all background work, deletes the
 * database, every Keystore key (database, token, seed, PIN and signing keys), all private files (recordings,
 * certificates, the wrapped database key, the PIN file) and preferences. Videos the user saved to the gallery are
 * theirs and stay.
 */
class DataWiper(
    private val context: Context,
    private val db: GiveawayDatabase,
    private val clock: Clock,
    private val eraseKeys: () -> Unit,
    private val cancelWork: () -> Unit,
) : EverythingWiper {
    @Inject
    constructor(@ApplicationContext context: Context, db: GiveawayDatabase, clock: Clock, keys: KeystoreKeys) : this(
        context,
        db,
        clock,
        keys::deleteAll,
        { WorkManager.getInstance(context).cancelAllWork() },
    )

    /** Deletes giveaways whose auto-delete date has passed. Returns how many. */
    suspend fun deleteExpired(): Int {
        val expired = db.giveawayDao().expired(clock.instant())
        expired.forEach { id -> db.withTransaction { db.deleteGiveaway(id) }.forEach { it.delete() } }
        return expired.size
    }

    override suspend fun deleteEverything() = withContext(Dispatchers.IO) {
        cancelWork()
        db.close()
        context.deleteDatabase(DatabaseFactory.DATABASE_NAME)
        eraseKeys()
        listOf(context.filesDir, context.cacheDir, context.noBackupFilesDir, File(context.dataDir, "shared_prefs"))
            .forEach { dir -> dir.listFiles()?.forEach { it.deleteRecursively() } }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WipeModule {
    @Binds
    internal abstract fun everythingWiper(impl: DataWiper): EverythingWiper
}
