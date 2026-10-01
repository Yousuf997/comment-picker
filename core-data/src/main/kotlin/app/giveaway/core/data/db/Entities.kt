// Rows are identified by their keys and never compared with equals, so arrays in data classes are fine here.
@file:Suppress("ArrayInDataClass")

package app.giveaway.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.giveaway.draw.ExclusionReason
import app.giveaway.draw.Role
import java.time.Instant

// Room schema v1 (plan section 4). Only what the draw and certificate need is stored: usernames, comment text and
// timestamps, never profile photos (spec: Data model). Columns added beyond the spec are noted.

/** The connected Instagram account. One row. The token is encrypted with the TOKEN Keystore key. */
@Entity(tableName = "account")
data class AccountEntity(
    @PrimaryKey val igUserId: String,
    val username: String,
    val encryptedToken: ByteArray,
    val tokenExpiresAt: Instant,
    /** Added: Instagram rejected the token (error 190); S4 asks the user to sign in again. */
    val tokenRevoked: Boolean = false,
)

@Entity(
    tableName = "giveaway",
    indices = [Index("status"), Index("autoDeleteAt")],
)
data class GiveawayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val igMediaId: String,
    val mediaType: String,
    val thumbnailUrl: String?,
    val status: GiveawayStatus,
    val createdAt: Instant,
    val closesAt: Instant,
    val autoDeleteAt: Instant?,
    /** Added: the giveaway account's handle, for the OWN_ACCOUNT rule and the certificate. */
    val ownerUsername: String,
)

/** One row per giveaway. Frozen once the giveaway is COMMITTED (plan A9). */
@Entity(
    tableName = "rules",
    foreignKeys = [ForeignKey(GiveawayEntity::class, ["id"], ["giveawayId"], onDelete = ForeignKey.CASCADE)],
)
data class RulesEntity(
    @PrimaryKey val giveawayId: Long,
    val minMentions: Int,
    val requiredHashtag: String?,
    val keyword: String?,
    val onePerPerson: Boolean,
    val excludePastWinners: Boolean,
    val excludeBlocklist: Boolean,
    val winnersCount: Int,
    val alternatesCount: Int,
)

/** The committed seed stays encrypted with the SEED Keystore key until the real draw. */
@Entity(
    tableName = "commitment",
    foreignKeys = [ForeignKey(GiveawayEntity::class, ["id"], ["giveawayId"], onDelete = ForeignKey.CASCADE)],
)
data class CommitmentEntity(
    @PrimaryKey val giveawayId: Long,
    val encryptedSeed: ByteArray,
    val commitHash: String,
    val createdAt: Instant,
    val captionVerifiedAt: Instant?,
)

/** Makes imports resumable after errors, rate limits or the app being killed. */
@Entity(
    tableName = "import_state",
    foreignKeys = [ForeignKey(GiveawayEntity::class, ["id"], ["giveawayId"], onDelete = ForeignKey.CASCADE)],
)
data class ImportStateEntity(
    @PrimaryKey val giveawayId: Long,
    val nextCursor: String?,
    val pagesFetched: Int,
    val commentsFetched: Int,
    val expectedCount: Int,
    val lastError: String?,
    val updatedAt: Instant,
    /** Added: consecutive failed retries; S9 allows a partial import after 3. */
    val failedRetries: Int,
    /** Added: the user accepted a partial import; printed on the certificate. */
    val acceptedPartial: Boolean,
    /** Added: replies counted (not stored) so the total can be compared with the post's comment count (A14). */
    val repliesCounted: Int,
)

/** Raw imported top-level comments. */
@Entity(
    tableName = "comment",
    primaryKeys = ["giveawayId", "id"],
    indices = [Index("giveawayId", "timestamp", "id")],
    foreignKeys = [ForeignKey(GiveawayEntity::class, ["id"], ["giveawayId"], onDelete = ForeignKey.CASCADE)],
)
data class CommentEntity(
    val giveawayId: Long,
    /** Instagram comment ID. */
    val id: String,
    val username: String,
    val text: String,
    val timestamp: Instant,
)

/** Result of filtering one comment. */
@Entity(
    tableName = "entry",
    primaryKeys = ["giveawayId", "commentId"],
    indices = [Index("giveawayId", "isValid"), Index("giveawayId", "username")],
    foreignKeys = [ForeignKey(GiveawayEntity::class, ["id"], ["giveawayId"], onDelete = ForeignKey.CASCADE)],
)
data class EntryEntity(
    val giveawayId: Long,
    val commentId: String,
    val username: String,
    val isValid: Boolean,
    val exclusionReason: ExclusionReason?,
    /** Added: the organizer's reason for a MANUAL exclusion, printed on the certificate. */
    val manualNote: String?,
)

/**
 * A draw record: one real draw per giveaway, any number of test draws. [realGiveawayId] equals [giveawayId] for the
 * real draw and is null for test draws; its unique index enforces "one real draw" in the database itself.
 */
@Entity(
    tableName = "draw",
    indices = [Index("giveawayId"), Index("realGiveawayId", unique = true)],
    foreignKeys = [ForeignKey(GiveawayEntity::class, ["id"], ["giveawayId"], onDelete = ForeignKey.CASCADE)],
)
data class DrawEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val giveawayId: Long,
    val isTest: Boolean,
    val realGiveawayId: Long?,
    val seed: ByteArray,
    val entryListHash: String,
    val drawnAt: Instant,
    val algorithmVersion: String,
    val deviceSignature: ByteArray,
    /** Added: the signer's public key, so the certificate verifies after a restore on another phone. */
    val signerPublicKey: ByteArray,
    val signerFingerprint: String,
    val captionCheck: CaptionCheck,
    val integrityVerified: Boolean,
    val partialImport: Boolean,
    val entryCount: Int,
)

@Entity(
    tableName = "draw_result",
    primaryKeys = ["drawId", "position"],
    foreignKeys = [ForeignKey(DrawEntity::class, ["id"], ["drawId"], onDelete = ForeignKey.CASCADE)],
)
data class DrawResultEntity(
    val drawId: Long,
    val position: Int,
    val username: String,
    val role: Role,
    val confirmationStatus: ConfirmationStatus,
    /** Added: why this winner was replaced (e.g. failed the follow check), printed on the certificate. */
    val replacedReason: String?,
    /** Added: the alternate's position that took this place. */
    val replacedByPosition: Int?,
)

/** Shared across giveaways. */
@Entity(tableName = "blocklist")
data class BlocklistEntity(
    @PrimaryKey val username: String,
    val addedAt: Instant,
    val note: String?,
)

/** Fed by confirmed winners. No foreign key: past winners outlive auto-deleted giveaways. */
@Entity(
    tableName = "past_winner",
    primaryKeys = ["username", "giveawayId"],
    indices = [Index("username")],
)
data class PastWinnerEntity(
    val username: String,
    val giveawayId: Long,
    val wonAt: Instant,
)

@Entity(
    tableName = "media_file",
    indices = [Index("giveawayId")],
    foreignKeys = [ForeignKey(GiveawayEntity::class, ["id"], ["giveawayId"], onDelete = ForeignKey.CASCADE)],
)
data class MediaFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val giveawayId: Long,
    val kind: MediaKind,
    val uri: String,
    val savedToGallery: Boolean,
    val createdAt: Instant,
    /** Added: the S13 save/don't-save choice is still open (asked again when the giveaway reopens). */
    val pendingDecision: Boolean,
)

/** One row (id = 1). Defaults from the spec. */
@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val appLockEnabled: Boolean = false,
    /** Added: the unlock method; null when app lock is off. */
    val appLockMethod: AppLockMethod? = null,
    val lockAfterSeconds: Int = 60,
    val blockScreenshots: Boolean = false,
    val autoDeleteDays: Int = 90,
    val recordDrawsByDefault: Boolean = true,
    /** BCP 47 tag, or null to follow the system language. */
    val language: String? = null,
    /** Added: drives the backup reminder on S4. */
    val lastBackupAt: Instant? = null,
    /** Added: whether S1-S3 have been completed. */
    val onboardingComplete: Boolean = false,
) {
    companion object {
        const val SINGLE_ROW_ID = 1
    }
}
