package com.itzsuli.smartreminder.ai

import com.itzsuli.smartreminder.data.DateOrder
import com.itzsuli.smartreminder.data.DayPart
import com.itzsuli.smartreminder.data.ParseSource
import com.itzsuli.smartreminder.data.ParsedReminder
import com.itzsuli.smartreminder.data.ReminderKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Prompt text and JSON handling shared by every AI engine. */
object Prompts {

    fun cloudSystem(now: ZonedDateTime, dateOrder: DateOrder): String {
        val dayText = now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH))
        val iso = now.toLocalDate().toString()
        val time = now.format(DateTimeFormatter.ofPattern("HH:mm"))
        val order = when (dateOrder) {
            DateOrder.DAY_FIRST -> "day.month (so 10.10 means October 10 and 3.5 means May 3)"
            DateOrder.MONTH_FIRST -> "month/day (so 10/12 means October 12)"
        }
        return """
            You turn quick, messy notes into clear reminders for a personal reminder app. Notes arrive with typos, abbreviations and sometimes mixed languages, for example "HW englisj read page 32 till teusday 10.10".

            Context: today is $dayText (ISO $iso). Local time is $time, time zone ${now.zone.id}. The user writes numeric dates as $order.

            Fill the JSON fields like this:
            - title: short and specific, at most 8 words, no trailing period. Fix typos and expand abbreviations (HW = homework, pg = page, ch = chapter). For school work put the subject first, e.g. "English homework: read page 32". Keep the language of the note.
            - details: one or two friendly, complete sentences that keep every piece of information from the note. Mention the due date in words, month name first, when there is one, e.g. "Read page 32 for English. Due Saturday, October 10."
            - due_date: ISO yyyy-mm-dd, or "" when the note has no date. A weekday name means its next occurrence. If both a weekday and a numeric date are present, the numeric date wins. A numeric date without a year is this year, or next year if that day already passed.
            - due_time: 24-hour HH:MM, or "" when no time is given.
            - kind: "deadline" for anything that is done once (homework, exams, appointments, errands, calls). "routine" for things repeated every day or regularly (supplements, medication, habits, practice, drinking water).
            - day_parts: for routines only, when the note says when in the day it happens: any of "morning", "midday", "evening". Otherwise an empty list.
            - emoji: exactly one emoji that fits the reminder.

            Never invent dates, times or details that are not in the note.
        """.trimIndent()
    }

    /** Compact prompt for the small on-device model. Dates come from the offline parser, so the model only rewrites text. */
    fun nanoPrompt(input: String, hints: ParsedReminder): String {
        val facts = buildList {
            add("type: " + if (hints.kind == ReminderKind.ROUTINE) "daily routine" else "one-off deadline")
            hints.dueDate?.let { add("due date: " + it.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH))) }
            hints.dueTime?.let { add("time: " + it.format(DateTimeFormatter.ofPattern("HH:mm"))) }
            if (hints.dayParts.isNotEmpty()) add("part of day: " + hints.dayParts.joinToString(", ") { it.label.lowercase(Locale.ROOT) })
        }
        return """
            Rewrite a messy reminder note into a clean reminder. Fix typos, expand abbreviations (HW = homework, pg = page, ch = chapter, tmrw = tomorrow). Keep the language of the note. Do not add information that is not in the note.

            Known facts about this note (use them exactly, do not change them): ${facts.joinToString(" | ")}

            Note: "${input.trim()}"

            Reply with only a JSON object in this exact shape and nothing else:
            {"title": "short specific title, at most 8 words, subject first for school work", "details": "one or two friendly complete sentences, mention the due date in words (month name first) if there is one", "emoji": "one fitting emoji"}

            Example. Note: "HW englisj read page 32 till teusday 10.10" with due date Saturday, October 10
            {"title": "English homework: read page 32", "details": "Read page 32 for English. Due Saturday, October 10.", "emoji": "📚"}
        """.trimIndent()
    }

    /** Pulls the first JSON object out of a model reply that may contain fences or chatter. */
    fun extractJson(text: String): JsonObject? {
        val cleaned = text.replace("```json", "").replace("```", "").trim()
        val start = cleaned.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until cleaned.length) {
            val c = cleaned[i]
            if (inString) {
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return runCatching { Json.parseToJsonElement(cleaned.substring(start, i + 1)).jsonObject }.getOrNull()
                    }
                }
            }
        }
        return null
    }

    fun toParsed(o: JsonObject, source: ParseSource): ParsedReminder {
        fun str(key: String) = o[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val kind = if (str("kind").equals("routine", ignoreCase = true)) ReminderKind.ROUTINE else ReminderKind.DEADLINE
        val date = str("due_date").takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val time = str("due_time").takeIf { it.isNotEmpty() }?.let { t ->
            runCatching { LocalTime.parse(if (t.length == 4) "0$t" else t) }.getOrNull()
        }
        val parts = runCatching {
            o["day_parts"]?.jsonArray?.mapNotNull { e ->
                when (e.jsonPrimitive.contentOrNull?.lowercase(Locale.ROOT)) {
                    "morning" -> DayPart.MORNING
                    "midday" -> DayPart.MIDDAY
                    "evening" -> DayPart.EVENING
                    else -> null
                }
            }
        }.getOrNull().orEmpty().distinct()
        return ParsedReminder(
            title = str("title").ifBlank { "Reminder" },
            details = str("details"),
            dueDate = if (kind == ReminderKind.DEADLINE) date else null,
            dueTime = time,
            kind = kind,
            emoji = str("emoji").ifBlank { if (kind == ReminderKind.ROUTINE) "🔁" else "📌" }.take(4),
            dayParts = if (kind == ReminderKind.ROUTINE) parts else emptyList(),
            source = source,
        )
    }

    /** JSON Schema (draft style) used by Claude's structured output. */
    val JSON_SCHEMA: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("title") { put("type", "string") }
            putJsonObject("details") { put("type", "string") }
            putJsonObject("due_date") { put("type", "string") }
            putJsonObject("due_time") { put("type", "string") }
            putJsonObject("kind") {
                put("type", "string")
                putJsonArray("enum") { add("deadline"); add("routine") }
            }
            putJsonObject("day_parts") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "string")
                    putJsonArray("enum") { add("morning"); add("midday"); add("evening") }
                }
            }
            putJsonObject("emoji") { put("type", "string") }
        }
        put("required", buildJsonArray {
            add("title"); add("details"); add("due_date"); add("due_time"); add("kind"); add("day_parts"); add("emoji")
        })
        put("additionalProperties", false)
    }

    /** OpenAPI-style schema used by the Gemini API's responseSchema. */
    val GEMINI_SCHEMA: JsonObject = buildJsonObject {
        put("type", "OBJECT")
        putJsonObject("properties") {
            putJsonObject("title") { put("type", "STRING") }
            putJsonObject("details") { put("type", "STRING") }
            putJsonObject("due_date") { put("type", "STRING") }
            putJsonObject("due_time") { put("type", "STRING") }
            putJsonObject("kind") {
                put("type", "STRING")
                putJsonArray("enum") { add("deadline"); add("routine") }
            }
            putJsonObject("day_parts") {
                put("type", "ARRAY")
                putJsonObject("items") {
                    put("type", "STRING")
                    putJsonArray("enum") { add("morning"); add("midday"); add("evening") }
                }
            }
            putJsonObject("emoji") { put("type", "STRING") }
        }
        put("required", buildJsonArray {
            add("title"); add("details"); add("due_date"); add("due_time"); add("kind"); add("day_parts"); add("emoji")
        })
        put("propertyOrdering", buildJsonArray {
            add("title"); add("details"); add("due_date"); add("due_time"); add("kind"); add("day_parts"); add("emoji")
        })
    }
}
