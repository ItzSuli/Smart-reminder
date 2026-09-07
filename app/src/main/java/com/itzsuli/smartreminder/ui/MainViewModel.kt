package com.itzsuli.smartreminder.ui

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.itzsuli.smartreminder.SmartReminderApp
import com.itzsuli.smartreminder.ai.ClaudeParser
import com.itzsuli.smartreminder.ai.ReminderParser
import com.itzsuli.smartreminder.data.DayPart
import com.itzsuli.smartreminder.data.Intensity
import com.itzsuli.smartreminder.data.ParseSource
import com.itzsuli.smartreminder.data.Reminder
import com.itzsuli.smartreminder.data.ReminderKind
import com.itzsuli.smartreminder.data.Settings
import com.itzsuli.smartreminder.schedule.Popup
import com.itzsuli.smartreminder.schedule.Scheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

sealed class Screen {
    data object Home : Screen()
    data object Add : Screen()
    data object Settings : Screen()
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
) {
    val isEdit: Boolean get() = editId != null
    val canSave: Boolean get() = previewReady && title.isNotBlank() && !loading
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SmartReminderApp
    private val parser = ReminderParser { app.settings.settings.value }

    val reminders: StateFlow<List<Reminder>> = app.repository.reminders
    val settings: StateFlow<Settings> = app.settings.settings

    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _draft = MutableStateFlow(Draft())
    val draft: StateFlow<Draft> = _draft.asStateFlow()

    /** Bumped every time the activity resumes so permission rows re-check themselves. */
    private val _resumeTick = MutableStateFlow(0)
    val resumeTick: StateFlow<Int> = _resumeTick.asStateFlow()

    fun onResumed() = _resumeTick.update { it + 1 }

    // ---- navigation ------------------------------------------------------------------------

    fun goHome() { _screen.value = Screen.Home }
    fun openSettings() { _screen.value = Screen.Settings }

    fun startAdd(prefill: String? = null) {
        _draft.value = Draft(input = prefill.orEmpty())
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
        )
        _screen.value = Screen.Add
    }

    // ---- draft editing ---------------------------------------------------------------------

    fun updateDraft(transform: (Draft) -> Draft) = _draft.update(transform)

    /** Runs the note through Claude (or the offline parser) and fills the editable preview. */
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
            emoji = d.emoji.trim().ifBlank { if (d.kind == ReminderKind.ROUTINE) "🔁" else "📌" },
            intensity = d.intensity,
            dueDate = if (d.kind == ReminderKind.DEADLINE) d.dueDate?.toString() else null,
            dueTime = d.dueTime?.toString()?.take(5),
            dayParts = if (d.kind == ReminderKind.ROUTINE) d.dayParts else emptyList(),
            rawInput = d.input.trim(),
            // editing a done reminder brings it back to life
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

    // ---- settings --------------------------------------------------------------------------

    fun updateSettings(transform: (Settings) -> Settings) {
        app.settings.update(transform)
        Scheduler.reschedule(app)
    }

    suspend fun testConnection(): String {
        val s = settings.value
        if (!s.hasApiKey) return "Enter an API key first."
        return try {
            val ping = ClaudeParser(s.apiKey, s.model).ping(s.dateOrder)
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
            Popup.show(context, listOf(sample), settings.value.popupSeconds * 1000L) {}
        } else {
            Toast.makeText(context, "Allow \"display over other apps\" first, then try again.", Toast.LENGTH_LONG).show()
        }
    }
}
