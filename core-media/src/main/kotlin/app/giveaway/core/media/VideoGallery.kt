package app.giveaway.core.media

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** Copies a finished recording into the gallery (spec: Draw recording, requirement 7). Faked in tests. */
interface VideoGallery {
    /** Saves [source] as Movies/[album]/[displayName] and returns its content URI. */
    suspend fun save(source: File, album: String, displayName: String): Uri

    /** Length and frame size of a recording, for the S13 preview line. */
    suspend fun describe(file: File): VideoInfo?
}

data class VideoInfo(val durationMs: Long, val width: Int, val height: Int)

/**
 * MediaStore on Android 10 and later needs no permission; Android 8 and 9 need WRITE_EXTERNAL_STORAGE, which the
 * manifest asks for only up to API 28 and the screen requests just before saving.
 */
internal class MediaStoreVideoGallery @Inject constructor(
    @ApplicationContext private val context: Context,
) : VideoGallery {

    override suspend fun save(source: File, album: String, displayName: String): Uri = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, MIME_MP4)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$album")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION") // The only way to choose the folder before Android 10.
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), album)
                dir.mkdirs()
                @Suppress("DEPRECATION")
                put(MediaStore.Video.Media.DATA, File(dir, displayName).absolutePath)
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("The gallery refused the video")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("Couldn't open the gallery file")
            out.use { stream -> source.inputStream().use { it.copyTo(stream) } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            }
            uri
        } catch (error: IOException) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    override suspend fun describe(file: File): VideoInfo? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            fun int(key: Int) = retriever.extractMetadata(key)?.toLongOrNull()
            VideoInfo(
                durationMs = int(MediaMetadataRetriever.METADATA_KEY_DURATION) ?: 0,
                width = int(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toInt() ?: 0,
                height = int(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toInt() ?: 0,
            )
        } catch (expected: IllegalArgumentException) {
            null
        } finally {
            retriever.release()
        }
    }

    private companion object {
        const val MIME_MP4 = "video/mp4"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class GalleryModule {
    @Binds
    internal abstract fun videoGallery(impl: MediaStoreVideoGallery): VideoGallery
}
