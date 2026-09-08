package com.itzsuli.smartreminder.data

import android.content.Context
import com.itzsuli.smartreminder.schedule.Scheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

/**
 * Tiny JSON-file store. A personal reminder list is a few kilobytes, so a database
 * would be overkill; writes are atomic (write temp file, then rename).
 */
class ReminderRepository(private val context: Context) {

    private val file = File(context.filesDir, "reminders.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val serializer = ListSerializer(Reminder.serializer())
    private val lock = Any()

    private val _reminders = MutableStateFlow<List<Reminder>>(emptyList())
    val reminders: StateFlow<List<Reminder>> = _reminders.asStateFlow()

    init {
        load()
    }

    private fun load() {
        synchronized(lock) {
            if (!file.exists()) return
            runCatching { json.decodeFromString(serializer, file.readText()) }
                .onSuccess { _reminders.value = it }
        }
    }

    private fun persist(list: List<Reminder>, reschedule: Boolean = true) {
        synchronized(lock) {
            _reminders.value = list
            val text = json.encodeToString(serializer, list)
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        }
        if (reschedule) Scheduler.reschedule(context)
    }

    fun get(id: String): Reminder? = _reminders.value.firstOrNull { it.id == id }

    fun upsert(reminder: Reminder) {
        val current = _reminders.value
        val list = if (current.any { it.id == reminder.id }) {
            current.map { if (it.id == reminder.id) reminder else it }
        } else {
            current + reminder
        }
        persist(list)
    }

    fun delete(id: String) = persist(_reminders.value.filterNot { it.id == id })

    fun update(id: String, transform: (Reminder) -> Reminder) {
        val current = _reminders.value
        if (current.none { it.id == id }) return
        persist(current.map { if (it.id == id) transform(it) else it })
    }

    fun setDone(id: String, done: Boolean) = update(id) {
        it.copy(done = done, doneAt = if (done) System.currentTimeMillis() else null)
    }

    fun setDoneForToday(id: String, done: Boolean) = update(id) {
        val today = LocalDate.now()
        it.copy(
            doneForDay = if (done) today.toString() else null,
            history = Streaks.with(it.history, today, done),
        )
    }

    /** Adds or replaces several reminders at once (imports, restores). Returns how many were written. */
    fun upsertAll(items: List<Reminder>): Int {
        if (items.isEmpty()) return 0
        val byId = items.associateBy { it.id }
        val current = _reminders.value
        val merged = current.map { byId[it.id] ?: it } + items.filter { r -> current.none { it.id == r.id } }
        persist(merged)
        return items.size
    }

    fun setPaused(id: String, paused: Boolean) = update(id) { it.copy(paused = paused) }

    fun clearDone() = persist(_reminders.value.filterNot { it.done })

    /** Removes events that are over (date before today) or ticked off. */
    fun clearPastEvents(today: LocalDate = LocalDate.now()) = persist(
        _reminders.value.filterNot { it.kind == ReminderKind.EVENT && (it.done || (it.dueLocalDate?.isBefore(today) == true)) }
    )
}
