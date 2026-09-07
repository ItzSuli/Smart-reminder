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
import com.itzsuli.smartreminder.data.DateOrder
import com.itzsuli.smartreminder.data.Delivery
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val resumeTick by vm.resumeTick.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status = remember(resumeTick) { Permissions.status(context) }
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.onResumed() }

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
            SettingsCard("AI clean-up") {
                Text(
                    "Paste a Claude API key and your notes get rewritten by Claude. Without a key the app uses its built-in, simpler parser.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = settings.apiKey,
                    onValueChange = { v -> vm.updateSettings { it.copy(apiKey = v) }; testResult = null },
                    label = { Text("Claude API key") },
                    placeholder = { Text("sk-ant-…") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") } },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = settings.model,
                    onValueChange = { v -> vm.updateSettings { it.copy(model = v) }; testResult = null },
                    label = { Text("Model") },
                    supportingText = { Text("Default: claude-opus-5. A cheaper option is claude-haiku-4-5.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            scope.launch {
                                testing = true
                                testResult = vm.testConnection()
                                testing = false
                            }
                        },
                        enabled = settings.hasApiKey && !testing,
                    ) {
                        if (testing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Test connection")
                    }
                }
                testResult?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = if (it.startsWith("Connected")) Color(0xFF16A34A) else MaterialTheme.colorScheme.error)
                }
                Text(
                    "Get a key at console.anthropic.com. The key never leaves this phone except to talk to Claude.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsCard("How reminders show up") {
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
                PermissionRow("Notifications", "Used when the screen is off", status.notifications) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else context.startActivity(Permissions.notificationSettingsIntent(context))
                }
                PermissionRow("Exact alarms", "Lets reminders fire on time", status.exactAlarms) {
                    Permissions.exactAlarmIntent(context)?.let(context::startActivity)
                }
                PermissionRow("Unrestricted battery", "Stops Android from silencing the app", status.batteryUnrestricted) {
                    context.startActivity(Permissions.batteryIntent(context))
                }
            }

            SettingsCard("About") {
                Text("Smart Reminder ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "Reminder times are unpredictable on purpose. All you know is meh < whatever < A LOT, and that deadlines get louder as they get closer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Source code: github.com/ItzSuli/Smart-reminder", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (pickStart) {
        TimePickerSheet(LocalTime.of(settings.activeStart / 60, settings.activeStart % 60), onDismiss = { pickStart = false }) { t ->
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
