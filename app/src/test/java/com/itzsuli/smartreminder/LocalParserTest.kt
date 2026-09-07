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
        assertTrue(p.details, p.details.contains("Due Saturday, October 10."))
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
    fun `common words are not mistaken for weekdays or subjects`() {
        val p = parser.parse("pay money for the fridge")
        assertNull(p.dueDate)
        assertEquals("Pay money for the fridge", p.title)
        val q = parser.parse("do it by friday")
        assertEquals("Do it", q.title)
    }

    @Test
    fun `page numbers are not read as times`() {
        val p = parser.parse("read page 32")
        assertNull(p.dueTime)
        assertNull(p.dueDate)
    }

    @Test
    fun `english school shortcuts are expanded`() {
        val p = parser.parse("M T3 p32 till tmrw")
        assertEquals("Math task 3 page 32", p.title)
        assertEquals(today.plusDays(1), p.dueDate)
        val q = parser.parse("hist ch 5 read + qs 1-3 by mon")
        assertEquals("History chapter 5 read + question 1-3", q.title)
        assertEquals(DayOfWeek.MONDAY, q.dueDate?.dayOfWeek)
    }

    @Test
    fun `german school shortcuts are expanded in german`() {
        val p = parser.parse("D HA S.45 Nr 3-5 bis Do")
        assertEquals("Deutsch-Hausaufgabe: Seite 45 Nr. 3-5", p.title)
        assertEquals(DayOfWeek.THURSDAY, p.dueDate?.dayOfWeek)
        assertTrue(p.details, p.details.startsWith("Seite 45 Nr. 3-5 für Deutsch."))
        assertTrue(p.details, p.details.contains("Fällig am Donnerstag, "))
        assertEquals("📚", p.emoji)
    }

    @Test
    fun `more german shortcuts`() {
        val p = parser.parse("Bio AB fertig machen bis Montag")
        assertEquals("Biologie Arbeitsblatt fertig machen", p.title)
        assertEquals(DayOfWeek.MONDAY, p.dueDate?.dayOfWeek)
        val q = parser.parse("E Vok Unit 4 lernen bis Fr")
        assertEquals("Englisch Vokabeln Unit 4 lernen", q.title)
        assertEquals(DayOfWeek.FRIDAY, q.dueDate?.dayOfWeek)
        val r = parser.parse("Ph Kap. 4 lesen und A 2 bis übermorgen um 18 Uhr")
        assertEquals("Physik Kapitel 4 lesen und Aufgabe 2", r.title)
        assertEquals(today.plusDays(2), r.dueDate)
        assertEquals(LocalTime.of(18, 0), r.dueTime)
        val s = parser.parse("M KA am 14.10.")
        assertEquals("Mathe Klassenarbeit", s.title)
        assertEquals(LocalDate.of(2026, 10, 14), s.dueDate)
        assertEquals("📝", s.emoji)
    }

    @Test
    fun `german routine wording`() {
        val p = parser.parse("Tabletten jeden Morgen und Abend")
        assertEquals(ReminderKind.ROUTINE, p.kind)
        assertEquals(listOf(DayPart.MORNING, DayPart.EVENING), p.dayParts)
        assertTrue(p.details, p.details.endsWith("Jeden Tag morgens und abends."))
    }

    @Test
    fun `german input with homework words works too`() {
        val p = parser.parse("mathe hausaufgaben s.12 bis freitag")
        assertEquals(DayOfWeek.FRIDAY, p.dueDate?.dayOfWeek)
        assertEquals("Mathe-Hausaufgabe: Seite 12", p.title)
    }

    @Test
    fun `distance treats swapped letters as one edit`() {
        assertEquals(1, LocalParser.distance("teusday", "tuesday"))
        assertEquals(1, LocalParser.distance("englisj", "english"))
        assertEquals(0, LocalParser.distance("friday", "friday"))
    }
}
