package com.itzsuli.smartreminder.ai

import com.itzsuli.smartreminder.data.DateOrder
import com.itzsuli.smartreminder.data.ParsedReminder
import com.itzsuli.smartreminder.data.Settings
import kotlinx.coroutines.CancellationException

/**
 * Front door for "make this note understandable": uses Claude when an API key is set and
 * quietly falls back to the offline parser otherwise (or when the network/API fails).
 */
class ReminderParser(private val settingsProvider: () -> Settings) {

    suspend fun parse(input: String): ParsedReminder {
        val settings = settingsProvider()
        val dayFirst = settings.dateOrder == DateOrder.DAY_FIRST
        if (!settings.hasApiKey) {
            return LocalParser(dayFirst).parse(input)
                .copy(note = "Cleaned up offline. Add a Claude API key in Settings for smarter results.")
        }
        return try {
            ClaudeParser(settings.apiKey, settings.model).parse(input, settings.dateOrder)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LocalParser(dayFirst).parse(input)
                .copy(note = "Claude couldn't help this time (${e.message ?: "unknown error"}), so this was cleaned up offline.")
        }
    }
}
