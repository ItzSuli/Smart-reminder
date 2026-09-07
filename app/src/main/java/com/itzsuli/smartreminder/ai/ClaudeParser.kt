package com.itzsuli.smartreminder.ai

import com.itzsuli.smartreminder.data.DateOrder
import com.itzsuli.smartreminder.data.DayPart
import com.itzsuli.smartreminder.data.ParseSource
import com.itzsuli.smartreminder.data.ParsedReminder
import com.itzsuli.smartreminder.data.ReminderKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Calls the Claude Messages API directly over HTTPS and asks for a strict JSON object
 * (structured outputs), so the reply can be dropped straight into the editor.
 */
class ClaudeParser(private val apiKey: String, private val model: String) {

    class ClaudeException(message: String) : IOException(message)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Result of a test call: the model that answered. */
    data class Ping(val model: String, val sample: ParsedReminder)

    suspend fun ping(dateOrder: DateOrder): Ping {
        val (parsed, answeredBy) = request("HW englisj read page 32 till teusday", dateOrder, ZonedDateTime.now())
        return Ping(answeredBy, parsed)
    }

    suspend fun parse(input: String, dateOrder: DateOrder, now: ZonedDateTime = ZonedDateTime.now()): ParsedReminder =
        request(input, dateOrder, now).first

    private suspend fun request(input: String, dateOrder: DateOrder, now: ZonedDateTime): Pair<ParsedReminder, String> =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("model", model)
                put("max_tokens", 1024)
                put("fallbacks", "default")
                put("system", systemPrompt(now, dateOrder))
                putJsonObject("output_config") {
                    put("effort", "medium")
                    putJsonObject("format") {
                        put("type", "json_schema")
                        put("schema", SCHEMA)
                    }
                }
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "user")
                        put("content", input.trim())
                    }
                }
            }
            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .header("x-api-key", apiKey.trim())
                .header("anthropic-version", "2023-06-01")
                .header("anthropic-beta", "server-side-fallback-2026-07-01")
                .header("content-type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = try {
                client.newCall(request).execute()
            } catch (e: IOException) {
                throw ClaudeException("no connection: ${e.message ?: e.javaClass.simpleName}")
            }
            response.use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw ClaudeException(describeError(resp.code, text))
                val root = runCatching { Json.parseToJsonElement(text).jsonObject }
                    .getOrElse { throw ClaudeException("unreadable reply") }
                val stop = root["stop_reason"]?.jsonPrimitive?.contentOrNull
                if (stop == "refusal") throw ClaudeException("Claude declined this note")
                val content = root["content"]?.jsonArray ?: throw ClaudeException("empty reply")
                val jsonText = content
                    .map { it.jsonObject }
                    .firstOrNull { it["type"]?.jsonPrimitive?.contentOrNull == "text" }
                    ?.get("text")?.jsonPrimitive?.contentOrNull
                    ?: throw ClaudeException("no text in reply")
                val parsed = runCatching { toParsed(Json.parseToJsonElement(jsonText).jsonObject) }
                    .getOrElse { throw ClaudeException("reply was not valid JSON") }
                parsed to (root["model"]?.jsonPrimitive?.contentOrNull ?: model)
            }
        }

    private fun toParsed(o: JsonObject): ParsedReminder {
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
            dueTime = if (kind == ReminderKind.DEADLINE) time else null,
            kind = kind,
            emoji = str("emoji").ifBlank { if (kind == ReminderKind.ROUTINE) "🔁" else "📌" }.take(4),
            dayParts = if (kind == ReminderKind.ROUTINE) parts else emptyList(),
            source = ParseSource.CLAUDE,
        )
    }

    private fun describeError(code: Int, body: String): String {
        val apiMessage = runCatching {
            Json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return when (code) {
            401 -> "API key rejected (401)"
            403 -> "permission denied (403)"
            404 -> "model \"$model\" not found (404)"
            429 -> "rate limit hit (429), try again in a moment"
            529 -> "Claude is overloaded right now (529)"
            in 500..599 -> "Claude servers had a problem ($code)"
            else -> apiMessage?.let { "$it ($code)" } ?: "HTTP $code"
        }
    }

    private fun systemPrompt(now: ZonedDateTime, dateOrder: DateOrder): String {
        val dayText = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH))
        val iso = now.toLocalDate().toString()
        val time = now.format(DateTimeFormatter.ofPattern("HH:mm"))
        val order = when (dateOrder) {
            DateOrder.DAY_FIRST -> "day.month (so 10.10 means 10 October and 3.5 means 3 May)"
            DateOrder.MONTH_FIRST -> "month/day (so 10/12 means October 12)"
        }
        return """
            You turn quick, messy notes into clear reminders for a personal reminder app. Notes arrive with typos, abbreviations and sometimes mixed languages, for example "HW englisj read page 32 till teusday 10.10".

            Context: today is $dayText (ISO $iso). Local time is $time, time zone ${now.zone.id}. The user writes numeric dates as $order.

            Fill the JSON fields like this:
            - title: short and specific, at most 8 words, no trailing period. Fix typos and expand abbreviations (HW = homework, pg = page, ch = chapter). For school work put the subject first, e.g. "English homework: read page 32". Keep the language of the note.
            - details: one or two friendly, complete sentences that keep every piece of information from the note. Mention the due date in words when there is one, e.g. "Read page 32 for English. Due Tuesday, 10 October."
            - due_date: ISO yyyy-mm-dd, or "" when the note has no date. A weekday name means its next occurrence. If both a weekday and a numeric date are present, the numeric date wins. A numeric date without a year is this year, or next year if that day already passed.
            - due_time: 24-hour HH:MM, or "" when no time is given.
            - kind: "deadline" for anything that is done once (homework, exams, appointments, errands, calls). "routine" for things repeated every day or regularly (supplements, medication, habits, practice, drinking water).
            - day_parts: for routines only, when the note says when in the day it happens: any of "morning", "midday", "evening". Otherwise an empty list.
            - emoji: exactly one emoji that fits the reminder.

            Never invent dates, times or details that are not in the note.
        """.trimIndent()
    }

    private companion object {
        val SCHEMA: JsonObject = buildJsonObject {
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
    }
}
