package app.giveaway.di

import android.content.Context
import androidx.room.Room
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.di.DataModule
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/** UI tests use an in-memory database: SQLCipher's native library doesn't load under Robolectric. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataModule::class])
@Suppress("TooManyFunctions") // One provider per DAO, mirroring DataModule.
object TestDataModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): GiveawayDatabase =
        Room.inMemoryDatabaseBuilder(context, GiveawayDatabase::class.java).allowMainThreadQueries().build()

    @Provides fun accountDao(db: GiveawayDatabase) = db.accountDao()

    @Provides fun giveawayDao(db: GiveawayDatabase) = db.giveawayDao()

    @Provides fun rulesDao(db: GiveawayDatabase) = db.rulesDao()

    @Provides fun commitmentDao(db: GiveawayDatabase) = db.commitmentDao()

    @Provides fun importStateDao(db: GiveawayDatabase) = db.importStateDao()

    @Provides fun commentDao(db: GiveawayDatabase) = db.commentDao()

    @Provides fun entryDao(db: GiveawayDatabase) = db.entryDao()

    @Provides fun drawDao(db: GiveawayDatabase) = db.drawDao()

    @Provides fun blocklistDao(db: GiveawayDatabase) = db.blocklistDao()

    @Provides fun pastWinnerDao(db: GiveawayDatabase) = db.pastWinnerDao()

    @Provides fun mediaFileDao(db: GiveawayDatabase) = db.mediaFileDao()

    @Provides fun settingsDao(db: GiveawayDatabase) = db.settingsDao()
}
