package app.giveaway.core.designsystem

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.format.FormatStyle

/**
 * A date in the app's language, with that language's digits (spec: Localization). java.time keeps ASCII digits
 * unless asked, which would mix digit systems next to localized counts in Arabic.
 */
@Composable
fun formatDate(instant: Instant, style: FormatStyle = FormatStyle.MEDIUM): String {
    val locale = LocalConfiguration.current.locales[0]
    return DateTimeFormatter.ofLocalizedDate(style)
        .withLocale(locale)
        .withDecimalStyle(DecimalStyle.of(locale))
        .withZone(ZoneId.systemDefault())
        .format(instant)
}

/** A whole number in the app's language, with grouping ("2,400", "٢٬٤٠٠") unless [grouping] is off (sizes, codes). */
@Composable
fun formatCount(count: Int, grouping: Boolean = true): String =
    NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
        .apply { isGroupingUsed = grouping }
        .format(count)

/**
 * A span of time in the app's language by its two largest units, e.g. "2 days, 5 hr" or "45 min", with ICU's plural
 * forms and digits. Less than a minute shows as one minute.
 */
@Composable
fun formatDuration(duration: Duration): String {
    val minutes = duration.toMinutes().coerceAtLeast(1)
    val days = minutes / MINUTES_PER_DAY
    val hours = minutes % MINUTES_PER_DAY / MINUTES_PER_HOUR
    val measures = when {
        days > 0 -> listOf(Measure(days, MeasureUnit.DAY), Measure(hours, MeasureUnit.HOUR))
        hours > 0 -> listOf(Measure(hours, MeasureUnit.HOUR), Measure(minutes % MINUTES_PER_HOUR, MeasureUnit.MINUTE))
        else -> listOf(Measure(minutes, MeasureUnit.MINUTE))
    }.filter { it.number.toLong() > 0 }
    return MeasureFormat.getInstance(LocalConfiguration.current.locales[0], MeasureFormat.FormatWidth.SHORT)
        .formatMeasures(*measures.toTypedArray())
}

private const val MINUTES_PER_HOUR = 60L
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

/** A date and time in the app's language and the phone's time zone, e.g. for when entries close. */
@Composable
fun formatDateTime(instant: Instant): String {
    val locale = LocalConfiguration.current.locales[0]
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale)
        .withDecimalStyle(DecimalStyle.of(locale))
        .withZone(ZoneId.systemDefault())
        .format(instant)
}
