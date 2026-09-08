@file:OptIn(ExperimentalMaterial3Api::class)

package com.itzsuli.smartreminder.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import com.itzsuli.smartreminder.data.Intensity
import com.itzsuli.smartreminder.data.ReminderKind
import com.itzsuli.smartreminder.io.CalendarSource

@Composable
fun ImportScreen(vm: MainViewModel) {
    val events by vm.importEvents.collectAsStateWithLifecycle()
    val busy by vm.importBusy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selected by remember { mutableStateOf(setOf<String>()) }
    var intensity by remember { mutableStateOf(Intensity.WHATEVER) }
    var asKind by remember { mutableStateOf(ReminderKind.EVENT) }
    var hasPermission by remember { mutableStateOf(CalendarSource.hasPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) vm.refreshCalendar()
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::loadIcs)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Import deadlines") },
                navigationIcon = {
                    IconButton(onClick = vm::goHome) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (events.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        SectionLabel("Add them as")
                        val kinds = listOf(ReminderKind.EVENT to "Events (just show up)", ReminderKind.DEADLINE to "Deadlines (nag me)")
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            kinds.forEachIndexed { index, (kind, label) ->
                                SegmentedButton(
                                    selected = asKind == kind,
                                    onClick = { asKind = kind },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = kinds.size),
                                    label = { Text(label, maxLines = 1) },
                                )
                            }
                        }
                        if (asKind == ReminderKind.DEADLINE) {
                            Spacer(Modifier.height(8.dp))
                            IntensitySelector(intensity, onSelect = { intensity = it })
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { vm.importSelected(selected, intensity, asKind) },
                            enabled = selected.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                        ) {
                            val noun = if (asKind == ReminderKind.EVENT) "event" else "deadline"
                            Text(if (selected.size == 1) "Add 1 $noun" else "Add ${selected.size} ${noun}s")
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Pull exams, hand-ins and appointments from your phone's calendar (next 30 days) or from a timetable file (.ics). Each one becomes a deadline you can edit afterwards.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (hasPermission) {
                                FilledTonalButton(onClick = vm::refreshCalendar, enabled = !busy) { Text("Refresh calendar") }
                            } else {
                                FilledTonalButton(onClick = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) }) { Text("Allow calendar") }
                            }
                            OutlinedButton(onClick = { fileLauncher.launch(arrayOf("text/calendar", "application/octet-stream", "*/*")) }) {
                                Text("Open .ics file")
                            }
                            if (busy) CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
            if (events.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("${events.size} upcoming events")
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { selected = if (selected.size == events.size) emptySet() else events.map { it.key }.toSet() }) {
                            Text(if (selected.size == events.size) "Select none" else "Select all")
                        }
                    }
                }
            } else if (hasPermission && !busy) {
                item {
                    EmptyState("📅", "Nothing in the next 30 days", "Your calendar is empty for the coming month. You can still open a timetable file.")
                }
            }
            items(events, key = { it.key }) { event ->
                val checked = event.key in selected
                Card(
                    onClick = { selected = if (checked) selected - event.key else selected + event.key },
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)) {
                        Checkbox(checked = checked, onCheckedChange = { on -> selected = if (on) selected + event.key else selected - event.key })
                        Column(Modifier.weight(1f)) {
                            Text(event.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                event.date.pretty() + (event.time?.let { " · " + it.hhmm() } ?: "") + " · " + event.source,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
