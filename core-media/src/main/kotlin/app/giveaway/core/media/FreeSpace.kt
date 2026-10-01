package app.giveaway.core.media

import android.content.Context
import android.os.StatFs
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/** Free space where recordings are written (app-private storage). Faked in tests. */
fun interface FreeSpace {
    fun availableBytes(): Long
}

internal class StatFsFreeSpace @Inject constructor(@ApplicationContext private val context: Context) : FreeSpace {
    override fun availableBytes(): Long = StatFs(context.filesDir.absolutePath).availableBytes
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MediaModule {
    @Binds
    internal abstract fun freeSpace(impl: StatFsFreeSpace): FreeSpace
}

/**
 * Whether a draw with this many picks can be recorded (spec: warn before the draw when storage is short). Assumes
 * Full HD, the largest size, and keeps a safety margin for the rest of the phone.
 */
object RecordingSpace {
    private const val MARGIN_BYTES = 50L * 1024 * 1024

    fun neededBytes(winners: Int, alternates: Int): Long =
        DrawRecorder.estimatedBytes(DrawTimeline.estimatedDurationMs(winners, alternates), VideoSize.FULL_HD) +
            MARGIN_BYTES

    fun enough(freeSpace: FreeSpace, winners: Int, alternates: Int): Boolean =
        freeSpace.availableBytes() >= neededBytes(winners, alternates)
}
