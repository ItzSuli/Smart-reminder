package com.itzsuli.smartreminder.ai

import com.itzsuli.smartreminder.data.AiEngine
import com.itzsuli.smartreminder.data.DateOrder
import com.itzsuli.smartreminder.data.ParsedReminder
import com.itzsuli.smartreminder.data.ReminderKind
import com.itzsuli.smartreminder.data.Settings
import kotlinx.coroutines.CancellationException

/**
 * Front door for "make this note understandable". Picks the engine from the settings,
 * tries the next one when one fails, and always has the offline parser as a safety net.
 */
class ReminderParser(private val settingsProvider: () -> Settings) {

    suspend fun parse(input: String): ParsedReminder {
        val settings = settingsProvider()
        val local = LocalParser(settings.dateOrder == DateOrder.DAY_FIRST).parse(input)
        val failures = mutableListOf<String>()

        for (engine in engineOrder(settings)) {
            try {
                val ai = when (engine) {
                    AiEngine.NANO -> NanoEngine.parse(input, local)
                    AiEngine.GEMINI -> GeminiParser(settings.geminiKey, settings.geminiModel).parse(input, settings.dateOrder)
                    AiEngine.CLAUDE -> ClaudeParser(settings.claudeKey, settings.claudeModel).parse(input, settings.dateOrder)
                    else -> continue
                }
                return merge(ai, local)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures += "${engine.label.substringBefore(" (")}: ${e.message ?: e.javaClass.simpleName}"
            }
        }

        val note = when {
            failures.isNotEmpty() -> "AI didn't work this time (${failures.joinToString("; ")}), so this was cleaned up offline."
            settings.aiEngine == AiEngine.OFFLINE -> null
            else -> "Cleaned up offline. Add a free Gemini key in Settings for smarter results."
        }
        return local.copy(note = note)
    }

    private suspend fun engineOrder(s: Settings): List<AiEngine> = when (s.aiEngine) {
        AiEngine.AUTO -> buildList {
            if (NanoEngine.isAvailable()) add(AiEngine.NANO)
            if (s.hasGeminiKey) add(AiEngine.GEMINI)
            if (s.hasClaudeKey) add(AiEngine.CLAUDE)
        }
        AiEngine.NANO -> listOf(AiEngine.NANO)
        AiEngine.GEMINI -> if (s.hasGeminiKey) listOf(AiEngine.GEMINI) else emptyList()
        AiEngine.CLAUDE -> if (s.hasClaudeKey) listOf(AiEngine.CLAUDE) else emptyList()
        AiEngine.OFFLINE -> emptyList()
    }

    /** The model's wording wins; the offline parser fills whatever the model left empty. */
    private fun merge(ai: ParsedReminder, local: ParsedReminder): ParsedReminder {
        val kind = ai.kind
        return ai.copy(
            title = ai.title.ifBlank { local.title },
            details = ai.details.ifBlank { local.details },
            dueDate = if (kind != ReminderKind.ROUTINE) ai.dueDate ?: local.dueDate else null,
            dueTime = ai.dueTime ?: local.dueTime,
            dayParts = if (kind == ReminderKind.ROUTINE) ai.dayParts.ifEmpty { local.dayParts } else emptyList(),
            emoji = ai.emoji.ifBlank { local.emoji },
        )
    }
}
