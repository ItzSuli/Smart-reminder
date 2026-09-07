package com.itzsuli.smartreminder.io

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Minimal iCalendar (.ics) reader: enough for timetables and exam schedules exported from
 * school systems. Reads SUMMARY, DTSTART and DESCRIPTION of every VEVENT; recurring events
 * contribute their first occurrence only.
 */
object IcsParser {

    private val DATE = DateTimeFormatter.BASIC_ISO_DATE
    private val DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now()): List<ImportEvent> {
        val out = mutableListOf<ImportEvent>()
        var inEvent = false
        val props = mutableMapOf<String, Pair<String, String>>() // NAME -> (params, value)
        for (line in unfold(text)) {
            when {
                line.equals("BEGIN:VEVENT", ignoreCase = true) -> { inEvent = true; props.clear() }
                line.equals("END:VEVENT", ignoreCase = true) -> {
                    if (inEvent) build(props, zone)?.takeIf { !it.date.isBefore(today) }?.let(out::add)
                    inEvent = false
                }
                inEvent -> {
                    val colon = line.indexOf(':')
                    if (colon <= 0) continue
                    val head = line.substring(0, colon)
                    val value = line.substring(colon + 1)
                    val name = head.substringBefore(';').uppercase()
                    val params = head.substringAfter(';', "").uppercase()
                    props[name] = params to value
                }
            }
        }
        return out.distinctBy { it.key }.sortedWith(compareBy({ it.date }, { it.time }))
    }

    private fun unfold(text: String): List<String> {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val out = mutableListOf<String>()
        for (line in lines) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && out.isNotEmpty()) {
                out[out.lastIndex] = out.last() + line.substring(1)
            } else {
                out += line
            }
        }
        return out
    }

    private fun build(props: Map<String, Pair<String, String>>, zone: ZoneId): ImportEvent? {
        val summary = props["SUMMARY"]?.second?.let(::unescape)?.trim().orEmpty()
        if (summary.isEmpty()) return null
        val (params, raw) = props["DTSTART"] ?: return null
        val (date, time) = parseDateTime(raw.trim(), params, zone) ?: return null
        val uid = props["UID"]?.second?.trim().orEmpty()
        return ImportEvent(
            key = "ics:" + uid.ifEmpty { "$summary@$raw" },
            title = summary,
            date = date,
            time = time,
            details = props["DESCRIPTION"]?.second?.let(::unescape)?.trim().orEmpty(),
            source = "Calendar file",
        )
    }

    private fun parseDateTime(raw: String, params: String, zone: ZoneId): Pair<LocalDate, LocalTime?>? {
        if (params.contains("VALUE=DATE") && !params.contains("DATE-TIME") || raw.length == 8) {
            return runCatching { LocalDate.parse(raw, DATE) to null }.getOrNull()
        }
        val utc = raw.endsWith("Z")
        val body = raw.removeSuffix("Z")
        val local = runCatching { LocalDateTime.parse(body, DATE_TIME) }.getOrNull() ?: return null
        val zoned = if (utc) local.atZone(ZoneOffset.UTC).withZoneSameInstant(zone) else local.atZone(zone)
        return zoned.toLocalDate() to zoned.toLocalTime().withSecond(0).withNano(0)
    }

    private fun unescape(s: String): String =
        s.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")
}
