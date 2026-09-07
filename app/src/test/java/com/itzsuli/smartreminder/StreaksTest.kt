package com.itzsuli.smartreminder

import com.itzsuli.smartreminder.data.Streaks
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreaksTest {

    private val today = LocalDate.of(2026, 9, 7)

    @Test
    fun `counts consecutive days ending today`() {
        val history = listOf("2026-09-05", "2026-09-06", "2026-09-07")
        assertEquals(3, Streaks.current(history, today))
    }

    @Test
    fun `yesterday keeps the streak alive when today is not done yet`() {
        val history = listOf("2026-09-05", "2026-09-06")
        assertEquals(2, Streaks.current(history, today))
    }

    @Test
    fun `a gap breaks the streak`() {
        val history = listOf("2026-09-03", "2026-09-04", "2026-09-06")
        assertEquals(1, Streaks.current(history, today))
        assertEquals(2, Streaks.best(history))
    }

    @Test
    fun `with adds and removes days`() {
        val h1 = Streaks.with(emptyList(), today, true)
        assertEquals(listOf("2026-09-07"), h1)
        assertEquals(emptyList<String>(), Streaks.with(h1, today, false))
    }
}
