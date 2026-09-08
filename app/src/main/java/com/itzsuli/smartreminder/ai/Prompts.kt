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

    /** Shortcuts the user mixes into notes, in German and English. Shared by every engine. */
    const val GLOSSARY = """
Shortcut glossary (the user writes in German, English or a mix; expand every shortcut, never leave it as-is):
- Subjects: D/Dt/Deu = Deutsch (German) · E/En/Eng/Engl = Englisch (English) · M/Ma/Mathe = Mathe (Math) · F/Frz/Franz = Französisch (French) · L/Lat = Latein (Latin) · Spa/Span = Spanisch (Spanish) · Ita = Italienisch (Italian) · B/Bio = Biologie (Biology) · Ch/Che/Chem = Chemie (Chemistry) · Ph/Phy/Phys = Physik (Physics) · G/Ge/Gesch/Hist = Geschichte (History) · Ek/Erd/Geo = Erdkunde/Geographie (Geography) · K/Ku = Kunst (Art) · Mu/Mus = Musik (Music) · Sp/Spo/Sport/PE = Sport (PE) · R/Rel/Reli = Religion · Eth = Ethik (Ethics) · Pol/PoWi/Sk/SoWi = Politik/Sozialkunde (Politics) · Inf/Info/IT/CS = Informatik (Computer Science) · Wi/WiPo/Eco = Wirtschaft (Economics) · NaWi = Naturwissenschaften (Science) · Phil/Philo = Philosophie · Psy = Psychologie · Päd = Pädagogik · GL = Gesellschaftslehre · Tech = Technik · Chin = Chinesisch · Russ = Russisch · Griech = Griechisch · DS = Darstellendes Spiel (Drama) · Lit = Literatur · Stats = Statistik. Single letters (D, E, M, F, L, B, G, K, R) are subject codes only when the note is clearly about school work.
- School work: HA/Hausi/HW/h/w = Hausaufgabe(n) (homework) · AB = Arbeitsblatt (worksheet) · S./S/Seite/p./p/pg/pp = Seite (page), e.g. "S.45" or "p32" · Nr./Nr/No./#/Nummer = Nummer (number) · A/Aufg./Aufgabe/T/Task/Ex/Ü/Üb/Übung = Aufgabe (task/exercise), so "A3", "T3", "Aufg. 3", "Nr. 3" all mean task 3 · "Nr 3-5" = tasks 3 to 5 · Kap./Kap/Ch./Chap = Kapitel (chapter) · Vok/Vokabeln/Vocab = Vokabeln (vocabulary) · KA/Klassenarbeit/Klausur/Schulaufgabe/LZK/Ex/Exam/Test = Klassenarbeit (exam/test) · Ref/Referat/Präsi/Präsentation/PPT/Pres = Referat (presentation) · Abg./Abgabe = Abgabe (hand-in) · Prot/Protokoll = Protokoll (report) · Zsf/Zsmf = Zusammenfassung (summary) · Wdh = Wiederholung (revision) · Lös. = Lösungen (solutions) · TB = Textbook · WB = Workbook · Heft = notebook · Unit/Lektion = unit/lesson · lernen = study · lesen = read · abschreiben = copy · ausfüllen = fill in · bearbeiten = work on · fertig machen = finish · vorbereiten = prepare · üben = practise.
- Time: bis = until/by · spätestens = at the latest · Mo/Di/Mi/Do/Fr/Sa/So = Montag…Sonntag · heute = today · morgen = tomorrow · übermorgen = day after tomorrow · nächste Woche = next week · in 3 Tagen = in 3 days · um 15 Uhr = at 15:00 · WE/Wochenende/wknd = weekend · tmrw/tmr = tomorrow · nxt wk = next week · eod = end of day · asap · vorm./nachm./abends = morning/afternoon/evening · täglich/jeden Tag/daily = every day.
"""

    fun cloudSystem(now: ZonedDateTime, dateOrder: DateOrder): String {
        val dayText = now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH))
        val iso = now.toLocalDate().toString()
        val time = now.format(DateTimeFormatter.ofPattern("HH:mm"))
        val order = when (dateOrder) {
            DateOrder.DAY_FIRST -> "day.month (so 10.10 means October 10 and 3.5 means May 3)"
            DateOrder.MONTH_FIRST -> "month/day (so 10/12 means October 12)"
        }
        val thursday = nextWeekday(now.toLocalDate(), java.time.DayOfWeek.THURSDAY)
        val tomorrow = now.toLocalDate().plusDays(1)
        val thursdayDe = thursday.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN))
        val tomorrowEn = tomorrow.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH))
        return """
            You turn quick, messy notes into clear reminders for a personal reminder app. Notes arrive with typos, abbreviations, school shortcuts and sometimes mixed German and English, for example "HW englisj read page 32 till teusday 10.10" or "D HA S.45 Nr 3-5 bis Do".

            Context: today is $dayText (ISO $iso). Local time is $time, time zone ${now.zone.id}. The user writes numeric dates as $order.

            $GLOSSARY
            Language: write title and details in the language the note is mostly written in. A German note gets German output with proper German words (Hausaufgabe, Seite, Nummer, Aufgabe, Kapitel, Klassenarbeit, "Fällig am Donnerstag, 11. September"). An English note gets English output ("Due Thursday, September 11"). Never mix languages inside one reminder unless the note itself is a mix of both.

            Fill the JSON fields like this:
            - title: short and specific, at most 8 words, no trailing period. Fix typos and expand every shortcut from the glossary. For school work put the subject first, e.g. "English homework: read page 32" or "Deutsch-Hausaufgabe: Seite 45 Nr. 3–5".
            - details: one or two friendly, complete sentences that keep every piece of information from the note (page, task numbers, chapter, what to do). Mention the due date in words when there is one: English month first ("Due Saturday, October 10 at 14:00"), German the natural way ("Fällig am Samstag, 10. Oktober um 14:00 Uhr").
            - due_date: ISO yyyy-mm-dd, or "" when the note has no date. A weekday name or abbreviation (Do, Fr, mon, tues…) means its next occurrence. If both a weekday and a numeric date are present, the numeric date wins. A numeric date without a year is this year, or next year if that day already passed.
            - due_time: 24-hour HH:MM, or "" when no time is given ("um 15 Uhr" → "15:00", "5pm" → "17:00").
            - kind: "deadline" for work that has to get done by a date (homework, exams to study for, essays, errands, calls, hand-ins). "routine" for things repeated every day or regularly (supplements, medication, habits, practice, drinking water). "event" for things that simply happen at a set date or time and the user just attends (doctor/dentist/Arzt/Zahnarzt appointments, Termine, meetings, birthdays, parties, concerts, trips, flights, lessons, school events).
            - day_parts: for routines only, when the note says when in the day it happens: any of "morning", "midday", "evening". Otherwise an empty list.
            - emoji: exactly one emoji that fits the reminder.

            Examples (today's dates already applied):
            Note: "D HA S.45 Nr 3-5 bis Do"
            → {"title": "Deutsch-Hausaufgabe: Seite 45 Nr. 3–5", "details": "Auf Seite 45 die Aufgaben 3 bis 5 für Deutsch bearbeiten. Fällig am $thursdayDe.", "due_date": "$thursday", "due_time": "", "kind": "deadline", "day_parts": [], "emoji": "📚"}
            Note: "M T3 p32 till tmrw"
            → {"title": "Math: task 3 on page 32", "details": "Do task 3 on page 32 for Math. Due $tomorrowEn.", "due_date": "$tomorrow", "due_time": "", "kind": "deadline", "day_parts": [], "emoji": "📐"}
            Note: "Vit D jeden morgen"
            → {"title": "Vitamin D", "details": "Jeden Morgen Vitamin D nehmen.", "due_date": "", "due_time": "", "kind": "routine", "day_parts": ["morning"], "emoji": "💊"}
            Note: "Zahnarzt Do 10:30"
            → {"title": "Zahnarzt", "details": "Zahnarzttermin am $thursdayDe um 10:30 Uhr.", "due_date": "$thursday", "due_time": "10:30", "kind": "event", "day_parts": [], "emoji": "🦷"}

            Never invent dates, times or details that are not in the note.
        """.trimIndent()
    }

    private fun nextWeekday(from: LocalDate, target: java.time.DayOfWeek): LocalDate {
        var diff = (target.value - from.dayOfWeek.value + 7) % 7
        if (diff == 0) diff = 7
        return from.plusDays(diff.toLong())
    }

    /** Compact prompt for the small on-device model. Dates come from the offline parser, so the model only rewrites text. */
    fun nanoPrompt(input: String, hints: ParsedReminder): String {
        val facts = buildList {
            add("type: " + when (hints.kind) {
                ReminderKind.ROUTINE -> "daily routine"
                ReminderKind.EVENT -> "event / appointment (the user just attends it)"
                ReminderKind.DEADLINE -> "one-off deadline"
            })
            hints.dueDate?.let { add("due date: " + it.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH))) }
            hints.dueTime?.let { add("time: " + it.format(DateTimeFormatter.ofPattern("HH:mm"))) }
            if (hints.dayParts.isNotEmpty()) add("part of day: " + hints.dayParts.joinToString(", ") { it.label.lowercase(Locale.ROOT) })
        }
        return """
            Rewrite a messy reminder note into a clean reminder. Fix typos and expand shortcuts. Keep the language of the note (German note → German output, English note → English output). Do not add information that is not in the note.

            Shortcuts: HA/HW = Hausaufgabe/homework, S./p. = Seite/page, Nr. = Nummer/number, A3/T3/Aufg. 3 = Aufgabe 3/task 3, Kap./Ch. = Kapitel/chapter, AB = Arbeitsblatt, Vok = Vokabeln, KA/Klausur = Klassenarbeit/exam, Ref/Präsi = Referat/presentation. Subjects: D = Deutsch, E = Englisch/English, M = Mathe/Math, F = Französisch, L = Latein, Bio = Biologie, Ch = Chemie, Ph = Physik, Ge = Geschichte, Ek = Erdkunde, Ku = Kunst, Mu = Musik, Sp = Sport, Inf = Informatik.

            Known facts about this note (use them exactly, do not change them): ${facts.joinToString(" | ")}

            Note: "${input.trim()}"

            Reply with only a JSON object in this exact shape and nothing else:
            {"title": "short specific title, at most 8 words, subject first for school work", "details": "one or two friendly complete sentences, mention the due date in words if there is one", "emoji": "one fitting emoji"}

            Example. Note: "HW englisj read page 32 till teusday 10.10" with due date Saturday, October 10
            {"title": "English homework: read page 32", "details": "Read page 32 for English. Due Saturday, October 10.", "emoji": "📚"}
            Example. Note: "D HA S.45 Nr 3-5 bis Do" with due date Thursday, September 11
            {"title": "Deutsch-Hausaufgabe: Seite 45 Nr. 3–5", "details": "Auf Seite 45 die Aufgaben 3 bis 5 für Deutsch bearbeiten. Fällig am Donnerstag, 11. September.", "emoji": "📚"}
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
        val kind = when (str("kind").lowercase(Locale.ROOT)) {
            "routine" -> ReminderKind.ROUTINE
            "event" -> ReminderKind.EVENT
            else -> ReminderKind.DEADLINE
        }
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
            dueDate = if (kind != ReminderKind.ROUTINE) date else null,
            dueTime = time,
            kind = kind,
            emoji = str("emoji").ifBlank { when (kind) { ReminderKind.ROUTINE -> "🔁"; ReminderKind.EVENT -> "📅"; ReminderKind.DEADLINE -> "📌" } }.take(4),
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
                putJsonArray("enum") { add("deadline"); add("routine"); add("event") }
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
                putJsonArray("enum") { add("deadline"); add("routine"); add("event") }
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
