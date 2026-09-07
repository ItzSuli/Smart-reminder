package com.itzsuli.smartreminder.data

import java.time.LocalDate

object Streaks {

    private const val MAX_HISTORY = 400

    private fun days(history: List<String>): Set<LocalDate> =
        history.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()

    /** Consecutive days done, counting back from today (or from yesterday if today isn't done yet). */
    fun current(history: List<String>, today: LocalDate = LocalDate.now()): Int {
        val done = days(history)
        var day = if (today in done) today else today.minusDays(1)
        var n = 0
        while (day in done) {
            n++
            day = day.minusDays(1)
        }
        return n
    }

    fun best(history: List<String>): Int {
        val done = days(history).sorted()
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (d in done) {
            run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
            best = maxOf(best, run)
            prev = d
        }
        return best
    }

    fun with(history: List<String>, day: LocalDate, done: Boolean): List<String> {
        val iso = day.toString()
        val next = if (done) (history + iso).distinct() else history - iso
        return next.sorted().takeLast(MAX_HISTORY)
    }
}
