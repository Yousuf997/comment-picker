package app.giveaway.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Instant

// Base DAOs for schema v1. Repositories built in later tasks add the queries their screens need.

@Dao
interface AccountDao {
    @Query("SELECT * FROM account LIMIT 1")
    suspend fun get(): AccountEntity?

    @Query("SELECT * FROM account LIMIT 1")
    fun observe(): Flow<AccountEntity?>

    @Upsert
    suspend fun upsert(account: AccountEntity)

    @Query("UPDATE account SET encryptedToken = :encryptedToken, tokenExpiresAt = :expiresAt, tokenRevoked = 0")
    suspend fun updateToken(encryptedToken: ByteArray, expiresAt: Instant)

    @Query("UPDATE account SET tokenRevoked = 1")
    suspend fun markTokenRevoked()

    @Query("DELETE FROM account")
    suspend fun clear()
}

@Dao
interface GiveawayDao {
    @Insert
    suspend fun insert(giveaway: GiveawayEntity): Long

    @Update
    suspend fun update(giveaway: GiveawayEntity)

    @Query("SELECT * FROM giveaway WHERE id = :id")
    suspend fun get(id: Long): GiveawayEntity?

    @Query("SELECT * FROM giveaway ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GiveawayEntity>>

    /** Home list rows with their counts, newest first. */
    @Query(
        """
        SELECT g.*,
            (SELECT COUNT(*) FROM comment c WHERE c.giveawayId = g.id) AS commentCount,
            (SELECT COUNT(*) FROM entry e WHERE e.giveawayId = g.id AND e.isValid = 1) AS validEntryCount
        FROM giveaway g ORDER BY g.createdAt DESC
        """,
    )
    fun observeSummaries(): Flow<List<GiveawaySummary>>

    @Query("UPDATE giveaway SET autoDeleteAt = :at WHERE id = :id")
    suspend fun setAutoDeleteAt(id: Long, at: Instant?)

    @Query("UPDATE giveaway SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: GiveawayStatus)

    @Query("DELETE FROM giveaway WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface RulesDao {
    @Upsert
    suspend fun upsert(rules: RulesEntity)

    @Query("SELECT * FROM rules WHERE giveawayId = :giveawayId")
    suspend fun get(giveawayId: Long): RulesEntity?
}

@Dao
interface CommitmentDao {
    @Insert
    suspend fun insert(commitment: CommitmentEntity)

    @Query("SELECT * FROM commitment WHERE giveawayId = :giveawayId")
    suspend fun get(giveawayId: Long): CommitmentEntity?
}

@Dao
interface ImportStateDao {
    @Upsert
    suspend fun upsert(state: ImportStateEntity)

    @Query("SELECT * FROM import_state WHERE giveawayId = :giveawayId")
    suspend fun get(giveawayId: Long): ImportStateEntity?

    @Query("SELECT * FROM import_state WHERE giveawayId = :giveawayId")
    fun observe(giveawayId: Long): Flow<ImportStateEntity?>
}

@Dao
interface CommentDao {
    /** Re-imported pages are idempotent: a comment already stored is kept as is. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(comments: List<CommentEntity>)

    @Query("SELECT COUNT(*) FROM comment WHERE giveawayId = :giveawayId")
    suspend fun count(giveawayId: Long): Int
}

@Dao
interface EntryDao {
    @Upsert
    suspend fun upsertAll(entries: List<EntryEntity>)

    @Query("SELECT COUNT(*) FROM entry WHERE giveawayId = :giveawayId AND isValid = 1")
    suspend fun validCount(giveawayId: Long): Int
}

@Dao
interface DrawDao {
    @Insert
    suspend fun insert(draw: DrawEntity): Long

    @Insert
    suspend fun insertResults(results: List<DrawResultEntity>)

    @Query("SELECT * FROM draw WHERE realGiveawayId = :giveawayId")
    suspend fun realDraw(giveawayId: Long): DrawEntity?

    @Query("SELECT * FROM draw_result WHERE drawId = :drawId ORDER BY position")
    suspend fun results(drawId: Long): List<DrawResultEntity>
}

@Dao
interface BlocklistDao {
    @Upsert
    suspend fun upsert(entry: BlocklistEntity)

    @Query("SELECT username FROM blocklist")
    suspend fun usernames(): List<String>

    @Query("DELETE FROM blocklist WHERE username = :username")
    suspend fun remove(username: String)
}

@Dao
interface PastWinnerDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(winner: PastWinnerEntity)

    @Query("SELECT DISTINCT username FROM past_winner WHERE giveawayId != :exceptGiveawayId")
    suspend fun usernamesExcept(exceptGiveawayId: Long): List<String>
}

@Dao
interface MediaFileDao {
    @Insert
    suspend fun insert(file: MediaFileEntity): Long

    @Query("SELECT * FROM media_file WHERE giveawayId = :giveawayId")
    suspend fun forGiveaway(giveawayId: Long): List<MediaFileEntity>
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    fun observe(): Flow<SettingsEntity?>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun get(): SettingsEntity?

    @Upsert
    suspend fun upsert(settings: SettingsEntity)
}
