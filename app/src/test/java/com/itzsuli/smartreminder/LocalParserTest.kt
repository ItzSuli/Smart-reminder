package com.itzsuli.smartreminder

import com.itzsuli.smartreminder.ai.LocalParser
import com.itzsuli.smartreminder.data.DayPart
import com.itzsuli.smartreminder.data.ReminderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

class LocalParserTest {

    private val today: LocalDate = LocalDate.of(2026, 9, 7)
    private val parser = LocalParser(dayFirst = true, today = today, locale = Locale.ENGLISH)

    @Test
    fun `the example from the brief becomes a readable homework reminder`() {
        val p = parser.parse("HW englisj read page 32 till teusday 10.10")
        assertEquals(ReminderKind.DEADLINE, p.kind)
        assertEquals(LocalDate.of(2026, 10, 10), p.dueDate)
        assertEquals("English homework: read page 32", p.title)
        assertTrue(p.details, p.details.startsWith("Read page 32 for English homework."))
        assertTrue(p.details, p.details.contains("Due Saturday, 10 October."))
        assertEquals("📚", p.emoji)
    }

    @Test
    fun `weekday with a typo resolves to the next occurrence`() {
        val p = parser.parse("math test on frday")
        assertEquals(DayOfWeek.FRIDAY, p.dueDate?.dayOfWeek)
        assertTrue(p.dueDate!!.isAfter(today))
        assertEquals("Math test", p.title)
    }

    @Test
    fun `tomorrow and a clock time are both picked up`() {
        val p = parser.parse("dentist tmrw at 15:30")
        assertEquals(today.plusDays(1), p.dueDate)
        assertEquals(LocalTime.of(15, 30), p.dueTime)
        assertEquals("Dentist", p.title)
        assertEquals("🩺", p.emoji)
    }

    @Test
    fun `explicit numeric date beats the weekday`() {
        val p = parser.parse("essay by monday 20.9")
        assertEquals(LocalDate.of(2026, 9, 20), p.dueDate)
    }

    @Test
    fun `a date that already passed rolls over to next year`() {
        val p = parser.parse("party 1.1")
        assertEquals(LocalDate.of(2027, 1, 1), p.dueDate)
    }

    @Test
    fun `month first order is respected`() {
        val p = LocalParser(dayFirst = false, today = today, locale = Locale.ENGLISH).parse("call mom 10/12")
        assertEquals(LocalDate.of(2026, 10, 12), p.dueDate)
    }

    @Test
    fun `supplements become a routine with the right part of the day`() {
        val p = parser.parse("vitamin D every morning")
        assertEquals(ReminderKind.ROUTINE, p.kind)
        assertEquals(listOf(DayPart.MORNING), p.dayParts)
        assertNull(p.dueDate)
        assertEquals("Vitamin D", p.title)
        assertEquals("💊", p.emoji)
    }

    @Test
    fun `everyday keyword forces a routine even without nouns`() {
        val p = parser.parse("stretch 5 min daily")
        assertEquals(ReminderKind.ROUTINE, p.kind)
    }

    @Test
    fun `common words are not mistaken for weekdays`() {
        val p = parser.parse("pay money for the fridge")
        assertNull(p.dueDate)
    }

    @Test
    fun `page numbers are not read as times`() {
        val p = parser.parse("read page 32")
        assertNull(p.dueTime)
        assertNull(p.dueDate)
    }

    @Test
    fun `german input works too`() {
        val p = parser.parse("mathe hausaufgaben s.12 bis freitag")
        assertEquals(DayOfWeek.FRIDAY, p.dueDate?.dayOfWeek)
        assertEquals("Mathe homework: page 12", p.title)
    }

    @Test
    fun `distance treats swapped letters as one edit`() {
        assertEquals(1, LocalParser.distance("teusday", "tuesday"))
        assertEquals(1, LocalParser.distance("englisj", "english"))
        assertEquals(0, LocalParser.distance("friday", "friday"))
    }
}
