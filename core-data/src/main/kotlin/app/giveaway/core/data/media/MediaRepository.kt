package app.giveaway.core.data.media

import android.content.Context
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.db.MediaFileEntity
import app.giveaway.core.data.db.MediaKind
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Clock
import javax.inject.Inject

/**
 * The draw video and certificate files of a giveaway (spec: media_file). Recordings are written to app-private
 * storage first (spec: Draw recording, requirement 4); S13 then saves or deletes them.
 */
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: GiveawayDatabase,
    private val clock: Clock,
) {
    private val files get() = db.mediaFileDao()

    /** Where a giveaway's draw is recorded: private to the app, never in the gallery until the user says so. */
    fun recordingFile(giveawayId: Long): File =
        File(context.filesDir, "$VIDEOS/draw-$giveawayId.mp4").apply { parentFile?.mkdirs() }

    /** A finished recording waiting for the S13 choice (asked again if the app closes first; spec requirement 8). */
    suspend fun recordingSaved(giveawayId: Long, file: File): Long = files.insert(
        MediaFileEntity(
            giveawayId = giveawayId,
            kind = MediaKind.VIDEO,
            uri = file.absolutePath,
            savedToGallery = false,
            createdAt = clock.instant(),
            pendingDecision = true,
        ),
    )

    suspend fun videos(giveawayId: Long): List<MediaFileEntity> =
        files.forGiveaway(giveawayId).filter { it.kind == MediaKind.VIDEO }

    private companion object {
        const val VIDEOS = "videos"
    }
}
