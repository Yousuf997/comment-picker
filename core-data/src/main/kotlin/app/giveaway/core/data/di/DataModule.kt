package app.giveaway.core.data.di

import app.giveaway.core.data.db.DatabaseFactory
import app.giveaway.core.data.db.GiveawayDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
@Suppress("TooManyFunctions") // One provider per DAO.
internal object DataModule {
    @Provides
    @Singleton
    fun database(factory: DatabaseFactory): GiveawayDatabase = factory.open()

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
