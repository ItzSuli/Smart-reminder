package com.itzsuli.smartreminder

import com.itzsuli.smartreminder.io.IcsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class IcsParserTest {

    private val today = LocalDate.of(2026, 9, 7)
    private val zone: ZoneId = ZoneId.of("Europe/Berlin")

    private val sample = """
        BEGIN:VCALENDAR
        VERSION:2.0
        BEGIN:VEVENT
        UID:1@school
        SUMMARY:Math exam
        DTSTART;TZID=Europe/Berlin:20261012T090000
        DESCRIPTION:Chapters 1-3\, bring a calculator
        END:VEVENT
        BEGIN:VEVENT
        UID:2@school
        SUMMARY:Project hand-in with a very long
          folded title
        DTSTART;VALUE=DATE:20261020
        END:VEVENT
        BEGIN:VEVENT
        UID:3@school
        SUMMARY:Old event
        DTSTART:20250101T100000Z
        END:VEVENT
        END:VCALENDAR
    """.trimIndent().replace("\n", "\r\n")

    @Test
    fun `reads timed and all-day events and drops past ones`() {
        val events = IcsParser.parse(sample, zone, today)
        assertEquals(2, events.size)
        assertEquals("Math exam", events[0].title)
        assertEquals(LocalDate.of(2026, 10, 12), events[0].date)
        assertEquals(LocalTime.of(9, 0), events[0].time)
        assertEquals("Chapters 1-3, bring a calculator", events[0].details)
        assertEquals("Project hand-in with a very long folded title", events[1].title)
        assertEquals(LocalDate.of(2026, 10, 20), events[1].date)
        assertNull(events[1].time)
    }

    @Test
    fun `utc times are converted to the local zone`() {
        val text = "BEGIN:VEVENT\nSUMMARY:Call\nDTSTART:20261001T120000Z\nEND:VEVENT"
        val e = IcsParser.parse(text, zone, today).single()
        assertEquals(LocalTime.of(14, 0), e.time)
    }
}
