@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.itzsuli.smartreminder.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itzsuli.smartreminder.data.Reminder
import com.itzsuli.smartreminder.data.ReminderKind
import java.time.LocalDate

@Composable
fun HomeScreen(vm: MainViewModel) {
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val resumeTick by vm.resumeTick.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val status = remember(resumeTick) { Permissions.status(context) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.onResumed() }

    val today = LocalDate.now()
    val deadlines = reminders.filter { it.kind == ReminderKind.DEADLINE }
    val routines = reminders.filter { it.kind == ReminderKind.ROUTINE }
    val activeDeadlines = deadlines.filter { !it.done }.sortedWith(
        compareBy<Reminder> { it.dueLocalDate == null }
            .thenBy { it.dueLocalDate }
            .thenBy { it.dueLocalTime == null }
            .thenBy { it.dueLocalTime }
            .thenByDescending { it.createdAt }
    )
    val doneDeadlines = deadlines.filter { it.done }.sortedByDescending { it.doneAt ?: 0L }
    val activeRoutines = routines.filter { !it.done }.sortedWith(
        compareBy<Reminder> { it.paused }.thenBy { it.isDoneForDay(today) }.thenBy { it.title.lowercase() }
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Smart Reminder", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = vm::openSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.startAdd() },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(countLabel("Deadlines", activeDeadlines.size)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(countLabel("Daily", activeRoutines.size)) })
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!status.allGood && !settings.setupCardDismissed) {
                    item(key = "setup") {
                        SetupCard(
                            status = status,
                            onNotifications = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    context.startActivity(Permissions.notificationSettingsIntent(context))
                                }
                            },
                            onOverlay = { context.startActivity(Permissions.overlayIntent(context)) },
                            onExact = { Permissions.exactAlarmIntent(context)?.let(context::startActivity) },
                            onBattery = { context.startActivity(Permissions.batteryIntent(context)) },
                            onDismiss = { vm.updateSettings { it.copy(setupCardDismissed = true) } },
                        )
                    }
                }
                if (tab == 0) {
                    if (activeDeadlines.isEmpty()) {
                        item(key = "empty-deadlines") {
                            EmptyState(
                                emoji = "📚",
                                title = "Nothing due right now",
                                body = "Tap Add and type it the way you'd text a friend, like \"HW english read page 32 till tuesday 10.10\".",
                            )
                        }
                    }
                    items(activeDeadlines, key = { it.id }) { reminder ->
                        DeadlineCard(reminder, onOpen = { vm.startEdit(reminder.id) }, onDone = { vm.setDone(reminder.id, true) })
                    }
                    if (doneDeadlines.isNotEmpty()) {
                        item(key = "done-header") {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                                SectionLabel("Done · ${doneDeadlines.size}")
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = vm::clearDone) { Text("Clear") }
                            }
                        }
                        items(doneDeadlines, key = { "done-" + it.id }) { reminder ->
                            DoneRow(reminder, onUndo = { vm.setDone(reminder.id, false) })
                        }
                    }
                } else {
                    if (activeRoutines.isEmpty()) {
                        item(key = "empty-routines") {
                            EmptyState(
                                emoji = "💊",
                                title = "No daily things yet",
                                body = "Supplements, water, stretching… Try \"vitamin D every morning\". Daily things use the gentle 3-second pop-up.",
                            )
                        }
                    }
                    items(activeRoutines, key = { it.id }) { reminder ->
                        RoutineCard(
                            reminder = reminder,
                            doneToday = reminder.isDoneForDay(today),
                            onOpen = { vm.startEdit(reminder.id) },
                            onDoneToday = { vm.setDoneForToday(reminder.id, it) },
                        )
                    }
                }
            }
        }
    }
}

private fun countLabel(name: String, count: Int) = if (count > 0) "$name · $count" else name

@Composable
private fun DeadlineCard(reminder: Reminder, onOpen: () -> Unit, onDone: () -> Unit) {
    Card(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 8.dp)) {
            Text(reminder.emoji, fontSize = 28.sp, modifier = Modifier.padding(end = 12.dp, top = 2.dp))
            Column(Modifier.weight(1f)) {
                Text(reminder.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (reminder.details.isNotBlank()) {
                    Text(
                        reminder.details,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 10.dp),
                ) {
                    reminder.dueLocalDate?.let { DuePill(it, reminder.dueLocalTime) }
                    IntensityPill(reminder.intensity)
                    if (reminder.paused) Pill("paused")
                }
            }
            FilledTonalIconButton(onClick = onDone) { Icon(Icons.Default.Check, contentDescription = "Mark done") }
        }
    }
}

@Composable
private fun RoutineCard(reminder: Reminder, doneToday: Boolean, onOpen: () -> Unit, onDoneToday: (Boolean) -> Unit) {
    Card(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (doneToday) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(reminder.emoji, fontSize = 28.sp, modifier = Modifier.padding(end = 12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    reminder.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (doneToday) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                if (reminder.details.isNotBlank()) {
                    Text(
                        reminder.details,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    IntensityPill(reminder.intensity)
                    if (reminder.dayParts.isNotEmpty()) Pill(reminder.dayParts.joinToString(" · ") { it.label })
                    reminder.dueLocalTime?.let { Pill("at ${it.hhmm()}") }
                    if (reminder.paused) Pill("paused", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                    if (doneToday) Pill("done today ✓", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            FilledIconToggleButton(checked = doneToday, onCheckedChange = onDoneToday) {
                Icon(Icons.Default.Check, contentDescription = "Done for today")
            }
        }
    }
}

@Composable
private fun DoneRow(reminder: Reminder, onUndo: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(reminder.emoji, fontSize = 20.sp, modifier = Modifier.padding(end = 12.dp))
            Text(
                reminder.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = TextDecoration.LineThrough,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onUndo) { Icon(Icons.Default.Refresh, contentDescription = "Undo") }
        }
    }
}

@Composable
private fun SetupCard(
    status: PermissionStatus,
    onNotifications: () -> Unit,
    onOverlay: () -> Unit,
    onExact: () -> Unit,
    onBattery: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Finish setup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Later") }
            }
            Text(
                "A few switches so reminders actually reach you:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            SetupRow("Pop-ups over other apps", "The 3-second card that slides in", status.overlay, onOverlay)
            SetupRow("Notifications", "Used when the screen is off", status.notifications, onNotifications)
            if (!status.exactAlarms) SetupRow("Exact timing", "Lets reminders fire on time", false, onExact)
            SetupRow("No battery limits", "Stops Android from silencing the app", status.batteryUnrestricted, onBattery)
        }
    }
}

@Composable
private fun SetupRow(title: String, subtitle: String, ok: Boolean, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(
            if (ok) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (ok) Color(0xFF16A34A) else MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!ok) FilledTonalButton(onClick = onFix) { Text("Allow") }
    }
}
