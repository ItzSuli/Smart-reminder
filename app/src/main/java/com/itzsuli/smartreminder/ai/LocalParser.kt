package com.itzsuli.smartreminder.ai

import com.itzsuli.smartreminder.data.DayPart
import com.itzsuli.smartreminder.data.ParseSource
import com.itzsuli.smartreminder.data.ParsedReminder
import com.itzsuli.smartreminder.data.ReminderKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Offline fallback for turning shorthand like "HW englisj read page 32 till teusday 10.10" or
 * "D HA S.45 Nr 3-5 bis Do" into something readable. It is deliberately forgiving: fuzzy weekday
 * and subject matching, school shortcuts in German and English (T3, Nr, S., Kap., HA, AB, Vok…),
 * subject codes (D, E, M, Bio, Ch, Ph, Ge, Ek, Ku, Mu…), several date formats.
 *
 * The AI engines do a nicer job of wording; this exists so the app works without a key or network.
 */
class LocalParser(
    private val dayFirst: Boolean = true,
    private val today: LocalDate = LocalDate.now(),
    @Suppress("unused") private val locale: Locale = Locale.getDefault(),
) {

    /** Words rendered in the note's language. */
    private class Vocab(val german: Boolean) {
        private val de = mapOf(
            "homework" to "Hausaufgabe", "page" to "Seite", "number" to "Nr.", "task" to "Aufgabe", "chapter" to "Kapitel",
            "exercise" to "Übung", "worksheet" to "Arbeitsblatt", "vocabulary" to "Vokabeln", "exam" to "Klassenarbeit",
            "presentation" to "Referat", "summary" to "Zusammenfassung", "revision" to "Wiederholung", "report" to "Protokoll",
            "solutions" to "Lösungen", "textbook" to "Buch", "workbook" to "Arbeitsheft", "notebook" to "Heft",
            "hand-in" to "Abgabe", "essay" to "Aufsatz", "unit" to "Unit", "question" to "Frage", "questions" to "Fragen",
            "reading" to "Lektüre", "project" to "Projekt", "appointment" to "Termin", "study" to "lernen", "read" to "lesen",
        )
        fun w(key: String): String = if (german) de[key] ?: key else key
    }

    fun parse(raw: String): ParsedReminder {
        val text = raw.trim().replace(Regex("\\s+"), " ")
        if (text.isEmpty()) {
            return ParsedReminder("Reminder", "", null, null, ReminderKind.DEADLINE, "📌", source = ParseSource.LOCAL)
        }

        val tokens = text.split(" ")
        val consumed = BooleanArray(tokens.size)
        val german = detectGerman(tokens)
        val vocab = Vocab(german)
        val names = if (german) Locale.GERMAN else Locale.ENGLISH
        var date: LocalDate? = null
        var time: LocalTime? = null
        var routineHint = false
        val dayParts = linkedSetOf<DayPart>()
        val frequencyWords = BooleanArray(tokens.size)

        // ---- pass 1: numeric dates / times ------------------------------------------------
        tokens.forEachIndexed { i, tok ->
            val t = tok.trim(',', ';', '!', '(', ')')
            explicitDate(t)?.let { date = it; consumed[i] = true; return@forEachIndexed }
            explicitTime(t)?.let { time = it; consumed[i] = true; return@forEachIndexed }
            if (i + 1 < tokens.size && t.matches(Regex("\\d{1,2}")) && norm(tokens[i + 1]) in setOf("am", "pm")) {
                explicitTime(t + norm(tokens[i + 1]))?.let { time = it; consumed[i] = true; consumed[i + 1] = true }
            }
        }
        // "at 10" / "um 15" / "um 15 Uhr" / "@ 9"
        tokens.forEachIndexed { i, tok ->
            if (consumed[i]) return@forEachIndexed
            if (norm(tok) in setOf("at", "um", "@") && i + 1 < tokens.size && !consumed[i + 1]) {
                val next = tokens[i + 1].trim(',', ';', '!', '.')
                if (next.matches(Regex("\\d{1,2}")) && next.toInt() in 0..23 && time == null) {
                    time = LocalTime.of(next.toInt(), 0)
                    consumed[i] = true; consumed[i + 1] = true
                    if (i + 2 < tokens.size && norm(tokens[i + 2]) in setOf("uhr", "h", "oclock", "o'clock")) consumed[i + 2] = true
                } else if (next.matches(Regex("\\d{1,2}(:\\d{2})?(am|pm|h|uhr)?", RegexOption.IGNORE_CASE)) && time == null) {
                    explicitTime(next)?.let { time = it; consumed[i] = true; consumed[i + 1] = true }
                }
            }
        }
        // "15:30 Uhr"
        tokens.forEachIndexed { i, tok ->
            if (norm(tok) == "uhr" && i > 0 && consumed[i - 1]) consumed[i] = true
        }

        // ---- pass 2: relative words, weekdays, routine hints ---------------------------------
        tokens.forEachIndexed { i, tok ->
            if (consumed[i]) return@forEachIndexed
            val n = norm(tok)
            val prev = if (i > 0) norm(tokens[i - 1]) else ""
            val prev2 = if (i > 1) norm(tokens[i - 2]) else ""
            when {
                n in setOf("today", "heute", "tonight", "heut") -> { date = date ?: today; consumed[i] = true }
                // "jeden Morgen" / "am Morgen" is the morning, not tomorrow
                n == "morgen" && (prev in setOf("jeden", "jede", "jeder", "am", "guten") || (tok.trim(',', '.') == "Morgen" && i > 0)) -> {
                    dayParts += DayPart.MORNING; frequencyWords[i] = true
                    if (prev.startsWith("jede")) routineHint = true
                }
                isTomorrow(n) -> {
                    if (prev == "after" && prev2 == "day") {
                        date = date ?: today.plusDays(2); consumed[i - 1] = true; consumed[i - 2] = true
                        if (i > 2 && norm(tokens[i - 3]) == "the") consumed[i - 3] = true
                    } else {
                        date = date ?: today.plusDays(1)
                    }
                    consumed[i] = true
                }
                n in setOf("übermorgen", "uebermorgen", "overmorrow") -> { date = date ?: today.plusDays(2); consumed[i] = true }
                n in setOf("week", "woche", "wk") && prev in setOf("next", "nächste", "naechste", "nächsten", "nxt") -> {
                    date = date ?: today.plusDays(7); consumed[i] = true; consumed[i - 1] = true
                }
                n in setOf("weekend", "wochenende", "wknd", "we") && (n != "we" || tok == "WE") -> {
                    date = date ?: nextWeekday(DayOfWeek.SATURDAY); consumed[i] = true
                    if (prev in setOf("this", "next", "the", "am", "on", "bis", "übers", "ubers")) consumed[i - 1] = true
                }
                n in setOf("days", "day", "tage", "tag", "tagen", "weeks", "week", "wochen", "wk") && prev.matches(Regex("\\d{1,2}")) && prev2 == "in" -> {
                    val amount = prev.toLong()
                    date = date ?: if (n.startsWith("w")) today.plusWeeks(amount) else today.plusDays(amount)
                    consumed[i] = true; consumed[i - 1] = true; consumed[i - 2] = true
                }
                else -> {
                    val wd = weekday(n, tok, german)
                    if (wd != null) {
                        if (date == null) date = nextWeekday(wd)
                        consumed[i] = true
                        if (prev in setOf("next", "this", "on", "am", "nächsten", "naechsten", "coming", "bis")) consumed[i - 1] = true
                    }
                }
            }
            if (n in ROUTINE_WORDS) { routineHint = true; frequencyWords[i] = true }
            if (n in ROUTINE_NOUNS) routineHint = true
            dayPartFor(n)?.let { dayParts += it; frequencyWords[i] = true }
        }

        tokens.forEachIndexed { i, tok ->
            val n = norm(tok)
            if (n in setOf("every", "each", "jeden", "jede", "jedes", "per", "a", "in", "the", "at", "am", "vor", "nach", "before", "after", "with", "beim", "zum") &&
                i + 1 < tokens.size && frequencyWords[i + 1]
            ) frequencyWords[i] = true
        }
        tokens.forEachIndexed { i, tok ->
            val n = norm(tok)
            if (n in setOf("day", "tag", "night", "nacht") && i > 0 && norm(tokens[i - 1]) in setOf("every", "each", "jeden", "a", "per")) {
                frequencyWords[i] = true; frequencyWords[i - 1] = true; routineHint = true
                if (n == "night" || n == "nacht") dayParts += DayPart.EVENING
            }
        }

        // ---- pass 3: deadline marker words right before a date ------------------------------
        for (i in tokens.indices) {
            if (!consumed[i]) continue
            var j = i - 1
            while (j >= 0 && !consumed[j] && norm(tokens[j]) in MARKERS) { consumed[j] = true; j-- }
        }

        // ---- kind ---------------------------------------------------------------------------
        val explicitEvery = tokens.any { norm(it) in setOf("every", "daily", "everyday", "täglich", "taeglich", "jeden") }
        val kind = when {
            explicitEvery -> ReminderKind.ROUTINE
            date != null -> ReminderKind.DEADLINE
            routineHint -> ReminderKind.ROUTINE
            else -> ReminderKind.DEADLINE
        }

        // ---- expand shortcuts, subjects, homework words --------------------------------------
        val schoolContext = tokens.any { isSchoolWord(it) }
        val rest = mutableListOf<String>()
        val restForTitle = mutableListOf<String>()
        var subject: String? = null
        var homework = false
        tokens.forEachIndexed { i, tok ->
            if (consumed[i]) return@forEachIndexed
            val next = if (i + 1 < tokens.size && !consumed[i + 1]) tokens[i + 1] else null
            val n = norm(tok)
            if (n in HOMEWORK_WORDS || (n == "ha" && (german || tok == "HA"))) {
                homework = true
                rest += vocab.w("homework"); restForTitle += vocab.w("homework")
                return@forEachIndexed
            }
            val subj = subjectFor(tok, n, german, schoolContext, next)
            if (subj != null) {
                if (subject == null) subject = subj
                rest += subj; restForTitle += subj
                return@forEachIndexed
            }
            val expanded = expand(tok, n, next, vocab, german)
            for (w in expanded.split(" ")) {
                rest += w
                if (!(kind == ReminderKind.ROUTINE && frequencyWords[i])) restForTitle += w
            }
        }

        // ---- build title + details --------------------------------------------------------
        val clean = rest.joinToString(" ").trim().trim(',', '.', ';', '-').trim()
        val cleanTitle = restForTitle.joinToString(" ").trim().trim(',', '.', ';', '-').trim()
        val subjectName = subject
        val hwWord = vocab.w("homework")
        val title: String
        val sentence: String
        if (homework) {
            val core = wordsWithout(cleanTitle, listOf(hwWord, subjectName ?: ""))
            if (german) {
                title = when {
                    subjectName != null && core.isNotBlank() -> "$subjectName-Hausaufgabe: $core"
                    subjectName != null -> "$subjectName-Hausaufgabe"
                    core.isNotBlank() -> "Hausaufgabe: $core"
                    else -> "Hausaufgabe"
                }
                sentence = when {
                    subjectName != null && core.isNotBlank() -> "${cap(core, names)} für $subjectName."
                    subjectName != null -> "Hausaufgabe für $subjectName."
                    core.isNotBlank() -> "${cap(core, names)} (Hausaufgabe)."
                    else -> "Hausaufgabe."
                }
            } else {
                title = when {
                    subjectName != null && core.isNotBlank() -> "$subjectName homework: $core"
                    subjectName != null -> "$subjectName homework"
                    core.isNotBlank() -> "Homework: $core"
                    else -> "Homework"
                }
                sentence = when {
                    subjectName != null && core.isNotBlank() -> "${cap(core, names)} for $subjectName homework."
                    subjectName != null -> "$subjectName homework."
                    core.isNotBlank() -> "${cap(core, names)} for homework."
                    else -> "Homework."
                }
            }
        } else {
            title = cap(cleanTitle.ifBlank { clean }.ifBlank { text }, names)
            sentence = cap(clean.ifBlank { text }, names).let { if (it.endsWith(".") || it.endsWith("!") || it.endsWith("?")) it else "$it." }
        }

        val dueDate = date
        val details = buildString {
            append(sentence)
            if (kind == ReminderKind.DEADLINE && dueDate != null) {
                append(" ").append(duePhrase(dueDate, time, german, names))
            } else if (kind == ReminderKind.ROUTINE) {
                append(" ").append(routinePhrase(dayParts.toList(), time, german))
            }
        }

        return ParsedReminder(
            title = shorten(title),
            details = details,
            dueDate = if (kind == ReminderKind.DEADLINE) date else null,
            dueTime = time,
            kind = kind,
            emoji = emojiFor("$clean $text", kind),
            dayParts = if (kind == ReminderKind.ROUTINE) dayParts.toList() else emptyList(),
            source = ParseSource.LOCAL,
        )
    }

    // ---------------------------------------------------------------------------------------
    // language

    private fun detectGerman(tokens: List<String>): Boolean {
        var de = 0
        var en = 0
        for (tok in tokens) {
            val n = norm(tok)
            if (tok.matches(Regex("(Mo|Di|Mi|Do|Fr|Sa|So)\\.?")) && tok.length <= 3) { de++; continue }
            if (n in GERMAN_MARKERS || n.matches(Regex("(s|nr|aufg|kap|üb)\\.?\\d+(-\\d+)?"))) de++
            if (n in ENGLISH_MARKERS || n.matches(Regex("(p|pg|pp|ch|chap)\\.?\\d+(-\\d+)?"))) en++
            if (n.any { it in "äöüß" }) de++
        }
        return de > en
    }

    // ---------------------------------------------------------------------------------------
    // helpers

    private fun norm(t: String) = t.lowercase(Locale.ROOT).trim { !it.isLetterOrDigit() && it != '/' && it != '#' }

    private fun cap(s: String, loc: Locale) = s.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(loc) else it.toString() }

    private fun shorten(s: String, max: Int = 70): String {
        if (s.length <= max) return s
        val cut = s.substring(0, max).substringBeforeLast(' ')
        return "$cut…"
    }

    private fun wordsWithout(text: String, drop: List<String>): String {
        val dropSet = drop.filter { it.isNotBlank() }.map { it.lowercase(Locale.ROOT) }.toSet()
        return text.split(" ").filter { it.lowercase(Locale.ROOT).trim('.', ',') !in dropSet }.joinToString(" ").trim().trim(',', '-', ':').trim()
    }

    private fun duePhrase(date: LocalDate, time: LocalTime?, german: Boolean, loc: Locale): String {
        val timeText = time?.format(DateTimeFormatter.ofPattern("HH:mm"))
        return if (german) {
            val dayText = when (date) {
                today -> "heute"
                today.plusDays(1) -> "morgen"
                else -> "am " + date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", loc))
            }
            "Fällig $dayText" + (timeText?.let { " um $it Uhr" } ?: "") + "."
        } else {
            val dayText = when (date) {
                today -> "today"
                today.plusDays(1) -> "tomorrow"
                else -> date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", loc))
            }
            "Due $dayText" + (timeText?.let { " at $it" } ?: "") + "."
        }
    }

    private fun routinePhrase(parts: List<DayPart>, time: LocalTime?, german: Boolean): String {
        val timeText = time?.format(DateTimeFormatter.ofPattern("HH:mm"))
        if (german) {
            if (timeText != null) return "Jeden Tag um $timeText Uhr."
            if (parts.isEmpty()) return "Jeden Tag."
            val names = parts.map { when (it) { DayPart.MORNING -> "morgens"; DayPart.MIDDAY -> "mittags"; DayPart.EVENING -> "abends" } }
            return "Jeden Tag " + joinNatural(names, "und") + "."
        }
        if (timeText != null) return "Every day at $timeText."
        if (parts.isEmpty()) return "Every day."
        return "Every day in the " + joinNatural(parts.map { it.label.lowercase(Locale.ROOT) }, "and") + "."
    }

    private fun joinNatural(items: List<String>, and: String): String = when (items.size) {
        1 -> items[0]
        2 -> items[0] + " $and " + items[1]
        else -> items.dropLast(1).joinToString(", ") + " $and " + items.last()
    }

    private fun explicitDate(t: String): LocalDate? {
        Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$").matchEntire(t)?.let { m ->
            return runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
        }
        val m = Regex("^(\\d{1,2})[./](\\d{1,2})(?:[./](\\d{2,4}))?\\.?$").matchEntire(t) ?: return null
        val a = m.groupValues[1].toInt()
        val b = m.groupValues[2].toInt()
        val yearRaw = m.groupValues[3]
        var day = if (dayFirst) a else b
        var month = if (dayFirst) b else a
        if (month !in 1..12 && day in 1..12) { val tmp = day; day = month; month = tmp }
        if (month !in 1..12 || day !in 1..31) return null
        if (yearRaw.isEmpty() && !t.contains('/') && !t.endsWith(".") && m.groupValues[1].length == 1 && m.groupValues[2].length == 1 && !dayFirst) return null
        val year = when {
            yearRaw.isEmpty() -> today.year
            yearRaw.length == 2 -> 2000 + yearRaw.toInt()
            else -> yearRaw.toInt()
        }
        var result = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return null
        if (yearRaw.isEmpty() && result.isBefore(today.minusDays(1))) result = result.plusYears(1)
        return result
    }

    private fun explicitTime(t: String): LocalTime? {
        val m = Regex("^(\\d{1,2})(?::(\\d{2}))?\\s?(am|pm|h|uhr)?$", RegexOption.IGNORE_CASE).matchEntire(t) ?: return null
        var hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].ifEmpty { null }?.toInt() ?: 0
        val suffix = m.groupValues[3].lowercase(Locale.ROOT)
        if (m.groupValues[2].isEmpty() && suffix.isEmpty()) return null
        if (suffix == "pm" && hour < 12) hour += 12
        if (suffix == "am" && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }

    private fun isTomorrow(n: String): Boolean =
        n in setOf("tomorrow", "tmrw", "tmr", "tmrrw", "tmw", "morgen", "morgn") || (n.length >= 6 && distance(n, "tomorrow") <= 2 && n.startsWith("to"))

    private fun nextWeekday(target: DayOfWeek): LocalDate {
        var diff = (target.value - today.dayOfWeek.value + 7) % 7
        if (diff == 0) diff = 7
        return today.plusDays(diff.toLong())
    }

    private fun weekday(n: String, original: String, german: Boolean): DayOfWeek? {
        WEEKDAY_EXACT[n]?.let { return it }
        // German two-letter abbreviations: "Do", "Fr", "Mo" (capitalised, with or without dot)
        if (german || original.endsWith(".")) {
            GERMAN_SHORT[original.trimEnd('.', ',')]?.let { return it }
        }
        if (n.length < 5) return null
        val threshold = if (n.length >= 8) 2 else 1
        var best: DayOfWeek? = null
        var bestDist = Int.MAX_VALUE
        for ((name, day) in WEEKDAY_FULL) {
            val d = distance(n, name)
            if (d < bestDist) { bestDist = d; best = day }
        }
        return if (bestDist <= threshold) best else null
    }

    private fun subjectFor(original: String, n: String, german: Boolean, schoolContext: Boolean, next: String?): String? {
        val bare = original.trim(',', ':', ';', '.', '-')
        // "ch 5" / "Ch. 5" is a chapter, not chemistry
        if (n in setOf("ch", "chap", "kap") && next != null && norm(next).matches(Regex("\\d+(-\\d+)?"))) return null
        val entry = when {
            bare.length == 1 && bare[0].isUpperCase() && schoolContext -> SUBJECT_BY_CODE[bare.lowercase(Locale.ROOT)]
            bare == "IT" || bare == "CS" || bare == "PE" || bare == "DS" -> SUBJECT_BY_CODE[bare.lowercase(Locale.ROOT)]
            n in setOf("it", "cs", "pe", "ds", "we", "al", "gl", "sk", "wi", "ma", "ge", "mu", "ku", "ek", "ka") ->
                if (bare.length >= 2 && bare[0].isUpperCase() && (schoolContext || german)) SUBJECT_BY_CODE[n] else null
            bare.length >= 2 -> SUBJECT_BY_CODE[n]
            else -> null
        } ?: fuzzySubject(n)
        return entry?.let { if (german) it.de else it.en }
    }

    private fun fuzzySubject(n: String): Subject? {
        if (n.length < 5) return null
        val threshold = if (n.length >= 8) 2 else 1
        var best: Subject? = null
        var bestDist = Int.MAX_VALUE
        for ((name, subject) in SUBJECT_BY_NAME) {
            val d = distance(n, name)
            if (d < bestDist) { bestDist = d; best = subject }
        }
        return if (bestDist <= threshold) best else null
    }

    private fun isSchoolWord(tok: String): Boolean {
        val n = norm(tok)
        if (n in SCHOOL_WORDS || n in HOMEWORK_WORDS) return true
        if (n.matches(Regex("(s|p|pg|pp|nr|no|a|t|aufg|kap|ch|chap|üb|ü)\\.?\\d+(-\\d+)?"))) return true
        if (tok == "HA" || tok == "AB" || tok == "KA") return true
        return n.length >= 2 && SUBJECT_BY_CODE.containsKey(n) && n !in setOf("it", "we", "al", "ma", "ge", "ka")
    }

    private fun dayPartFor(n: String): DayPart? = when (n) {
        "morning", "mornings", "morgens", "früh", "frueh", "breakfast", "frühstück", "fruehstueck", "wakeup", "wake-up", "vormittags", "vorm" -> DayPart.MORNING
        "noon", "midday", "lunch", "lunchtime", "mittag", "mittags", "afternoon", "afternoons", "nachmittag", "nachmittags", "nachm" -> DayPart.MIDDAY
        "evening", "evenings", "abend", "abends", "dinner", "bed", "bedtime", "nachts", "night", "nights" -> DayPart.EVENING
        else -> null
    }

    /** Expands one token; may return several words. */
    private fun expand(tok: String, n: String, next: String?, vocab: Vocab, german: Boolean): String {
        val nextIsNumber = next != null && norm(next).matches(Regex("\\d+([-–]\\d+)?[a-z]?"))
        val bare = tok.trim(',', ';', ':')

        // attached forms: S.45, p32, Nr3, A3, T3, Kap.4, Ü2
        Regex("^(s|p|pg|pp|seite|page)\\.?(\\d+(?:[-–]\\d+)?)$", RegexOption.IGNORE_CASE).matchEntire(bare)?.let { return vocab.w("page") + " " + it.groupValues[2] }
        Regex("^(nr|no|nummer|number|#)\\.?(\\d+(?:[-–]\\d+)?)$", RegexOption.IGNORE_CASE).matchEntire(bare)?.let { return vocab.w("number") + " " + it.groupValues[2] }
        Regex("^(a|t|aufg|aufgabe|task|ex|üb|ü|übung)\\.?(\\d+(?:[-–]\\d+)?[a-z]?)$", RegexOption.IGNORE_CASE).matchEntire(bare)?.let { return vocab.w("task") + " " + it.groupValues[2] }
        Regex("^(kap|kapitel|ch|chap|chapter)\\.?(\\d+(?:[-–]\\d+)?)$", RegexOption.IGNORE_CASE).matchEntire(bare)?.let { return vocab.w("chapter") + " " + it.groupValues[2] }
        Regex("^(q|qs|frage|fragen)\\.?(\\d+(?:[-–]\\d+)?)$", RegexOption.IGNORE_CASE).matchEntire(bare)?.let { return vocab.w("question") + " " + it.groupValues[2] }

        // standalone shortcut followed by a number: "S. 45", "Nr. 3", "Aufg. 3", "T 3" (single letters only when capitalised)
        if (nextIsNumber) {
            when (n) {
                "s", "seite", "seiten", "p", "pg", "pgs", "pp", "page", "pages" -> return vocab.w("page")
                "nr", "no", "nummer", "number", "#" -> return vocab.w("number")
                "aufg", "aufgabe", "aufgaben", "task", "tasks", "ex", "exc", "übung", "üb", "ü" -> return vocab.w("task")
                "a", "t" -> if (bare == "A" || bare == "T" || german) return vocab.w("task")
                "kap", "kapitel", "ch", "chap", "chapter" -> return vocab.w("chapter")
                "q", "qs", "frage", "fragen", "question", "questions" -> return vocab.w("question")
            }
        }

        // uppercase-only German shortcuts
        when (bare) {
            "AB" -> return vocab.w("worksheet")
            "KA" -> return vocab.w("exam")
            "LZK" -> return vocab.w("exam")
            "TB" -> return vocab.w("textbook")
            "WB" -> return vocab.w("workbook")
        }

        SHORTCUTS[n]?.let { return vocab.w(it) }
        return tok
    }

    private fun emojiFor(text: String, kind: ReminderKind): String {
        val t = text.lowercase(Locale.ROOT)
        for ((keys, emoji) in EMOJI_RULES) if (keys.any { t.contains(it) }) return emoji
        return if (kind == ReminderKind.ROUTINE) "🔁" else "📌"
    }

    private class Subject(val en: String, val de: String, val codes: List<String>, val names: List<String>)

    companion object {
        /** Optimal string alignment (Damerau–Levenshtein) distance: typos and swapped letters cost 1. */
        fun distance(a: String, b: String): Int {
            val d = Array(a.length + 1) { IntArray(b.length + 1) }
            for (i in 0..a.length) d[i][0] = i
            for (j in 0..b.length) d[0][j] = j
            for (i in 1..a.length) for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
                }
            }
            return d[a.length][b.length]
        }

        private val MARKERS = setOf("till", "until", "untill", "til", "by", "due", "deadline", "before", "bis", "zum", "on", "for", "at", "am", "the", "this", "next", "coming", "nächsten", "naechsten", "spätestens", "-", "–", "→", "->", "abgabe")

        private val WEEKDAY_FULL: Map<String, DayOfWeek> = mapOf(
            "monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY, "wednesday" to DayOfWeek.WEDNESDAY,
            "thursday" to DayOfWeek.THURSDAY, "friday" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY, "sunday" to DayOfWeek.SUNDAY,
            "montag" to DayOfWeek.MONDAY, "dienstag" to DayOfWeek.TUESDAY, "mittwoch" to DayOfWeek.WEDNESDAY,
            "donnerstag" to DayOfWeek.THURSDAY, "freitag" to DayOfWeek.FRIDAY, "samstag" to DayOfWeek.SATURDAY,
            "sonnabend" to DayOfWeek.SATURDAY, "sonntag" to DayOfWeek.SUNDAY,
        )

        private val WEEKDAY_EXACT: Map<String, DayOfWeek> = WEEKDAY_FULL + mapOf(
            "mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY, "wed" to DayOfWeek.WEDNESDAY,
            "weds" to DayOfWeek.WEDNESDAY, "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY,
            "fri" to DayOfWeek.FRIDAY, "sat" to DayOfWeek.SATURDAY, "sun" to DayOfWeek.SUNDAY,
            "wensday" to DayOfWeek.WEDNESDAY, "wendsday" to DayOfWeek.WEDNESDAY, "wednsday" to DayOfWeek.WEDNESDAY,
            "mo." to DayOfWeek.MONDAY, "di." to DayOfWeek.TUESDAY, "mi." to DayOfWeek.WEDNESDAY, "do." to DayOfWeek.THURSDAY,
            "fr." to DayOfWeek.FRIDAY, "sa." to DayOfWeek.SATURDAY, "so." to DayOfWeek.SUNDAY,
        )

        private val GERMAN_SHORT: Map<String, DayOfWeek> = mapOf(
            "Mo" to DayOfWeek.MONDAY, "Di" to DayOfWeek.TUESDAY, "Mi" to DayOfWeek.WEDNESDAY, "Do" to DayOfWeek.THURSDAY,
            "Fr" to DayOfWeek.FRIDAY, "Sa" to DayOfWeek.SATURDAY, "So" to DayOfWeek.SUNDAY,
        )

        private val SUBJECTS: List<Subject> = listOf(
            Subject("English", "Englisch", listOf("e", "en", "eng", "engl"), listOf("english", "englisch")),
            Subject("German", "Deutsch", listOf("d", "dt", "deu", "ger"), listOf("german", "deutsch")),
            Subject("Math", "Mathe", listOf("m", "ma", "mathe", "math", "maths", "mathematik"), listOf("mathematics", "mathematik")),
            Subject("French", "Französisch", listOf("f", "fr", "frz", "franz"), listOf("french", "französisch", "franzoesisch")),
            Subject("Latin", "Latein", listOf("l", "lat", "latin", "latein"), listOf("latein")),
            Subject("Spanish", "Spanisch", listOf("spa", "span", "spanish", "spanisch"), listOf("spanish", "spanisch")),
            Subject("Italian", "Italienisch", listOf("ita", "ital", "italian", "italienisch"), listOf("italian", "italienisch")),
            Subject("Biology", "Biologie", listOf("b", "bio", "biology", "biologie"), listOf("biology", "biologie")),
            Subject("Chemistry", "Chemie", listOf("ch", "che", "chem", "chemie", "chemistry"), listOf("chemistry", "chemie")),
            Subject("Physics", "Physik", listOf("ph", "phy", "phys", "physik", "physics"), listOf("physics", "physik")),
            Subject("History", "Geschichte", listOf("g", "ge", "gesch", "hist", "history", "geschichte"), listOf("history", "geschichte")),
            Subject("Geography", "Erdkunde", listOf("ek", "erd", "geo", "erdkunde", "geographie", "geography"), listOf("geography", "erdkunde", "geographie")),
            Subject("Art", "Kunst", listOf("k", "ku", "kunst", "art"), listOf("kunst")),
            Subject("Music", "Musik", listOf("mu", "mus", "musik", "music"), listOf("music", "musik")),
            Subject("PE", "Sport", listOf("sp", "spo", "sport", "pe", "gym"), listOf("sport")),
            Subject("Religion", "Religion", listOf("r", "rel", "reli", "religion"), listOf("religion")),
            Subject("Ethics", "Ethik", listOf("eth", "ethik", "ethics"), listOf("ethics", "ethik")),
            Subject("Politics", "Politik", listOf("pol", "powi", "sk", "sowi", "politik", "politics", "sozialkunde"), listOf("politics", "politik", "sozialkunde")),
            Subject("Computer Science", "Informatik", listOf("inf", "info", "it", "cs", "ict", "informatik", "computing", "programming"), listOf("informatik", "computing", "programming", "informatics")),
            Subject("Economics", "Wirtschaft", listOf("wi", "wipo", "wirtschaft", "eco", "econ", "economics", "business"), listOf("economics", "wirtschaft", "business")),
            Subject("Science", "NaWi", listOf("nawi", "science", "naturwissenschaften"), listOf("science", "naturwissenschaften")),
            Subject("Philosophy", "Philosophie", listOf("phil", "philo", "philosophie", "philosophy"), listOf("philosophy", "philosophie")),
            Subject("Psychology", "Psychologie", listOf("psy", "psych", "psychologie", "psychology"), listOf("psychology", "psychologie")),
            Subject("Pedagogy", "Pädagogik", listOf("päd", "paed", "pädagogik", "paedagogik"), listOf("pädagogik")),
            Subject("Social studies", "Gesellschaftslehre", listOf("gl", "gesellschaftslehre"), listOf("gesellschaftslehre")),
            Subject("Technology", "Technik", listOf("tech", "technik", "technology"), listOf("technology", "technik")),
            Subject("Chinese", "Chinesisch", listOf("chin", "chinesisch", "chinese"), listOf("chinese", "chinesisch")),
            Subject("Russian", "Russisch", listOf("russ", "russisch", "russian"), listOf("russian", "russisch")),
            Subject("Greek", "Griechisch", listOf("griech", "griechisch", "greek"), listOf("griechisch")),
            Subject("Drama", "Darstellendes Spiel", listOf("ds", "drama", "theater", "theatre"), listOf("drama", "theater", "theatre")),
            Subject("Literature", "Literatur", listOf("lit", "literature", "literatur"), listOf("literature", "literatur")),
            Subject("Statistics", "Statistik", listOf("stats", "statistics", "statistik"), listOf("statistics", "statistik")),
        )

        private val SUBJECT_BY_CODE: Map<String, Subject> = buildMap { SUBJECTS.forEach { s -> s.codes.forEach { put(it, s) } } }
        private val SUBJECT_BY_NAME: Map<String, Subject> = buildMap { SUBJECTS.forEach { s -> s.names.forEach { put(it, s) } } }

        private val HOMEWORK_WORDS = setOf("homework", "hw", "h/w", "hmwk", "hausaufgabe", "hausaufgaben", "hausi", "hausis", "homeworks", "hausis", "hausaufg")

        /** lowercase token → vocabulary key (rendered in the note's language). */
        private val SHORTCUTS: Map<String, String> = mapOf(
            "vok" to "vocabulary", "vokabeln" to "vocabulary", "vocab" to "vocabulary", "vocabs" to "vocabulary", "voc" to "vocabulary",
            "klausur" to "exam", "klassenarbeit" to "exam", "schulaufgabe" to "exam", "lzk" to "exam", "exam" to "exam", "prüfung" to "exam", "pruefung" to "exam",
            "ref" to "presentation", "referat" to "presentation", "präsi" to "presentation", "praesi" to "presentation", "präsentation" to "presentation",
            "ppt" to "presentation", "pres" to "presentation", "presentation" to "presentation",
            "zsf" to "summary", "zsmf" to "summary", "zusammenfassung" to "summary", "summary" to "summary",
            "wdh" to "revision", "wiederholung" to "revision", "revision" to "revision",
            "prot" to "report", "protokoll" to "report",
            "lös" to "solutions", "loes" to "solutions", "lösung" to "solutions", "lösungen" to "solutions", "loesungen" to "solutions",
            "arbeitsblatt" to "worksheet", "worksheet" to "worksheet", "arbeitsheft" to "workbook", "textbook" to "textbook", "workbook" to "workbook",
            "abg" to "hand-in", "abgabe" to "hand-in", "essay" to "essay", "aufsatz" to "essay", "erörterung" to "essay",
            "lektüre" to "reading", "lektuere" to "reading", "proj" to "project", "projekt" to "project", "appt" to "appointment", "termin" to "appointment",
            "asap" to "as soon as possible", "w/" to "with", "w/o" to "without", "b4" to "before", "bday" to "birthday", "msg" to "message",
            "pls" to "please", "plz" to "please", "thx" to "thanks", "prep" to "prepare", "rdg" to "reading", "rd" to "read", "wrt" to "write",
            "ex" to "exercise", "exs" to "exercises", "hr" to "hour", "hrs" to "hours", "min" to "minutes", "mins" to "minutes", "std" to "hours",
            "vit" to "vitamin", "vits" to "vitamins", "meds" to "medication", "supps" to "supplements",
            "lernen" to "study", "lesen" to "read", "üben" to "practise", "abschreiben" to "copy", "ausfüllen" to "fill in", "bearbeiten" to "work on",
            "unit" to "unit", "übung" to "exercise", "übungen" to "exercises",
        )

        private val SCHOOL_WORDS: Set<String> = HOMEWORK_WORDS + setOf(
            "page", "pages", "seite", "seiten", "nr", "nummer", "number", "task", "tasks", "aufgabe", "aufgaben", "aufg", "kapitel", "kap", "chapter",
            "exercise", "exercises", "übung", "übungen", "vok", "vokabeln", "vocab", "klausur", "klassenarbeit", "exam", "test", "referat", "präsi",
            "arbeitsblatt", "worksheet", "lernen", "lesen", "study", "read", "essay", "aufsatz", "zsf", "zusammenfassung", "unit", "lektion", "lesson",
            "abgabe", "hausi", "schule", "school", "lehrer", "teacher", "unterricht", "stunde", "fach",
        )

        private val GERMAN_MARKERS = setOf(
            "bis", "und", "für", "fuer", "um", "heute", "morgen", "übermorgen", "nächste", "naechste", "nächsten", "woche", "lesen", "lernen", "machen",
            "schreiben", "üben", "wiederholen", "abgeben", "fertig", "ha", "hausaufgabe", "hausaufgaben", "hausi", "seite", "aufgabe", "aufgaben", "aufg",
            "nr", "nummer", "kapitel", "kap", "klausur", "klassenarbeit", "ka", "arbeit", "referat", "präsi", "vok", "vokabeln", "deutsch", "mathe", "englisch",
            "physik", "chemie", "geschichte", "erdkunde", "kunst", "musik", "latein", "biologie", "informatik", "uhr", "montag", "dienstag", "mittwoch",
            "donnerstag", "freitag", "samstag", "sonntag", "jeden", "täglich", "abends", "morgens", "mittags", "mit", "das", "der", "die", "ein", "eine",
            "zum", "zur", "noch", "auch", "bitte", "mal", "nicht", "abgabe", "lösung", "lösungen", "zsf", "wdh", "termin", "arzt", "zahnarzt", "einkaufen",
            "anrufen", "vorbereiten", "tabletten", "wasser", "trinken",
        )

        private val ENGLISH_MARKERS = setOf(
            "till", "until", "by", "due", "homework", "hw", "page", "pages", "read", "study", "tomorrow", "tmrw", "next", "week", "math", "english", "exam",
            "test", "for", "the", "and", "with", "finish", "write", "learn", "every", "daily", "monday", "tuesday", "wednesday", "thursday", "friday",
            "saturday", "sunday", "mon", "tue", "wed", "thu", "fri", "sat", "sun", "chapter", "task", "number", "questions", "essay", "call", "buy",
            "prepare", "water", "drink", "vitamin", "pills", "dentist", "doctor", "meeting", "at",
        )

        private val ROUTINE_WORDS = setOf("every", "daily", "everyday", "each", "täglich", "taeglich", "jeden", "jede", "jedes", "always", "routine", "regularly", "immer")

        private val ROUTINE_NOUNS = setOf(
            "vitamin", "vitamins", "supplement", "supplements", "supps", "pill", "pills", "meds", "medication", "medicine", "tablet", "tablets",
            "creatine", "protein", "omega", "magnesium", "zinc", "iron", "melatonin", "probiotic", "probiotics", "collagen", "d3", "b12",
            "water", "stretch", "stretching", "workout", "meditate", "meditation", "journal", "practice", "practise", "walk",
            "floss", "skincare", "sunscreen", "posture", "eyedrops", "inhaler", "insulin", "tabletten", "medikament", "medikamente", "wasser",
        )

        private val EMOJI_RULES: List<Pair<List<String>, String>> = listOf(
            listOf("vitamin", "supplement", "pill", "tablet", "medic", "meds", "creatine", "protein", "omega", "magnesium", "zinc", "iron", "melatonin", "probiotic", "collagen", "inhaler", "insulin") to "💊",
            listOf("water", "drink", "hydrat", "wasser", "trink") to "💧",
            listOf("exam", "test", "quiz", "klausur", "prüfung", "klassenarbeit", "lzk") to "📝",
            listOf("homework", "hausaufgabe", "read", "lesen", "page", "seite", "chapter", "kapitel", "book", "study", "lernen", "learn", "essay", "vocab", "vokabeln", "aufgabe", "task", "übung") to "📚",
            listOf("gym", "workout", "run", "jog", "train", "stretch", "sport", "exercise", "walk", "yoga") to "🏃",
            listOf("meditat", "breath", "journal", "sleep", "bed") to "🧘",
            listOf("call", "phone", "ring", "anrufen") to "📞",
            listOf("mail", "email", "message", "text", "reply", "write", "schreiben") to "✉️",
            listOf("buy", "shop", "grocer", "order", "pick up", "pickup", "einkaufen", "kaufen") to "🛒",
            listOf("pay", "bill", "rent", "invoice", "money", "bank", "transfer", "zahlen", "überweisen") to "💳",
            listOf("meeting", "meet", "appointment", "termin", "interview") to "📅",
            listOf("birthday", "bday", "party", "gift", "present", "geburtstag", "geschenk") to "🎂",
            listOf("clean", "laundry", "wash", "dishes", "tidy", "vacuum", "trash", "garbage", "putzen", "wäsche", "müll", "aufräumen") to "🧹",
            listOf("cook", "dinner", "lunch", "breakfast", "meal", "food", "eat", "kochen", "essen") to "🍳",
            listOf("doctor", "dentist", "arzt", "zahnarzt", "clinic", "hospital", "checkup", "check-up") to "🩺",
            listOf("presentation", "present", "slides", "talk", "speech", "referat", "präsi", "vortrag") to "🎤",
            listOf("project", "code", "build", "deploy", "fix", "bug", "commit", "projekt") to "💻",
            listOf("plant", "garden", "flower", "pflanze", "gießen") to "🪴",
            listOf("brush", "floss", "teeth", "skincare", "sunscreen", "shower", "zähne") to "🪥",
            listOf("dog", "cat", "pet", "feed", "hund", "katze", "füttern") to "🐾",
            listOf("travel", "flight", "train", "trip", "pack", "hotel", "ticket", "reise", "zug", "flug", "packen") to "✈️",
        )
    }
}
