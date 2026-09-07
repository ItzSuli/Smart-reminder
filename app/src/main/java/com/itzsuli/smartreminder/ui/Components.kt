@file:OptIn(ExperimentalMaterial3Api::class)

package com.itzsuli.smartreminder.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itzsuli.smartreminder.data.Intensity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// ---- small building blocks -------------------------------------------------------------------

@Composable
fun Pill(
    text: String,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    icon: ImageVector? = null,
    bold: Boolean = false,
) {
    Surface(shape = RoundedCornerShape(50), color = container) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
                Spacer(Modifier.size(4.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = content,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

@Composable
fun IntensityPill(intensity: Intensity) {
    val scheme = MaterialTheme.colorScheme
    when (intensity) {
        Intensity.MEH -> Pill(intensity.label, scheme.surfaceVariant, scheme.onSurfaceVariant)
        Intensity.WHATEVER -> Pill(intensity.label, scheme.secondaryContainer, scheme.onSecondaryContainer)
        Intensity.A_LOT -> Pill(intensity.label, scheme.primary, scheme.onPrimary, bold = true)
    }
}

enum class Urgency { OVERDUE, TODAY, SOON, LATER }

data class DueLabel(val text: String, val urgency: Urgency)

fun dueLabel(date: LocalDate, time: LocalTime?, today: LocalDate = LocalDate.now()): DueLabel {
    val days = ChronoUnit.DAYS.between(today, date)
    val short = date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
    val timeText = time?.let { " " + it.format(DateTimeFormatter.ofPattern("HH:mm")) } ?: ""
    return when {
        days < 0 -> DueLabel("Overdue · $short", Urgency.OVERDUE)
        days == 0L -> DueLabel("Today$timeText", Urgency.TODAY)
        days == 1L -> DueLabel("Tomorrow$timeText", Urgency.SOON)
        days < 7 -> DueLabel("$short$timeText · in $days days", Urgency.SOON)
        days < 30 -> DueLabel("$short · in ${(days / 7)} wk", Urgency.LATER)
        else -> DueLabel(short, Urgency.LATER)
    }
}

@Composable
fun DuePill(date: LocalDate, time: LocalTime?) {
    val label = dueLabel(date, time)
    val scheme = MaterialTheme.colorScheme
    when (label.urgency) {
        Urgency.OVERDUE -> Pill(label.text, scheme.errorContainer, scheme.onErrorContainer, bold = true)
        Urgency.TODAY -> Pill(label.text, scheme.tertiaryContainer, scheme.onTertiaryContainer, bold = true)
        Urgency.SOON -> Pill(label.text, scheme.primaryContainer, scheme.onPrimaryContainer)
        Urgency.LATER -> Pill(label.text, scheme.surfaceVariant, scheme.onSurfaceVariant)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 4.dp, bottom = 6.dp),
    )
}

@Composable
fun EmptyState(emoji: String, title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun IntensitySelector(selected: Intensity, onSelect: (Intensity) -> Unit, modifier: Modifier = Modifier) {
    val options = Intensity.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = {
                    Text(
                        option.label,
                        fontWeight = if (option == Intensity.A_LOT) FontWeight.Bold else FontWeight.Normal,
                    )
                },
            )
        }
    }
}

// ---- pickers ---------------------------------------------------------------------------------

@Composable
fun DatePickerSheet(initial: LocalDate?, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

@Composable
fun TimePickerSheet(initial: LocalTime?, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    val start = initial ?: LocalTime.of(9, 0)
    val state = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = state) },
    )
}

fun LocalTime.hhmm(): String = format(DateTimeFormatter.ofPattern("HH:mm"))
fun LocalDate.pretty(): String = format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault()))
fun minutesToHhmm(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
