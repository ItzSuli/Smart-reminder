package com.itzsuli.smartreminder.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/** One-off things with a deadline vs. things you do every day (supplements, habits…). */
@Serializable
enum class ReminderKind { DEADLINE, ROUTINE }

/** How hard the app nags. The labels are shown exactly like this in the UI. */
@Serializable
enum class Intensity(val label: String) {
    MEH("meh"),
    WHATEVER("whatever"),
    A_LOT("A LOT");
}

/** Rough part of the day a routine reminder should land in. */
@Serializable
enum class DayPart(val label: String) {
    MORNING("Morning"),
    MIDDAY("Midday"),
    EVENING("Evening");
}

@Serializable
data class Reminder(
    val id: String = UUID.randomUUID().toString(),
    val kind: ReminderKind,
    val title: String,
    val details: String = "",
    val emoji: String = "📌",
    val intensity: Intensity = Intensity.WHATEVER,
    /** ISO date (yyyy-MM-dd), deadlines only. */
    val dueDate: String? = null,
    /** HH:mm, deadlines only. */
    val dueTime: String? = null,
    /** Routines only; empty means "any time during active hours". */
    val dayParts: List<DayPart> = emptyList(),
    val rawInput: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val done: Boolean = false,
    val doneAt: Long? = null,
    val paused: Boolean = false,
    /** ISO date on which a routine was ticked off ("done for today"). */
    val doneForDay: String? = null,
) {
    val dueLocalDate: LocalDate? get() = dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val dueLocalTime: LocalTime? get() = dueTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

    fun isDoneForDay(day: LocalDate): Boolean = doneForDay == day.toString()

    /** True when reminders may still fire for this entry. */
    val isActive: Boolean get() = !done && !paused
}

/** What the AI (or the offline parser) produced from a raw note. */
data class ParsedReminder(
    val title: String,
    val details: String,
    val dueDate: LocalDate?,
    val dueTime: LocalTime?,
    val kind: ReminderKind,
    val emoji: String,
    val dayParts: List<DayPart> = emptyList(),
    val source: ParseSource,
    /** Short human-readable note, e.g. why the offline parser was used. */
    val note: String? = null,
)

enum class ParseSource { CLAUDE, LOCAL }

/** How a reminder reaches you. */
enum class Delivery(val label: String, val description: String) {
    POPUP("Pop-up only", "A small card slides in for a few seconds. Nothing if the screen is off (it quietly tries again a bit later)."),
    POPUP_THEN_NOTIFICATION("Pop-up, notification if screen is off", "Pop-up when you're using the phone, a normal notification otherwise."),
    NOTIFICATION("Notification only", "Classic notification every time.");
}

enum class DateOrder(val label: String, val example: String) {
    DAY_FIRST("day.month", "10.10 = 10 October"),
    MONTH_FIRST("month/day", "10/12 = October 12");
}

data class Settings(
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    val deadlineDelivery: Delivery = Delivery.POPUP_THEN_NOTIFICATION,
    val routineDelivery: Delivery = Delivery.POPUP,
    val popupSeconds: Int = 3,
    /** Minutes after midnight. Reminders only fire inside [activeStart, activeEnd). */
    val activeStart: Int = 8 * 60,
    val activeEnd: Int = 22 * 60,
    val dateOrder: DateOrder = DateOrder.DAY_FIRST,
    val dynamicColors: Boolean = false,
    val setupCardDismissed: Boolean = false,
) {
    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    /** Sanitised active window; falls back to 08:00–22:00 if the values make no sense. */
    val activeWindow: IntRange
        get() = if (activeEnd - activeStart >= 60) activeStart until activeEnd else (8 * 60) until (22 * 60)

    companion object {
        const val DEFAULT_MODEL = "claude-opus-5"
    }
}
