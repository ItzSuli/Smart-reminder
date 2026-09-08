@file:OptIn(ExperimentalMaterial3Api::class)

package com.itzsuli.smartreminder.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.itzsuli.smartreminder.BuildConfig
import com.itzsuli.smartreminder.ai.NanoEngine
import com.itzsuli.smartreminder.data.AiEngine
import com.itzsuli.smartreminder.data.DateOrder
import com.itzsuli.smartreminder.data.Delivery
import com.itzsuli.smartreminder.data.PopupPosition
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val resumeTick by vm.resumeTick.collectAsStateWithLifecycle()
    val nanoStatus by vm.nanoStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status = remember(resumeTick) { Permissions.status(context) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.onResumed() }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::exportBackup)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importBackup)
    }
    val isSamsung = remember { Build.MANUFACTURER.equals("samsung", ignoreCase = true) }

    LaunchedEffect(Unit) { if (nanoStatus is NanoEngine.Status.Unknown) vm.refreshNano() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = vm::goHome) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AiCard(vm, settings.aiEngine, settings.geminiKey, settings.geminiModel, settings.claudeKey, settings.claudeModel, nanoStatus)

            SettingsCard("How reminders show up") {
                Text(
                    "Everything is a pop-up by default. Only pick a notification option if you really want notifications.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DeliveryPicker("Deadlines", settings.deadlineDelivery) { d -> vm.updateSettings { it.copy(deadlineDelivery = d) } }
                HorizontalDivider()
                DeliveryPicker("Daily things", settings.routineDelivery) { d -> vm.updateSettings { it.copy(routineDelivery = d) } }
                HorizontalDivider()
                Text("Pop-up stays for ${settings.popupSeconds} seconds", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = settings.popupSeconds.toFloat(),
                    onValueChange = { v -> vm.updateSettings { it.copy(popupSeconds = v.roundToInt().coerceIn(2, 8)) } },
                    valueRange = 2f..8f,
                    steps = 5,
                )
                Text("Pop-up position", style = MaterialTheme.typography.bodyMedium)
                val positions = PopupPosition.entries
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    positions.forEachIndexed { index, pos ->
                        SegmentedButton(
                            selected = settings.popupPosition == pos,
                            onClick = { vm.updateSettings { it.copy(popupPosition = pos) } },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = positions.size),
                            label = { Text(pos.label, maxLines = 1) },
                        )
                    }
                }
                FilledTonalButton(onClick = { vm.showTestPopup(context) }) { Text("Show a test pop-up") }
            }

            SettingsCard("Active hours") {
                Text(
                    "Reminders only appear between these times. Outside of them the app stays quiet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickStart = true }, modifier = Modifier.weight(1f)) { Text("From ${minutesToHhmm(settings.activeStart)}") }
                    OutlinedButton(onClick = { pickEnd = true }, modifier = Modifier.weight(1f)) { Text("To ${minutesToHhmm(settings.activeEnd)}") }
                }
                if (settings.activeEnd - settings.activeStart < 60) {
                    Text("That window is too short, so 08:00–22:00 is used instead.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            SettingsCard("Dates") {
                Text("When you type a numeric date, read it as", style = MaterialTheme.typography.bodyMedium)
                val orders = DateOrder.entries
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    orders.forEachIndexed { index, order ->
                        SegmentedButton(
                            selected = settings.dateOrder == order,
                            onClick = { vm.updateSettings { it.copy(dateOrder = order) } },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = orders.size),
                            label = { Text(order.label) },
                        )
                    }
                }
                Text(settings.dateOrder.example, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Dates are always shown month first, like \"October 10\".", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SettingsCard("Import & backup") {
                Text(
                    "Bring in exams and appointments from your calendar, or keep a copy of everything as a file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = vm::openImport) { Text("Import from calendar / .ics file") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { exportLauncher.launch("smart-reminder-backup-${LocalDate.now()}.json") },
                        modifier = Modifier.weight(1f),
                    ) { Text("Export backup") }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Restore backup") }
                }
                Text("Backups contain your reminders and settings, never your API keys.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SettingsCard("Look") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Use wallpaper colours", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Material You, follows your wallpaper" else "Needs Android 12 or newer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.dynamicColors,
                        onCheckedChange = { on -> vm.updateSettings { it.copy(dynamicColors = on) } },
                        enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    )
                }
            }

            SettingsCard("Permissions") {
                PermissionRow("Display over other apps", "Needed for the pop-up card", status.overlay) {
                    context.startActivity(Permissions.overlayIntent(context))
                }
                PermissionRow("Exact alarms", "Lets reminders fire on time", status.exactAlarms) {
                    Permissions.exactAlarmIntent(context)?.let(context::startActivity)
                }
                PermissionRow("Unrestricted battery", "Stops Android from silencing the app", status.batteryUnrestricted) {
                    context.startActivity(Permissions.batteryIntent(context))
                }
                if (settings.usesNotifications) {
                    PermissionRow("Notifications", "Only needed for the notification options above", status.notifications) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else context.startActivity(Permissions.notificationSettingsIntent(context))
                    }
                }
                if (isSamsung) {
                    HorizontalDivider()
                    Text("Samsung tip", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "One UI puts apps to sleep after a few days, which silences the pop-ups. Open Settings → Battery → Background usage limits and make sure Smart Reminder is not in \"Sleeping apps\" or \"Deep sleeping apps\" (add it to \"Never sleeping apps\"). Also set this app's battery use to \"Unrestricted\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { context.startActivity(Permissions.appSettingsIntent(context)) }) { Text("Open this app's settings") }
                }
            }

            SettingsCard("About") {
                Text("Smart Reminder ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "Reminder times are unpredictable on purpose. All you know is meh < whatever < A LOT, and that deadlines get louder as they get closer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Source code and releases: github.com/ItzSuli/Smart-reminder", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickStart) {
        TimePickerSheet(LocalTime.of(settings.activeStart / 60 % 24, settings.activeStart % 60), onDismiss = { pickStart = false }) { t ->
            vm.updateSettings { it.copy(activeStart = t.hour * 60 + t.minute) }
        }
    }
    if (pickEnd) {
        TimePickerSheet(LocalTime.of(settings.activeEnd / 60 % 24, settings.activeEnd % 60), onDismiss = { pickEnd = false }) { t ->
            vm.updateSettings { it.copy(activeEnd = t.hour * 60 + t.minute) }
        }
    }
}

@Composable
private fun AiCard(
    vm: MainViewModel,
    engine: AiEngine,
    geminiKey: String,
    geminiModel: String,
    claudeKey: String,
    claudeModel: String,
    nanoStatus: NanoEngine.Status,
) {
    val scope = rememberCoroutineScope()
    var showGeminiKey by remember { mutableStateOf(false) }
    var showClaudeKey by remember { mutableStateOf(false) }
    var claudeExpanded by remember { mutableStateOf(claudeKey.isNotBlank()) }
    var testing by remember { mutableStateOf(false) }
    var geminiResult by remember { mutableStateOf<String?>(null) }
    var claudeResult by remember { mutableStateOf<String?>(null) }

    SettingsCard("AI clean-up") {
        Text(
            "Pick what turns your messy notes into reminders. \"Automatic\" is right for most people.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AiEngine.entries.forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.updateSettings { it.copy(aiEngine = option) } }
                    .padding(vertical = 4.dp),
            ) {
                RadioButton(selected = option == engine, onClick = null)
                Column(Modifier.padding(start = 6.dp)) {
                    Text(option.label, style = MaterialTheme.typography.bodyMedium)
                    Text(option.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        HorizontalDivider()
        Text("Gemini Nano on this phone", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val (text, ok) = when (val s = nanoStatus) {
                NanoEngine.Status.Unknown -> "Not checked yet" to false
                NanoEngine.Status.Checking -> "Checking…" to false
                NanoEngine.Status.Available -> "Ready. Notes are cleaned up on the phone, no internet needed." to true
                NanoEngine.Status.Downloadable -> "Supported, but the model still has to be downloaded (Wi-Fi recommended)." to false
                is NanoEngine.Status.Downloading -> "Downloading… ${s.bytes / 1_000_000} MB so far" to false
                is NanoEngine.Status.Unavailable -> "Not available on this phone (${s.reason}). Galaxy S25 and newer, Pixel 9 and newer support it." to false
            }
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = if (ok) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            when (nanoStatus) {
                NanoEngine.Status.Downloadable -> FilledTonalButton(onClick = vm::downloadNano) { Text("Download") }
                NanoEngine.Status.Checking, is NanoEngine.Status.Downloading ->
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                NanoEngine.Status.Available -> Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A))
                else -> TextButton(onClick = vm::refreshNano) { Text("Check") }
            }
        }

        HorizontalDivider()
        Text("Gemini API key (free)", style = MaterialTheme.typography.titleSmall)
        Text(
            "Go to aistudio.google.com, tap \"Get API key\", paste it here. No card needed. Only the text of the note is sent to Google.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        KeyField(
            value = geminiKey,
            label = "Gemini API key",
            placeholder = "AIza…",
            visible = showGeminiKey,
            onToggle = { showGeminiKey = !showGeminiKey },
            onChange = { v -> vm.updateSettings { it.copy(geminiKey = v) }; geminiResult = null },
        )
        OutlinedTextField(
            value = geminiModel,
            onValueChange = { v -> vm.updateSettings { it.copy(geminiModel = v) }; geminiResult = null },
            label = { Text("Model") },
            supportingText = { Text("Default: gemini-3.5-flash-lite (fast, free). Any free-tier Gemini model works.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TestRow(enabled = geminiKey.isNotBlank() && !testing, testing = testing, result = geminiResult) {
            scope.launch { testing = true; geminiResult = vm.testGemini(); testing = false }
        }

        HorizontalDivider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { claudeExpanded = !claudeExpanded },
        ) {
            Text("Claude API key (paid, optional)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(if (claudeExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
        }
        if (claudeExpanded) {
            KeyField(
                value = claudeKey,
                label = "Claude API key",
                placeholder = "sk-ant-…",
                visible = showClaudeKey,
                onToggle = { showClaudeKey = !showClaudeKey },
                onChange = { v -> vm.updateSettings { it.copy(claudeKey = v) }; claudeResult = null },
            )
            OutlinedTextField(
                value = claudeModel,
                onValueChange = { v -> vm.updateSettings { it.copy(claudeModel = v) }; claudeResult = null },
                label = { Text("Model") },
                supportingText = { Text("Default: claude-opus-5. Cheaper: claude-haiku-4-5.") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            TestRow(enabled = claudeKey.isNotBlank() && !testing, testing = testing, result = claudeResult) {
                scope.launch { testing = true; claudeResult = vm.testClaude(); testing = false }
            }
        }
    }
}

@Composable
private fun KeyField(
    value: String,
    label: String,
    placeholder: String,
    visible: Boolean,
    onToggle: () -> Unit,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = { TextButton(onClick = onToggle) { Text(if (visible) "Hide" else "Show") } },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TestRow(enabled: Boolean, testing: Boolean, result: String?, onTest: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(onClick = onTest, enabled = enabled) {
            if (testing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
            }
            Text("Test connection")
        }
        result?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (it.startsWith("Connected")) Color(0xFF16A34A) else MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun DeliveryPicker(title: String, selected: Delivery, onSelect: (Delivery) -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp))
        Delivery.entries.forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(option) }
                    .padding(vertical = 4.dp),
            ) {
                RadioButton(selected = option == selected, onClick = null)
                Column(Modifier.padding(start = 6.dp)) {
                    Text(option.label, style = MaterialTheme.typography.bodyMedium)
                    Text(option.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(title: String, subtitle: String, ok: Boolean, onFix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (ok) Color(0xFF16A34A) else MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (ok) TextButton(onClick = onFix) { Text("Change") } else FilledTonalButton(onClick = onFix) { Text("Allow") }
    }
}

@Suppress("unused")
private val keepLocale = Locale.ROOT
