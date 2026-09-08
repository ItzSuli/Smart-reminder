package com.itzsuli.smartreminder.schedule

import com.itzsuli.smartreminder.data.DayPart
import com.itzsuli.smartreminder.data.Intensity
import com.itzsuli.smartreminder.data.Reminder
import com.itzsuli.smartreminder.data.ReminderKind
import com.itzsuli.smartreminder.data.Settings
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.Collections
import java.util.Random
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 * ┌──────────────────────────────────────────────────────────────────────────────┐
 * │  SPOILER WARNING                                                             │
 * │  This file decides how often each reminder shows up during a day.            │
 * │  The whole point of the app is that you don't know, so if you want to keep   │
 * │  the surprise, close this file now. The schedule is also mixed with a random │
 * │  number generated on your phone, so reading the code never tells you the     │
 * │  exact times anyway.                                                         │
 * └──────────────────────────────────────────────────────────────────────────────┘
 */
object Cadence {

    private val DEADLINE_BASE = mapOf(Intensity.MEH to 1.0, Intensity.WHATEVER to 3.0, Intensity.A_LOT to 6.0)
    private val ROUTINE_BASE = mapOf(Intensity.MEH to 1, Intensity.WHATEVER to 2, Intensity.A_LOT to 4)
    private const val MAX_PER_DAY = 10
    private const val FINAL_CALL_MINUTES = 60L

    /** All moments on [day] at which [reminder] wants to pop up, sorted. Deterministic for a given salt. */
    fun slotsFor(reminder: Reminder, day: LocalDate, settings: Settings, salt: Long): List<LocalTime> {
        if (!reminder.isActive) return emptyList()
        if (reminder.kind == ReminderKind.ROUTINE && reminder.isDoneForDay(day)) return emptyList()
        val rng = Random(seed(reminder.id, day, salt))
        val window = settings.activeWindow
        val slots = when (reminder.kind) {
            ReminderKind.DEADLINE -> deadlineSlots(reminder, day, rng, window)
            ReminderKind.ROUTINE -> routineSlots(reminder, rng, window)
            ReminderKind.EVENT -> eventSlots(reminder, day, rng, window)
        }
        return slots.distinct().sorted()
    }

    private fun deadlineSlots(r: Reminder, day: LocalDate, rng: Random, window: IntRange): List<LocalTime> {
        val due = r.dueLocalDate
        val daysLeft = due?.let { ChronoUnit.DAYS.between(day, it).toInt() }
        // Two weeks past the deadline we stop nagging; the app still shows it as overdue.
        if (daysLeft != null && daysLeft < -14) return emptyList()

        val urgency = when {
            daysLeft == null -> 1.0
            daysLeft in -3..0 -> 2.0
            daysLeft < -3 -> 1.0
            daysLeft == 1 -> 1.6
            daysLeft == 2 -> 1.3
            daysLeft <= 6 -> 1.0
            daysLeft <= 13 -> 0.6
            daysLeft <= 29 -> 0.35
            else -> 0.15
        }
        val expected = DEADLINE_BASE.getValue(r.intensity) * urgency
        var count = floor(expected).toInt()
        if (rng.nextDouble() < expected - count) count++ // stochastic rounding: 0.35 → some days yes, most days no
        count = min(count, MAX_PER_DAY)

        val slots = spread(count, window.first, window.last + 1, rng).toMutableList()

        // "Final call": one guaranteed nudge an hour before a timed deadline on its day.
        val dueTime = r.dueLocalTime
        if (daysLeft == 0 && dueTime != null) {
            val finalCall = dueTime.minusMinutes(FINAL_CALL_MINUTES)
            val finalMinute = finalCall.toSecondOfDay() / 60
            if (finalCall.isBefore(dueTime) && finalMinute in window) {
                slots.removeAll { abs(it.toSecondOfDay() / 60 - finalMinute) < 30 }
                slots += finalCall
            }
        }
        return slots
    }

    /**
     * Events are things you just attend, so they get a handful of heads-ups instead of nagging:
     * one on the first lead day (if the lead is longer than a day), one the day before (later in
     * the day), one on the morning of, and a final call an hour before a timed event.
     */
    private fun eventSlots(r: Reminder, day: LocalDate, rng: Random, window: IntRange): List<LocalTime> {
        val due = r.dueLocalDate ?: return emptyList()
        val daysLeft = ChronoUnit.DAYS.between(day, due).toInt()
        if (daysLeft < 0) return emptyList()
        val lead = r.leadDays.coerceIn(1, 30)
        val start = window.first
        val end = window.last + 1
        val eventMinute = r.dueLocalTime?.let { it.toSecondOfDay() / 60 }
        return when {
            daysLeft == 0 -> {
                val out = mutableListOf<LocalTime>()
                val latest = min(start + 180, eventMinute?.minus(45) ?: end)
                if (latest > start) out += spread(1, start, min(latest, end), rng)
                if (eventMinute != null) {
                    val finalCall = eventMinute - FINAL_CALL_MINUTES.toInt()
                    if (finalCall in window && out.none { abs(it.toSecondOfDay() / 60 - finalCall) < 30 }) {
                        out += LocalTime.of(finalCall / 60, finalCall % 60)
                    }
                }
                out
            }
            daysLeft == 1 -> spread(1, start + (end - start) * 55 / 100, end, rng)
            daysLeft == lead && lead > 1 -> spread(1, start, end, rng)
            lead >= 7 && daysLeft == 3 -> spread(1, start, end, rng)
            else -> emptyList()
        }
    }

    private fun routineSlots(r: Reminder, rng: Random, window: IntRange): List<LocalTime> {
        var count = ROUTINE_BASE.getValue(r.intensity)
        val fixed = mutableListOf<LocalTime>()
        // An explicit time ("every day at 8") is always one of the slots.
        r.dueLocalTime?.let { fixed += it; count-- }
        if (count <= 0) return fixed

        val parts = r.dayParts.mapNotNull { partWindow(it, window) }
        if (parts.isEmpty()) return fixed + spread(count, window.first, window.last + 1, rng)

        // Hand the slots round-robin to the chosen parts of the day, starting at a random part,
        // so "meh" with morning + evening lands in a different part on different days.
        val order = parts.indices.toMutableList()
        Collections.shuffle(order, rng)
        val perPart = IntArray(parts.size)
        repeat(count) { i -> perPart[order[i % order.size]]++ }
        return fixed + parts.indices.flatMap { i -> spread(perPart[i], parts[i].first, parts[i].second, rng) }
    }

    private fun partWindow(part: DayPart, active: IntRange): Pair<Int, Int>? {
        val (s, e) = when (part) {
            DayPart.MORNING -> (6 * 60 + 30) to (11 * 60)
            DayPart.MIDDAY -> (11 * 60) to (16 * 60)
            DayPart.EVENING -> (16 * 60) to (22 * 60 + 30)
        }
        val start = max(s, active.first)
        val end = min(e, active.last + 1)
        return if (end - start >= 20) start to end else null
    }

    /** [count] random minutes between [start] and [end), one in each equal segment so they are spread out. */
    private fun spread(count: Int, start: Int, end: Int, rng: Random): List<LocalTime> {
        if (count <= 0 || end <= start) return emptyList()
        val segment = (end - start).toDouble() / count
        return (0 until count).map { i ->
            val minute = (start + i * segment + rng.nextDouble() * segment).toInt().coerceIn(start, end - 1)
            LocalTime.of(minute / 60, minute % 60)
        }
    }

    private fun seed(id: String, day: LocalDate, salt: Long): Long {
        var h = salt xor -0x61c8864680b583ebL
        h = h * 31 + id.hashCode()
        h = h * 1_000_003L + day.toEpochDay()
        return h
    }
}
