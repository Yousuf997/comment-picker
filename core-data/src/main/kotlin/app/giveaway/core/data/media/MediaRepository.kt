package app.giveaway.core.data.media

import android.content.Context
import app.giveaway.core.data.db.GiveawayDatabase
import app.giveaway.core.data.db.MediaFileEntity
import app.giveaway.core.data.db.MediaKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
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

    /** The recording still waiting for the S13 choice, if any; S14 shows the sheet until it is made. */
    fun observePendingVideo(giveawayId: Long): Flow<MediaFileEntity?> = files.observePendingVideo(giveawayId)

    /** "Save to gallery": the private copy is removed once the gallery has it (spec: requirement 7). */
    suspend fun savedToGallery(video: MediaFileEntity, galleryUri: String) {
        File(video.uri).delete()
        files.update(video.copy(uri = galleryUri, savedToGallery = true, pendingDecision = false))
    }

    /** "Don't save": the private file is deleted at once (spec: requirement 8). */
    suspend fun discard(video: MediaFileEntity) {
        File(video.uri).delete()
        files.delete(video)
    }

    /** Where a giveaway's certificate is written: app-private, shared only through FileProvider (spec: S15). */
    fun certificateFile(giveawayId: Long, kind: MediaKind): File {
        val extension = if (kind == MediaKind.CERTIFICATE_PDF) "pdf" else "png"
        return File(context.filesDir, "$CERTIFICATES/certificate-$giveawayId.$extension").apply { parentFile?.mkdirs() }
    }

    /** Records a certificate file once; making it again overwrites the same file (spec: media_file). */
    suspend fun certificateMade(giveawayId: Long, kind: MediaKind, file: File) {
        if (files.forGiveaway(giveawayId).any { it.kind == kind }) return
        files.insert(
            MediaFileEntity(
                giveawayId = giveawayId,
                kind = kind,
                uri = file.absolutePath,
                savedToGallery = false,
                createdAt = clock.instant(),
                pendingDecision = false,
            ),
        )
    }

    /** Deletes every private recording and certificate file (a restore replaced the data they belonged to). */
    fun deleteAllFiles() {
        listOf(VIDEOS, CERTIFICATES).forEach { File(context.filesDir, it).deleteRecursively() }
    }

    private companion object {
        const val VIDEOS = "videos"

        /** Shared with Instagram through FileProvider; nothing else in filesDir is exposed. */
        const val CERTIFICATES = "certificates"
    }
}
