package com.itzsuli.smartreminder

import com.itzsuli.smartreminder.data.DayPart
import com.itzsuli.smartreminder.data.Intensity
import com.itzsuli.smartreminder.data.Reminder
import com.itzsuli.smartreminder.data.ReminderKind
import com.itzsuli.smartreminder.data.Settings
import com.itzsuli.smartreminder.schedule.Cadence
import com.itzsuli.smartreminder.schedule.Scheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class CadenceTest {

    private val settings = Settings()
    private val day: LocalDate = LocalDate.of(2026, 9, 7)

    private fun deadline(daysLeft: Long, intensity: Intensity, id: String = "d", time: String? = null) = Reminder(
        id = id, kind = ReminderKind.DEADLINE, title = "t", intensity = intensity,
        dueDate = day.plusDays(daysLeft).toString(), dueTime = time,
    )

    private fun routine(intensity: Intensity, parts: List<DayPart> = emptyList(), id: String = "r") =
        Reminder(id = id, kind = ReminderKind.ROUTINE, title = "t", intensity = intensity, dayParts = parts)

    private fun averageSlots(days: Int = 120, make: (Int) -> Reminder): Double =
        (0 until days).sumOf { Cadence.slotsFor(make(it), day, settings, 12345L + it).size }.toDouble() / days

    @Test
    fun `same inputs always give the same times`() {
        val r = deadline(3, Intensity.WHATEVER)
        assertEquals(Cadence.slotsFor(r, day, settings, 42L), Cadence.slotsFor(r, day, settings, 42L))
    }

    @Test
    fun `everything lands inside the active hours`() {
        for (salt in 1L..40L) {
            val slots = Cadence.slotsFor(deadline(0, Intensity.A_LOT), day, settings, salt) +
                Cadence.slotsFor(routine(Intensity.A_LOT), day, settings, salt)
            for (slot in slots) {
                val minute = slot.toSecondOfDay() / 60
                assertTrue("$slot outside window", minute in settings.activeWindow)
            }
        }
    }

    @Test
    fun `stronger intensity means more nudges`() {
        val meh = averageSlots { deadline(3, Intensity.MEH, id = "m$it") }
        val whatever = averageSlots { deadline(3, Intensity.WHATEVER, id = "w$it") }
        val aLot = averageSlots { deadline(3, Intensity.A_LOT, id = "a$it") }
        assertTrue(meh < whatever)
        assertTrue(whatever < aLot)
    }

    @Test
    fun `closer deadlines get louder`() {
        val far = averageSlots { deadline(25, Intensity.WHATEVER, id = "f$it") }
        val near = averageSlots { deadline(3, Intensity.WHATEVER, id = "n$it") }
        val today = averageSlots { deadline(0, Intensity.WHATEVER, id = "t$it") }
        assertTrue(far < near)
        assertTrue(near < today)
    }

    @Test
    fun `done or paused reminders never fire`() {
        assertTrue(Cadence.slotsFor(deadline(0, Intensity.A_LOT).copy(done = true), day, settings, 1L).isEmpty())
        assertTrue(Cadence.slotsFor(routine(Intensity.A_LOT).copy(paused = true), day, settings, 1L).isEmpty())
        assertTrue(Cadence.slotsFor(routine(Intensity.A_LOT).copy(doneForDay = day.toString()), day, settings, 1L).isEmpty())
    }

    @Test
    fun `a timed deadline gets a final call an hour before`() {
        val r = deadline(0, Intensity.MEH, time = "15:00")
        for (salt in 1L..20L) {
            assertTrue(Cadence.slotsFor(r, day, settings, salt).contains(LocalTime.of(14, 0)))
        }
    }

    @Test
    fun `routines respect the chosen part of the day`() {
        val r = routine(Intensity.A_LOT, parts = listOf(DayPart.MORNING))
        for (salt in 1L..30L) {
            for (slot in Cadence.slotsFor(r, day, settings, salt)) {
                assertTrue("$slot is not morning", slot.isBefore(LocalTime.of(11, 0)))
            }
        }
    }

    @Test
    fun `routine with a fixed time always includes it`() {
        val r = routine(Intensity.MEH).copy(dueTime = "08:30")
        assertEquals(listOf(LocalTime.of(8, 30)), Cadence.slotsFor(r, day, settings, 7L))
    }

    @Test
    fun `scheduler finds the next occurrence after a moment`() {
        val r = routine(Intensity.A_LOT)
        val after = LocalDateTime.of(day, LocalTime.of(12, 0))
        val next = Scheduler.nextFor(r, settings, 99L, after)
        assertTrue(next != null && next.isAfter(after))
        val occ = Scheduler.occurrences(r, settings, 99L, after, after.plusDays(1))
        assertTrue(occ.all { it.isAfter(after) && !it.isAfter(after.plusDays(1)) })
    }
}
