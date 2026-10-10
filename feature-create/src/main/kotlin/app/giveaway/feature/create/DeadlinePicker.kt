package app.giveaway.feature.create

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Picks when entries close: a date (today or later), then a time, in the phone's time zone. The S7 validation still
 * rejects a time that has already passed today. "Now" closes entries as the rules are saved, for an instant draw.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeadlinePicker(initial: Instant, onPicked: (Instant) -> Unit, onNow: () -> Unit, onDismiss: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val initialLocal = initial.atZone(zone)
    var date by remember { mutableStateOf<LocalDate?>(null) }
    val pickedDate = date
    if (pickedDate == null) {
        // The Material date picker works in UTC midnights; convert at the edges only.
        val today = LocalDate.now(zone)
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = initialLocal.toLocalDate().utcMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= today.utcMillis()

                override fun isSelectableYear(year: Int) = year >= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        onNow()
                        onDismiss()
                    }) { Text(stringResource(R.string.rules_picker_now)) }
                    TextButton(
                        onClick = { date = dateState.selectedDateMillis?.let(::utcMillisToDate) },
                        enabled = dateState.selectedDateMillis != null,
                    ) { Text(stringResource(R.string.rules_picker_next)) }
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.rules_picker_cancel)) } },
        ) { DatePicker(state = dateState) }
    } else {
        val timeState = rememberTimePickerState(
            initialHour = initialLocal.hour,
            initialMinute = initialLocal.minute,
            is24Hour = DateFormat.is24HourFormat(LocalContext.current),
        )
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.rules_closes)) },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    onPicked(pickedDate.atTime(LocalTime.of(timeState.hour, timeState.minute)).atZone(zone).toInstant())
                    onDismiss()
                }) { Text(stringResource(R.string.rules_picker_done)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.rules_picker_cancel)) } },
        )
    }
}

private fun LocalDate.utcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun utcMillisToDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).toLocalDate()
