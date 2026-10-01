package app.giveaway.core.data.db

import androidx.room.TypeConverter
import java.time.Instant

/** Instants are stored as epoch milliseconds. Enums use Room's built-in by-name conversion. */
internal class Converters {
    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}
