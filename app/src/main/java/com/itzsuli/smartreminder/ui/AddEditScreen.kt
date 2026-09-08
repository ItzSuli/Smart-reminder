@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.itzsuli.smartreminder.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itzsuli.smartreminder.data.AiEngine
import com.itzsuli.smartreminder.data.DayPart
import com.itzsuli.smartreminder.data.ParseSource
import com.itzsuli.smartreminder.data.ReminderKind

@Composable
fun AddEditScreen(vm: MainViewModel) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim().orEmpty()
        if (spoken.isNotEmpty()) vm.updateDraft { it.copy(input = (it.input.trim() + " " + spoken).trim()) }
    }
    fun speak() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say your reminder")
        try {
            speechLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No voice input available on this phone.", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(draft.launchVoice) {
        if (draft.launchVoice) {
            vm.updateDraft { it.copy(launchVoice = false) }
            speak()
        }
    }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (draft.isEdit) "Edit reminder" else "New reminder") },
                navigationIcon = {
                    IconButton(onClick = vm::goHome) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (draft.isEdit) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (draft.previewReady) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Button(
                        onClick = vm::saveDraft,
                        enabled = draft.canSave,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(52.dp),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (draft.isEdit) "Save changes" else "Add reminder")
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .imePadding(),
        ) {
            OutlinedTextField(
                value = draft.input,
                onValueChange = { v -> vm.updateDraft { it.copy(input = v) } },
                label = { Text("What's up?") },
                placeholder = { Text("HW english read page 32 till tuesday 10.10") },
                minLines = 2,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                when {
                    settings.aiEngine == AiEngine.OFFLINE -> "Type it fast and messy. The app tidies it up."
                    settings.aiEngine == AiEngine.AUTO && !settings.hasGeminiKey && !settings.hasClaudeKey ->
                        "Type it fast and messy, or tap the mic. Add a free Gemini key in Settings for smarter clean-up."
                    else -> "Type it fast and messy, or tap the mic. AI tidies it up."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { focus.clearFocus(); vm.makeClear() },
                    enabled = draft.input.isNotBlank() && !draft.loading,
                    modifier = Modifier.weight(1f).height(50.dp),
                ) {
                    if (draft.loading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(10.dp))
                        Text("Thinking…")
                    } else {
                        Text(if (draft.previewReady) "✨ Clean it up again" else "✨ Make it clear")
                    }
                }
                FilledTonalButton(onClick = { speak() }, modifier = Modifier.height(50.dp)) { Text("🎤") }
            }
            if (draft.previewReady) {
                Spacer(Modifier.height(16.dp))
                PreviewEditor(draft, vm, onPickDate = { showDate = true }, onPickTime = { showTime = true })
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDate) {
        DatePickerSheet(initial = draft.dueDate, onDismiss = { showDate = false }) { d -> vm.updateDraft { it.copy(dueDate = d) } }
    }
    if (showTime) {
        TimePickerSheet(initial = draft.dueTime, onDismiss = { showTime = false }) { t -> vm.updateDraft { it.copy(dueTime = t) } }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this reminder?") },
            text = { Text("It stops reminding you immediately. This can't be undone.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteDraft() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PreviewEditor(draft: Draft, vm: MainViewModel, onPickDate: () -> Unit, onPickTime: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Preview", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                when (val src = draft.source) {
                    null -> {}
                    ParseSource.LOCAL -> Pill(src.label)
                    else -> Pill(src.label, scheme.secondaryContainer, scheme.onSecondaryContainer)
                }
            }
            draft.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.tertiary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.emoji,
                    onValueChange = { v -> vm.updateDraft { it.copy(emoji = v.take(4)) } },
                    label = { Text("Icon") },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 22.sp, textAlign = TextAlign.Center),
                    modifier = Modifier.width(80.dp),
                )
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { v -> vm.updateDraft { it.copy(title = v) } },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = draft.details,
                onValueChange = { v -> vm.updateDraft { it.copy(details = v) } },
                label = { Text("Details") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel("Type")
            val kinds = listOf(ReminderKind.DEADLINE to "Deadline", ReminderKind.ROUTINE to "Daily", ReminderKind.EVENT to "Event")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                kinds.forEachIndexed { index, (kind, label) ->
                    SegmentedButton(
                        selected = draft.kind == kind,
                        onClick = { vm.updateDraft { it.copy(kind = kind) } },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = kinds.size),
                        label = { Text(label) },
                    )
                }
            }

            if (draft.kind == ReminderKind.EVENT) {
                SectionLabel("When")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPickDate, modifier = Modifier.weight(1.4f)) {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(draft.dueDate?.pretty() ?: "Pick a date", maxLines = 1)
                    }
                    OutlinedButton(onClick = onPickTime, modifier = Modifier.weight(1f)) {
                        Text(draft.dueTime?.hhmm() ?: "Time", maxLines = 1)
                    }
                }
                if (draft.dueDate != null || draft.dueTime != null) {
                    Row {
                        if (draft.dueDate != null) TextButton(onClick = { vm.updateDraft { it.copy(dueDate = null) } }) { Text("Clear date") }
                        if (draft.dueTime != null) TextButton(onClick = { vm.updateDraft { it.copy(dueTime = null) } }) { Text("Clear time") }
                    }
                }
                if (draft.dueDate == null) {
                    Text("An event needs a date, otherwise there's nothing to look forward to.", style = MaterialTheme.typography.bodySmall, color = scheme.error)
                }
                SectionLabel("Heads-up")
                val leads = listOf(1 to "Day before", 3 to "3 days before", 7 to "A week before")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    leads.forEachIndexed { index, (days, label) ->
                        SegmentedButton(
                            selected = draft.leadDays == days,
                            onClick = { vm.updateDraft { it.copy(leadDays = days) } },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = leads.size),
                            label = { Text(label, maxLines = 1) },
                        )
                    }
                }
                Text(
                    "Events don't nag. You get one heads-up on that day, one the day before, one on the morning of, and a final call an hour before if it has a time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            } else if (draft.kind == ReminderKind.DEADLINE) {
                SectionLabel("Due")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPickDate, modifier = Modifier.weight(1.4f)) {
                        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(draft.dueDate?.pretty() ?: "Pick a date", maxLines = 1)
                    }
                    OutlinedButton(onClick = onPickTime, modifier = Modifier.weight(1f)) {
                        Text(draft.dueTime?.hhmm() ?: "Time", maxLines = 1)
                    }
                }
                if (draft.dueDate != null || draft.dueTime != null) {
                    Row {
                        if (draft.dueDate != null) TextButton(onClick = { vm.updateDraft { it.copy(dueDate = null) } }) { Text("Clear date") }
                        if (draft.dueTime != null) TextButton(onClick = { vm.updateDraft { it.copy(dueTime = null) } }) { Text("Clear time") }
                    }
                }
                if (draft.dueDate == null) {
                    Text("No date is fine too. It just won't get louder over time.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            } else {
                SectionLabel("When in the day")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayPart.entries.forEach { part ->
                        val selected = part in draft.dayParts
                        FilterChip(
                            selected = selected,
                            onClick = {
                                vm.updateDraft {
                                    it.copy(dayParts = if (selected) it.dayParts - part else (it.dayParts + part).sortedBy { p -> p.ordinal })
                                }
                            },
                            label = { Text(part.label) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                        )
                    }
                }
                Text(
                    if (draft.dayParts.isEmpty()) "Nothing picked = any time during your active hours." else "Reminders land inside these parts of the day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onPickTime) {
                        Text(draft.dueTime?.let { "Always at ${it.hhmm()}" } ?: "Fixed time (optional)")
                    }
                    if (draft.dueTime != null) TextButton(onClick = { vm.updateDraft { it.copy(dueTime = null) } }) { Text("Clear") }
                }
            }

            if (draft.kind != ReminderKind.EVENT) {
                SectionLabel("How much should it nag?")
                IntensitySelector(draft.intensity, onSelect = { i -> vm.updateDraft { it.copy(intensity = i) } })
                Text(
                    if (draft.kind == ReminderKind.DEADLINE) "Whatever you pick, it gets louder by itself as the deadline gets closer. You won't know exactly when the next nudge comes."
                    else "Daily things are spread over the day and never fire more than a few times, so even A LOT stays calm.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}
