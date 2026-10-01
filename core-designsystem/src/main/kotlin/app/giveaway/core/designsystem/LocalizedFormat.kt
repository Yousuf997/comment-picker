package app.giveaway.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.text.NumberFormat
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
