package com.itzsuli.smartreminder.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * DEADLINE: things you have to get done (homework, errands) – nagged at the chosen level.
 * ROUTINE: things you do every day (supplements, habits).
 * EVENT: things that simply happen at a set time and you just show up (appointments, meetings) –
 *        only a couple of gentle heads-ups, never constant nagging.
 */
@Serializable
enum class ReminderKind { DEADLINE, ROUTINE, EVENT }

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
    /** HH:mm. Deadlines: the due time. Routines: a fixed time that is always one of the slots. */
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
    /** Routines only: ISO dates on which it was done. Feeds the streak counter. */
    val history: List<String> = emptyList(),
    /** Events only: how many days ahead the first heads-up comes (1, 3 or 7). */
    val leadDays: Int = 1,
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

enum class ParseSource(val label: String) {
    CLAUDE("Cleaned up by Claude"),
    GEMINI("Cleaned up by Gemini"),
    NANO("Cleaned up on this phone"),
    LOCAL("Cleaned up offline"),
}

/** Which brain turns notes into reminders. */
enum class AiEngine(val label: String, val description: String) {
    AUTO("Automatic", "Best available option: Gemini Nano on this phone if it has it, otherwise your free Gemini key, otherwise offline."),
    NANO("On this phone (Gemini Nano)", "Free, private, works without internet. Needs a supported phone (Pixel 9 or newer, Galaxy S25 or newer, …)."),
    GEMINI("Gemini API (free key)", "Free key from Google AI Studio, no card needed. Needs internet."),
    CLAUDE("Claude API (paid key)", "Anthropic's Claude. Costs a fraction of a cent per note."),
    OFFLINE("Offline only", "The built-in parser. Gets dates and typos right, but doesn't rewrite sentences as nicely."),
}

/** How a reminder reaches you. */
enum class Delivery(val label: String, val description: String) {
    POPUP("Pop-up only", "A small card slides in for a few seconds. If the screen is off it quietly tries again a little later."),
    POPUP_THEN_NOTIFICATION("Pop-up, notification if screen is off", "Pop-up when you're using the phone, a normal notification otherwise."),
    NOTIFICATION("Notification only", "Classic notification every time."),
}

/** Where the pop-up card appears on screen. */
enum class PopupPosition(val label: String) {
    TOP("Top"),
    UPPER_MIDDLE("Upper middle"),
    CENTER("Center");
}

enum class DateOrder(val label: String, val example: String) {
    DAY_FIRST("day.month", "10.10 = October 10, 3.5 = May 3"),
    MONTH_FIRST("month/day", "10/12 = October 12, 3/5 = March 5");
}

data class Settings(
    val aiEngine: AiEngine = AiEngine.AUTO,
    val geminiKey: String = "",
    val geminiModel: String = DEFAULT_GEMINI_MODEL,
    val claudeKey: String = "",
    val claudeModel: String = DEFAULT_CLAUDE_MODEL,
    val deadlineDelivery: Delivery = Delivery.POPUP,
    val routineDelivery: Delivery = Delivery.POPUP,
    val popupSeconds: Int = 3,
    val popupPosition: PopupPosition = PopupPosition.UPPER_MIDDLE,
    /** Minutes after midnight. Reminders only fire inside [activeStart, activeEnd). */
    val activeStart: Int = 8 * 60,
    val activeEnd: Int = 22 * 60,
    val dateOrder: DateOrder = DateOrder.DAY_FIRST,
    val dynamicColors: Boolean = false,
    val setupCardDismissed: Boolean = false,
) {
    val hasGeminiKey: Boolean get() = geminiKey.isNotBlank()
    val hasClaudeKey: Boolean get() = claudeKey.isNotBlank()

    /** True when at least one delivery style can post notifications. */
    val usesNotifications: Boolean get() = deadlineDelivery != Delivery.POPUP || routineDelivery != Delivery.POPUP

    /** Sanitised active window; falls back to 08:00–22:00 if the values make no sense. */
    val activeWindow: IntRange
        get() = if (activeEnd - activeStart >= 60) activeStart until activeEnd else (8 * 60) until (22 * 60)

    companion object {
        const val DEFAULT_GEMINI_MODEL = "gemini-3.5-flash-lite"
        const val DEFAULT_CLAUDE_MODEL = "claude-opus-5"
    }
}
