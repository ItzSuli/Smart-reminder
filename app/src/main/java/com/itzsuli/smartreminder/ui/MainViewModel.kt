package com.itzsuli.smartreminder.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.itzsuli.smartreminder.SmartReminderApp
import com.itzsuli.smartreminder.ai.ClaudeParser
import com.itzsuli.smartreminder.ai.GeminiParser
import com.itzsuli.smartreminder.ai.NanoEngine
import com.itzsuli.smartreminder.ai.ReminderParser
import com.itzsuli.smartreminder.data.DayPart
import com.itzsuli.smartreminder.data.Intensity
import com.itzsuli.smartreminder.data.ParseSource
import com.itzsuli.smartreminder.data.Reminder
import com.itzsuli.smartreminder.data.ReminderKind
import com.itzsuli.smartreminder.data.Settings
import com.itzsuli.smartreminder.io.Backup
import com.itzsuli.smartreminder.io.CalendarSource
import com.itzsuli.smartreminder.io.IcsParser
import com.itzsuli.smartreminder.io.ImportEvent
import com.itzsuli.smartreminder.schedule.Popup
import com.itzsuli.smartreminder.schedule.Scheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed class Screen {
    data object Home : Screen()
    data object Add : Screen()
    data object Settings : Screen()
    data object Import : Screen()
}

/** Everything the add/edit screen edits. Lives in the view model so rotation doesn't lose it. */
data class Draft(
    val editId: String? = null,
    val input: String = "",
    val title: String = "",
    val details: String = "",
    val emoji: String = "📌",
    val kind: ReminderKind = ReminderKind.DEADLINE,
    val intensity: Intensity = Intensity.WHATEVER,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val dayParts: List<DayPart> = emptyList(),
    val previewReady: Boolean = false,
    val loading: Boolean = false,
    val source: ParseSource? = null,
    val note: String? = null,
    val original: Reminder? = null,
    /** Set when the screen was opened through "Speak": it launches voice input once. */
    val launchVoice: Boolean = false,
    /** Events only: first heads-up this many days before. */
    val leadDays: Int = 1,
) {
    val isEdit: Boolean get() = editId != null
    val canSave: Boolean get() = previewReady && title.isNotBlank() && !loading && (kind != ReminderKind.EVENT || dueDate != null)
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SmartReminderApp
    private val parser = ReminderParser { app.settings.settings.value }

    val reminders: StateFlow<List<Reminder>> = app.repository.reminders
    val settings: StateFlow<Settings> = app.settings.settings
    val nanoStatus: StateFlow<NanoEngine.Status> = NanoEngine.status

    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _draft = MutableStateFlow(Draft())
    val draft: StateFlow<Draft> = _draft.asStateFlow()

    /** Bumped every time the activity resumes so permission rows re-check themselves. */
    private val _resumeTick = MutableStateFlow(0)
    val resumeTick: StateFlow<Int> = _resumeTick.asStateFlow()

    /** One-shot messages shown as a toast by the UI. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _importEvents = MutableStateFlow<List<ImportEvent>>(emptyList())
    val importEvents: StateFlow<List<ImportEvent>> = _importEvents.asStateFlow()
    private val _importBusy = MutableStateFlow(false)
    val importBusy: StateFlow<Boolean> = _importBusy.asStateFlow()

    fun onResumed() = _resumeTick.update { it + 1 }
    fun consumeMessage() { _message.value = null }
    private fun say(text: String) { _message.value = text }

    // ---- navigation ------------------------------------------------------------------------

    fun goHome() { _screen.value = Screen.Home }
    fun openSettings() { _screen.value = Screen.Settings }

    fun openImport() {
        _screen.value = Screen.Import
        refreshCalendar()
    }

    fun startAdd(prefill: String? = null, voice: Boolean = false) {
        _draft.value = Draft(input = prefill.orEmpty(), launchVoice = voice)
        _screen.value = Screen.Add
    }

    fun startEdit(id: String) {
        val r = app.repository.get(id) ?: return
        _draft.value = Draft(
            editId = r.id,
            input = r.rawInput,
            title = r.title,
            details = r.details,
            emoji = r.emoji,
            kind = r.kind,
            intensity = r.intensity,
            dueDate = r.dueLocalDate,
            dueTime = r.dueLocalTime,
            dayParts = r.dayParts,
            previewReady = true,
            original = r,
            leadDays = r.leadDays,
        )
        _screen.value = Screen.Add
    }

    // ---- draft editing ---------------------------------------------------------------------

    fun updateDraft(transform: (Draft) -> Draft) = _draft.update(transform)

    /** Runs the note through the chosen AI (or the offline parser) and fills the editable preview. */
    fun makeClear() {
        val input = _draft.value.input.trim()
        if (input.isEmpty() || _draft.value.loading) return
        _draft.update { it.copy(loading = true, note = null) }
        viewModelScope.launch {
            try {
                val parsed = parser.parse(input)
                _draft.update { d ->
                    d.copy(
                        loading = false,
                        previewReady = true,
                        title = parsed.title,
                        details = parsed.details,
                        emoji = parsed.emoji,
                        kind = parsed.kind,
                        dueDate = parsed.dueDate,
                        dueTime = parsed.dueTime,
                        dayParts = parsed.dayParts,
                        source = parsed.source,
                        note = parsed.note,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _draft.update { it.copy(loading = false, previewReady = true, title = it.title.ifBlank { input }, note = "Something went wrong: ${e.message}") }
            }
        }
    }

    fun saveDraft() {
        val d = _draft.value
        if (!d.canSave) return
        val base = d.original ?: Reminder(kind = d.kind, title = d.title, rawInput = d.input)
        val reminder = base.copy(
            kind = d.kind,
            title = d.title.trim(),
            details = d.details.trim(),
            emoji = d.emoji.trim().ifBlank { when (d.kind) { ReminderKind.ROUTINE -> "🔁"; ReminderKind.EVENT -> "📅"; ReminderKind.DEADLINE -> "📌" } },
            intensity = d.intensity,
            dueDate = if (d.kind != ReminderKind.ROUTINE) d.dueDate?.toString() else null,
            leadDays = d.leadDays.coerceIn(1, 30),
            dueTime = d.dueTime?.toString()?.take(5),
            dayParts = if (d.kind == ReminderKind.ROUTINE) d.dayParts else emptyList(),
            rawInput = d.input.trim(),
            done = false,
            doneAt = null,
        )
        app.repository.upsert(reminder)
        _draft.value = Draft()
        goHome()
    }

    fun deleteDraft() {
        _draft.value.editId?.let { app.repository.delete(it) }
        _draft.value = Draft()
        goHome()
    }

    // ---- list actions ----------------------------------------------------------------------

    fun setDone(id: String, done: Boolean) = app.repository.setDone(id, done)
    fun setDoneForToday(id: String, done: Boolean) = app.repository.setDoneForToday(id, done)
    fun setPaused(id: String, paused: Boolean) = app.repository.setPaused(id, paused)
    fun delete(id: String) = app.repository.delete(id)
    fun clearDone() = app.repository.clearDone()
    fun clearPastEvents() = app.repository.clearPastEvents()

    // ---- settings + AI ---------------------------------------------------------------------

    fun updateSettings(transform: (Settings) -> Settings) {
        app.settings.update(transform)
        Scheduler.reschedule(app)
    }

    fun refreshNano() { viewModelScope.launch { NanoEngine.refresh() } }
    fun downloadNano() { viewModelScope.launch { NanoEngine.download() } }

    suspend fun testGemini(): String {
        val s = settings.value
        if (!s.hasGeminiKey) return "Enter a Gemini API key first."
        return try {
            val ping = GeminiParser(s.geminiKey, s.geminiModel).ping(s.dateOrder)
            "Connected. ${ping.model} answered: \"${ping.sample.title}\""
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Failed: ${e.message}"
        }
    }

    suspend fun testClaude(): String {
        val s = settings.value
        if (!s.hasClaudeKey) return "Enter a Claude API key first."
        return try {
            val ping = ClaudeParser(s.claudeKey, s.claudeModel).ping(s.dateOrder)
            "Connected. ${ping.model} answered: \"${ping.sample.title}\""
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Failed: ${e.message}"
        }
    }

    fun showTestPopup(context: Context) {
        val sample = Reminder(
            kind = ReminderKind.ROUTINE,
            title = "This is how a pop-up looks",
            details = "It disappears by itself. Tap \"Done today\" to tick a daily thing off.",
            emoji = "👋",
        )
        if (Popup.canDraw(context)) {
            Popup.show(context, listOf(sample), settings.value.popupSeconds * 1000L, settings.value.popupPosition) {}
        } else {
            Toast.makeText(context, "Allow \"display over other apps\" first, then try again.", Toast.LENGTH_LONG).show()
        }
    }

    // ---- calendar / .ics import ------------------------------------------------------------

    fun refreshCalendar() {
        viewModelScope.launch {
            _importBusy.value = true
            val fromCalendar = withContext(Dispatchers.IO) { CalendarSource.upcoming(app) }
            val fromFiles = _importEvents.value.filter { it.key.startsWith("ics:") }
            _importEvents.value = (fromCalendar + fromFiles).distinctBy { it.key }.sortedWith(compareBy({ it.date }, { it.time }))
            _importBusy.value = false
        }
    }

    fun loadIcs(uri: Uri) {
        viewModelScope.launch {
            _importBusy.value = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = app.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                    IcsParser.parse(text)
                }
            }
            result.onSuccess { events ->
                _importEvents.value = (_importEvents.value + events).distinctBy { it.key }.sortedWith(compareBy({ it.date }, { it.time }))
                say(if (events.isEmpty()) "No upcoming events found in that file." else "Found ${events.size} upcoming events.")
            }.onFailure { say("Couldn't read that file: ${it.message}") }
            _importBusy.value = false
        }
    }

    fun importSelected(keys: Set<String>, intensity: Intensity, kind: ReminderKind = ReminderKind.EVENT) {
        val existing = app.repository.reminders.value
        val picked = _importEvents.value.filter { it.key in keys }
        val fresh = picked.filterNot { e ->
            existing.any { it.title.equals(e.title, ignoreCase = true) && it.dueDate == e.date.toString() && !it.done }
        }
        val reminders = fresh.map { e ->
            val dateText = e.date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault()))
            val timeText = e.time?.let { " at " + it.format(DateTimeFormatter.ofPattern("HH:mm")) } ?: ""
            Reminder(
                kind = kind,
                title = e.title,
                details = e.details.take(200).ifBlank { "From ${e.source}." } + (if (kind == ReminderKind.EVENT) " On $dateText$timeText." else " Due $dateText$timeText."),
                emoji = "📅",
                intensity = intensity,
                dueDate = e.date.toString(),
                dueTime = e.time?.format(DateTimeFormatter.ofPattern("HH:mm")),
                rawInput = e.title,
            )
        }
        val added = app.repository.upsertAll(reminders)
        val skipped = picked.size - fresh.size
        say(buildString {
            val noun = if (kind == ReminderKind.EVENT) "event" else "deadline"
            append(if (added == 1) "Added 1 $noun." else "Added $added ${noun}s.")
            if (skipped > 0) append(" $skipped already existed.")
        })
        goHome()
    }

    // ---- backup ----------------------------------------------------------------------------

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    app.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(Backup.export(app)) }
                        ?: error("could not open the file")
                }
            }
            say(result.fold({ "Backup saved." }, { "Backup failed: ${it.message}" }))
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = app.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                    Backup.import(app, text)
                }
            }
            say(result.fold({ "Restored $it reminders." }, { "Restore failed: ${it.message}" }))
        }
    }
}
