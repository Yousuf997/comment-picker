package app.giveaway.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The single on-device database, encrypted with SQLCipher (see [DatabaseFactory]). Schema v1 is exported to
 * `core-data/schemas`. Until the beta freeze the version stays at 1; after it, every change needs a migration and a
 * migration test, and release builds never fall back to destructive migration (plan section 4).
 */
@Database(
    version = 1,
    exportSchema = true,
    entities = [
        AccountEntity::class,
        GiveawayEntity::class,
        RulesEntity::class,
        CommitmentEntity::class,
        ImportStateEntity::class,
        CommentEntity::class,
        EntryEntity::class,
        DrawEntity::class,
        DrawResultEntity::class,
        BlocklistEntity::class,
        PastWinnerEntity::class,
        MediaFileEntity::class,
        SettingsEntity::class,
    ],
)
@TypeConverters(Converters::class)
@Suppress("TooManyFunctions") // One DAO accessor per table.
abstract class GiveawayDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun giveawayDao(): GiveawayDao
    abstract fun rulesDao(): RulesDao
    abstract fun commitmentDao(): CommitmentDao
    abstract fun importStateDao(): ImportStateDao
    abstract fun commentDao(): CommentDao
    abstract fun entryDao(): EntryDao
    abstract fun drawDao(): DrawDao
    abstract fun blocklistDao(): BlocklistDao
    abstract fun pastWinnerDao(): PastWinnerDao
    abstract fun mediaFileDao(): MediaFileDao
    abstract fun settingsDao(): SettingsDao
}
