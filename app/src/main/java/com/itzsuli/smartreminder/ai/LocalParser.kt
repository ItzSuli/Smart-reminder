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
 * Offline fallback for turning shorthand like "HW englisj read page 32 till teusday 10.10"
 * into something readable. It is deliberately forgiving: fuzzy weekday and subject matching,
 * abbreviation expansion, several date formats, English + German words.
 *
 * Claude does a much better job; this exists so the app works without an API key or network.
 */
class LocalParser(
    private val dayFirst: Boolean = true,
    private val today: LocalDate = LocalDate.now(),
    private val locale: Locale = Locale.getDefault(),
) {

    fun parse(raw: String): ParsedReminder {
        val text = raw.trim().replace(Regex("\\s+"), " ")
        if (text.isEmpty()) {
            return ParsedReminder("Reminder", "", null, null, ReminderKind.DEADLINE, "📌", source = ParseSource.LOCAL)
        }

        val tokens = text.split(" ")
        val consumed = BooleanArray(tokens.size)
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
            // "10 am" / "5 pm" as two tokens
            if (i + 1 < tokens.size && t.matches(Regex("\\d{1,2}")) && norm(tokens[i + 1]) in setOf("am", "pm")) {
                explicitTime(t + norm(tokens[i + 1]))?.let { time = it; consumed[i] = true; consumed[i + 1] = true }
            }
        }
        // "at 10" / "um 15" / "@ 9"
        tokens.forEachIndexed { i, tok ->
            if (consumed[i]) return@forEachIndexed
            if (norm(tok) in setOf("at", "um", "@") && i + 1 < tokens.size && !consumed[i + 1]) {
                val next = tokens[i + 1].trim(',', ';', '!', '.')
                if (next.matches(Regex("\\d{1,2}")) && next.toInt() in 0..23 && time == null) {
                    time = LocalTime.of(next.toInt(), 0)
                    consumed[i] = true; consumed[i + 1] = true
                } else if (next.matches(Regex("\\d{1,2}(:\\d{2})?(am|pm|h|uhr)?", RegexOption.IGNORE_CASE)) && time == null) {
                    explicitTime(next)?.let { time = it; consumed[i] = true; consumed[i + 1] = true }
                }
            }
        }

        // ---- pass 2: relative words, weekdays, routine hints ---------------------------------
        tokens.forEachIndexed { i, tok ->
            if (consumed[i]) return@forEachIndexed
            val n = norm(tok)
            val prev = if (i > 0) norm(tokens[i - 1]) else ""
            val prev2 = if (i > 1) norm(tokens[i - 2]) else ""
            when {
                n in setOf("today", "heute", "tonight") -> { date = date ?: today; consumed[i] = true }
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
                n in setOf("week", "woche") && prev in setOf("next", "nächste", "naechste", "nächsten") -> {
                    date = date ?: today.plusDays(7); consumed[i] = true; consumed[i - 1] = true
                }
                n in setOf("weekend", "wochenende") -> {
                    date = date ?: nextWeekday(DayOfWeek.SATURDAY); consumed[i] = true
                    if (prev in setOf("this", "next", "the", "am", "on")) consumed[i - 1] = true
                }
                n in setOf("days", "day", "tage", "tag", "weeks", "week", "wochen") && prev.matches(Regex("\\d{1,2}")) && prev2 == "in" -> {
                    val amount = prev.toLong()
                    date = date ?: if (n.startsWith("w")) today.plusWeeks(amount) else today.plusDays(amount)
                    consumed[i] = true; consumed[i - 1] = true; consumed[i - 2] = true
                }
                else -> {
                    val wd = weekday(n)
                    if (wd != null) {
                        if (date == null) date = nextWeekday(wd)
                        consumed[i] = true
                        if (prev in setOf("next", "this", "on", "am", "nächsten", "naechsten", "coming")) consumed[i - 1] = true
                    }
                }
            }
            if (n in ROUTINE_WORDS) { routineHint = true; frequencyWords[i] = true }
            if (n in ROUTINE_NOUNS) routineHint = true
            dayPartFor(n)?.let { dayParts += it; frequencyWords[i] = true }
        }

        // "every"/"each"/"jeden" followed by a consumed frequency word → also a frequency word
        tokens.forEachIndexed { i, tok ->
            val n = norm(tok)
            if (n in setOf("every", "each", "jeden", "jede", "jedes", "per", "a", "in", "the", "at", "am", "vor", "nach", "before", "after", "with", "beim", "zum") &&
                i + 1 < tokens.size && frequencyWords[i + 1]
            ) frequencyWords[i] = true
        }
        // "every day", "each day", "jeden tag", "every night"
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

        // ---- expand + fuzzy-fix remaining words --------------------------------------------
        val rest = mutableListOf<String>()
        val restForTitle = mutableListOf<String>()
        var subject: String? = null
        var homework = false
        tokens.forEachIndexed { i, tok ->
            if (consumed[i]) return@forEachIndexed
            val expanded = expand(tok, if (i + 1 < tokens.size) tokens[i + 1] else null)
            val words = expanded.split(" ")
            for (w in words) {
                val n = norm(w)
                val subj = subject(n)
                val out = when {
                    n in HOMEWORK_WORDS -> { homework = true; "homework" }
                    subj != null -> { if (subject == null) subject = subj; subj }
                    else -> w
                }
                rest += out
                if (!(kind == ReminderKind.ROUTINE && frequencyWords[i])) restForTitle += out
            }
        }

        // ---- build title + details --------------------------------------------------------
        val clean = rest.joinToString(" ").trim().trim(',', '.', ';', '-').trim()
        val cleanTitle = restForTitle.joinToString(" ").trim().trim(',', '.', ';', '-').trim()
        val subjectName = subject
        val title: String
        val sentence: String
        if (homework) {
            val core = wordsWithout(cleanTitle, listOf("homework", subjectName ?: ""))
            title = when {
                subjectName != null && core.isNotBlank() -> "$subjectName homework: $core"
                subjectName != null -> "$subjectName homework"
                core.isNotBlank() -> "Homework: $core"
                else -> "Homework"
            }
            sentence = when {
                subjectName != null && core.isNotBlank() -> "${cap(core)} for $subjectName homework."
                subjectName != null -> "$subjectName homework."
                core.isNotBlank() -> "${cap(core)} for homework."
                else -> "Homework."
            }
        } else {
            title = cap(cleanTitle.ifBlank { clean }.ifBlank { text })
            sentence = cap(clean.ifBlank { text }).let { if (it.endsWith(".") || it.endsWith("!") || it.endsWith("?")) it else "$it." }
        }

        val dueDate = date
        val details = buildString {
            append(sentence)
            if (kind == ReminderKind.DEADLINE && dueDate != null) {
                append(" ").append(duePhrase(dueDate, time))
            } else if (kind == ReminderKind.ROUTINE) {
                append(" ").append(routinePhrase(dayParts.toList(), time))
            }
        }

        return ParsedReminder(
            title = shorten(title),
            details = details,
            dueDate = if (kind == ReminderKind.DEADLINE) date else null,
            dueTime = if (kind == ReminderKind.DEADLINE) time else null,
            kind = kind,
            emoji = emojiFor("$clean $text", kind),
            dayParts = if (kind == ReminderKind.ROUTINE) dayParts.toList() else emptyList(),
            source = ParseSource.LOCAL,
        )
    }

    // ---------------------------------------------------------------------------------------
    // helpers

    private fun norm(t: String) = t.lowercase(Locale.ROOT).trim { !it.isLetterOrDigit() && it != '/' }

    private fun cap(s: String) = s.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

    private fun shorten(s: String, max: Int = 70): String {
        if (s.length <= max) return s
        val cut = s.substring(0, max).substringBeforeLast(' ')
        return "$cut…"
    }

    private fun wordsWithout(text: String, drop: List<String>): String {
        val dropSet = drop.filter { it.isNotBlank() }.map { it.lowercase(Locale.ROOT) }.toSet()
        return text.split(" ").filter { norm(it) !in dropSet }.joinToString(" ").trim().trim(',', '-', ':').trim()
    }

    private fun duePhrase(date: LocalDate, time: LocalTime?): String {
        val dayText = when (date) {
            today -> "today"
            today.plusDays(1) -> "tomorrow"
            else -> date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
        }
        val timeText = time?.let { " at " + it.format(DateTimeFormatter.ofPattern("HH:mm")) } ?: ""
        return "Due $dayText$timeText."
    }

    private fun routinePhrase(parts: List<DayPart>, time: LocalTime?): String {
        if (time != null) return "Every day at " + time.format(DateTimeFormatter.ofPattern("HH:mm")) + "."
        if (parts.isEmpty()) return "Every day."
        val names = parts.map { it.label.lowercase(Locale.ROOT) }
        val joined = when (names.size) {
            1 -> names[0]
            2 -> names[0] + " and " + names[1]
            else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
        }
        return "Every day in the $joined."
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
        // "3.5" alone could be a decimal number; only accept it when it looks like a date
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
        // a bare number is not a time ("page 32") – require a colon or a suffix
        if (m.groupValues[2].isEmpty() && suffix.isEmpty()) return null
        if (suffix == "pm" && hour < 12) hour += 12
        if (suffix == "am" && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }

    private fun isTomorrow(n: String): Boolean =
        n in setOf("tomorrow", "tmrw", "tmr", "tmrrw", "morgen") || (n.length >= 6 && distance(n, "tomorrow") <= 2 && n.startsWith("to"))

    private fun nextWeekday(target: DayOfWeek): LocalDate {
        var diff = (target.value - today.dayOfWeek.value + 7) % 7
        if (diff == 0) diff = 7
        return today.plusDays(diff.toLong())
    }

    private fun weekday(n: String): DayOfWeek? {
        WEEKDAY_EXACT[n]?.let { return it }
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

    private fun subject(n: String): String? {
        SUBJECT_EXACT[n]?.let { return it }
        if (n.length < 5) return null
        val threshold = if (n.length >= 8) 2 else 1
        var best: String? = null
        var bestDist = Int.MAX_VALUE
        for ((name, canonical) in SUBJECT_FUZZY) {
            val d = distance(n, name)
            if (d < bestDist) { bestDist = d; best = canonical }
        }
        return if (bestDist <= threshold) best else null
    }

    private fun dayPartFor(n: String): DayPart? = when (n) {
        "morning", "mornings", "morgens", "früh", "frueh", "breakfast", "frühstück", "fruehstueck", "wakeup", "wake-up" -> DayPart.MORNING
        "noon", "midday", "lunch", "lunchtime", "mittag", "mittags", "afternoon", "afternoons", "nachmittag", "nachmittags" -> DayPart.MIDDAY
        "evening", "evenings", "abend", "abends", "dinner", "bed", "bedtime", "nachts", "night", "nights" -> DayPart.EVENING
        else -> null
    }

    private fun expand(tok: String, next: String?): String {
        val n = norm(tok)
        ABBREVIATIONS[n]?.let { return it }
        Regex("^(p|pg|pgs|s|seite)\\.?(\\d+)$").matchEntire(n)?.let { return "page " + it.groupValues[2] }
        Regex("^(ch|chap|kap)\\.?(\\d+)$").matchEntire(n)?.let { return "chapter " + it.groupValues[2] }
        Regex("^(ex|exc)\\.?(\\d+)$").matchEntire(n)?.let { return "exercise " + it.groupValues[2] }
        Regex("^(q|qs|nr|no)\\.?(\\d+)$").matchEntire(n)?.let { return "number " + it.groupValues[2] }
        val nextIsNumber = next != null && norm(next).matches(Regex("\\d+(-\\d+)?"))
        if (nextIsNumber) {
            when (n) {
                "p", "pg", "pgs", "s", "seite", "seiten" -> return "page"
                "ch", "chap", "kap" -> return "chapter"
                "ex", "exc" -> return "exercise"
                "q", "qs", "nr", "no" -> return "number"
            }
        }
        return tok
    }

    private fun emojiFor(text: String, kind: ReminderKind): String {
        val t = text.lowercase(Locale.ROOT)
        for ((keys, emoji) in EMOJI_RULES) if (keys.any { t.contains(it) }) return emoji
        return if (kind == ReminderKind.ROUTINE) "🔁" else "📌"
    }

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

        private val MARKERS = setOf("till", "until", "untill", "til", "by", "due", "deadline", "before", "bis", "zum", "on", "for", "at", "am", "the", "this", "next", "coming", "nächsten", "naechsten", "-", "–", "→", "->")

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

        private val SUBJECT_FUZZY: Map<String, String> = mapOf(
            "english" to "English", "german" to "German", "french" to "French", "spanish" to "Spanish", "italian" to "Italian",
            "physics" to "Physics", "chemistry" to "Chemistry", "biology" to "Biology", "history" to "History",
            "geography" to "Geography", "science" to "Science", "economics" to "Economics", "business" to "Business",
            "philosophy" to "Philosophy", "religion" to "Religion", "politics" to "Politics", "sociology" to "Sociology",
            "psychology" to "Psychology", "literature" to "Literature", "computing" to "Computing", "programming" to "Programming",
            "informatics" to "Informatics", "mathematics" to "Maths", "statistics" to "Statistics", "geometry" to "Geometry",
            "algebra" to "Algebra", "drama" to "Drama", "theatre" to "Theatre", "spanish" to "Spanish",
            "deutsch" to "Deutsch", "englisch" to "Englisch", "französisch" to "Französisch", "franzoesisch" to "Französisch",
            "spanisch" to "Spanisch", "physik" to "Physik", "chemie" to "Chemie", "biologie" to "Biologie",
            "geschichte" to "Geschichte", "erdkunde" to "Erdkunde", "geographie" to "Geographie", "informatik" to "Informatik",
            "latein" to "Latein", "wirtschaft" to "Wirtschaft", "philosophie" to "Philosophie", "sozialkunde" to "Sozialkunde",
        )

        private val SUBJECT_EXACT: Map<String, String> = SUBJECT_FUZZY + mapOf(
            "math" to "Math", "maths" to "Maths", "mathe" to "Mathe", "art" to "Art", "music" to "Music", "musik" to "Musik",
            "kunst" to "Kunst", "sport" to "Sport", "pe" to "PE", "gym" to "Gym", "latin" to "Latin", "bio" to "Biology",
            "chem" to "Chemistry", "geo" to "Geography", "hist" to "History", "eng" to "English", "ethik" to "Ethik",
            "politik" to "Politik", "it" to "IT", "cs" to "Computer Science", "ict" to "ICT", "eco" to "Economics",
        )

        private val HOMEWORK_WORDS = setOf("homework", "hw", "h/w", "hausaufgabe", "hausaufgaben", "hausi", "hausis", "homeworks", "hmwk")

        private val ABBREVIATIONS: Map<String, String> = mapOf(
            "hw" to "homework", "h/w" to "homework", "hmwk" to "homework", "hausaufgabe" to "homework", "hausaufgaben" to "homework",
            "asap" to "as soon as possible", "w/" to "with", "w/o" to "without", "b4" to "before", "appt" to "appointment",
            "ppt" to "presentation", "pres" to "presentation", "tb" to "textbook", "wb" to "workbook", "vocab" to "vocabulary",
            "assgn" to "assignment", "proj" to "project", "msg" to "message", "ppl" to "people", "bday" to "birthday",
            "bc" to "because", "u" to "you", "ur" to "your", "pls" to "please", "plz" to "please", "thx" to "thanks",
            "prep" to "prepare", "rdg" to "reading", "rd" to "read", "wrt" to "write", "ex" to "exercise", "exs" to "exercises",
            "qs" to "questions", "hr" to "hour", "hrs" to "hours", "min" to "minutes", "mins" to "minutes",
            "vit" to "vitamin", "vits" to "vitamins", "meds" to "medication", "supps" to "supplements",
            "klausur" to "exam", "prüfung" to "exam", "pruefung" to "exam", "arbeit" to "test", "referat" to "presentation",
            "lernen" to "study", "lesen" to "read", "abgabe" to "hand-in",
        )

        private val ROUTINE_WORDS = setOf("every", "daily", "everyday", "each", "täglich", "taeglich", "jeden", "jede", "jedes", "always", "routine", "regularly")

        private val ROUTINE_NOUNS = setOf(
            "vitamin", "vitamins", "supplement", "supplements", "supps", "pill", "pills", "meds", "medication", "medicine", "tablet", "tablets",
            "creatine", "protein", "omega", "magnesium", "zinc", "iron", "melatonin", "probiotic", "probiotics", "collagen", "d3", "b12",
            "water", "stretch", "stretching", "workout", "meditate", "meditation", "journal", "practice", "practise", "walk",
            "floss", "skincare", "sunscreen", "posture", "eyedrops", "inhaler", "insulin", "tabletten", "medikament", "medikamente",
        )

        private val EMOJI_RULES: List<Pair<List<String>, String>> = listOf(
            listOf("vitamin", "supplement", "pill", "tablet", "medic", "meds", "creatine", "protein", "omega", "magnesium", "zinc", "iron", "melatonin", "probiotic", "collagen", "inhaler", "insulin") to "💊",
            listOf("water", "drink", "hydrat") to "💧",
            listOf("exam", "test", "quiz", "klausur", "prüfung") to "📝",
            listOf("homework", "read", "page", "chapter", "book", "study", "learn", "essay", "vocab") to "📚",
            listOf("gym", "workout", "run", "jog", "train", "stretch", "sport", "exercise", "walk", "yoga") to "🏃",
            listOf("meditat", "breath", "journal", "sleep", "bed") to "🧘",
            listOf("call", "phone", "ring") to "📞",
            listOf("mail", "email", "message", "text", "reply", "write") to "✉️",
            listOf("buy", "shop", "grocer", "order", "pick up", "pickup") to "🛒",
            listOf("pay", "bill", "rent", "invoice", "money", "bank", "transfer") to "💳",
            listOf("meeting", "meet", "appointment", "termin", "interview") to "📅",
            listOf("birthday", "bday", "party", "gift", "present") to "🎂",
            listOf("clean", "laundry", "wash", "dishes", "tidy", "vacuum", "trash", "garbage") to "🧹",
            listOf("cook", "dinner", "lunch", "breakfast", "meal", "food", "eat") to "🍳",
            listOf("doctor", "dentist", "arzt", "clinic", "hospital", "checkup", "check-up") to "🩺",
            listOf("presentation", "present", "slides", "talk", "speech", "referat") to "🎤",
            listOf("project", "code", "build", "deploy", "fix", "bug", "commit") to "💻",
            listOf("plant", "garden", "flower") to "🪴",
            listOf("brush", "floss", "teeth", "skincare", "sunscreen", "shower") to "🪥",
            listOf("dog", "cat", "pet", "feed") to "🐾",
            listOf("travel", "flight", "train", "trip", "pack", "hotel", "ticket") to "✈️",
        )
    }
}
