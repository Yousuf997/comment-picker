package app.giveaway.core.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
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

    @Query("UPDATE commitment SET captionVerifiedAt = :at WHERE giveawayId = :giveawayId")
    suspend fun setCaptionVerified(giveawayId: Long, at: Instant)
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

    /** Keyset paging in (timestamp, id) order for the entry rebuild; blocking, for use inside its transaction. */
    @Query("SELECT * FROM comment WHERE giveawayId = :giveawayId ORDER BY timestamp, id LIMIT :limit")
    fun firstPage(giveawayId: Long, limit: Int): List<CommentEntity>

    @Query(
        "SELECT * FROM comment WHERE giveawayId = :giveawayId AND " +
            "(timestamp > :timestamp OR (timestamp = :timestamp AND id > :id)) ORDER BY timestamp, id LIMIT :limit",
    )
    fun pageAfter(giveawayId: Long, timestamp: Instant, id: String, limit: Int): List<CommentEntity>
}

@Dao
interface EntryDao {
    @Upsert
    suspend fun upsertAll(entries: List<EntryEntity>)

    @Query("SELECT COUNT(*) FROM entry WHERE giveawayId = :giveawayId AND isValid = 1")
    suspend fun validCount(giveawayId: Long): Int

    /** The organizer's choices: MANUAL exclusions with their note, and manual inclusions (valid, with a note). */
    @Query("SELECT * FROM entry WHERE giveawayId = :giveawayId AND manualNote IS NOT NULL")
    suspend fun manualDecisions(giveawayId: Long): List<EntryEntity>

    @Query("DELETE FROM entry WHERE giveawayId = :giveawayId")
    suspend fun deleteAll(giveawayId: Long)

    @Insert
    fun insertAllBlocking(entries: List<EntryEntity>)

    @Query("SELECT * FROM entry WHERE giveawayId = :giveawayId")
    suspend fun all(giveawayId: Long): List<EntryEntity>

    @Query("SELECT * FROM entry WHERE giveawayId = :giveawayId AND commentId = :commentId")
    suspend fun get(giveawayId: Long, commentId: String): EntryEntity?

    @Query("SELECT username FROM entry WHERE giveawayId = :giveawayId AND isValid = 1")
    suspend fun validUsernames(giveawayId: Long): List<String>

    @Query(
        "SELECT COUNT(*) AS total, COALESCE(SUM(isValid), 0) AS valid FROM entry WHERE giveawayId = :giveawayId",
    )
    fun observeCounts(giveawayId: Long): Flow<EntryCounts>

    /** S10 rows in comment order; [query] is a LIKE pattern body with %, _ and \ escaped, or empty. */
    @Query(
        """
        SELECT e.commentId, e.username, e.isValid, e.exclusionReason, e.manualNote, c.text, c.timestamp
        FROM entry e JOIN comment c ON c.giveawayId = e.giveawayId AND c.id = e.commentId
        WHERE e.giveawayId = :giveawayId AND (:valid IS NULL OR e.isValid = :valid)
            AND (:query = '' OR e.username LIKE '%' || :query || '%' ESCAPE '\'
                OR c.text LIKE '%' || :query || '%' ESCAPE '\')
        ORDER BY c.timestamp, c.id
        """,
    )
    fun rows(giveawayId: Long, valid: Boolean?, query: String): PagingSource<Int, EntryRow>
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

    /** The real draw's results, updated as winners are confirmed or replaced (S14). */
    @Query(
        "SELECT r.* FROM draw_result r JOIN draw d ON d.id = r.drawId " +
            "WHERE d.realGiveawayId = :giveawayId ORDER BY r.position",
    )
    fun observeRealResults(giveawayId: Long): Flow<List<DrawResultEntity>>

    @Update
    suspend fun updateResult(result: DrawResultEntity)

    /** A person's entry comment: their earliest valid one. [username] is lowercase, as in the canonical list. */
    @Query(
        "SELECT c.text FROM entry e JOIN comment c ON c.giveawayId = e.giveawayId AND c.id = e.commentId " +
            "WHERE e.giveawayId = :giveawayId AND e.isValid = 1 AND lower(e.username) = :username " +
            "ORDER BY c.timestamp, c.id LIMIT 1",
    )
    suspend fun entryComment(giveawayId: Long, username: String): String?
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

    @Query(
        "SELECT * FROM media_file WHERE giveawayId = :giveawayId AND kind = 'VIDEO' AND pendingDecision = 1 " +
            "ORDER BY createdAt DESC LIMIT 1",
    )
    fun observePendingVideo(giveawayId: Long): Flow<MediaFileEntity?>

    @Update
    suspend fun update(file: MediaFileEntity)

    @Delete
    suspend fun delete(file: MediaFileEntity)
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
