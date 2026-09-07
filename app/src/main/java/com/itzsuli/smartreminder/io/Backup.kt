package com.itzsuli.smartreminder.io

import com.itzsuli.smartreminder.SmartReminderApp
import com.itzsuli.smartreminder.data.AiEngine
import com.itzsuli.smartreminder.data.DateOrder
import com.itzsuli.smartreminder.data.Delivery
import com.itzsuli.smartreminder.data.Reminder
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Export / import of everything except API keys, as one JSON file. */
object Backup {

    @Serializable
    data class SettingsBackup(
        val aiEngine: String,
        val geminiModel: String,
        val claudeModel: String,
        val deadlineDelivery: String,
        val routineDelivery: String,
        val popupSeconds: Int,
        val activeStart: Int,
        val activeEnd: Int,
        val dateOrder: String,
        val dynamicColors: Boolean,
    )

    @Serializable
    data class Payload(
        val app: String = "smart-reminder",
        val version: Int = 1,
        val exportedAt: Long,
        val reminders: List<Reminder>,
        val settings: SettingsBackup? = null,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun export(app: SmartReminderApp): String {
        val s = app.settings.settings.value
        val payload = Payload(
            exportedAt = System.currentTimeMillis(),
            reminders = app.repository.reminders.value,
            settings = SettingsBackup(
                aiEngine = s.aiEngine.name,
                geminiModel = s.geminiModel,
                claudeModel = s.claudeModel,
                deadlineDelivery = s.deadlineDelivery.name,
                routineDelivery = s.routineDelivery.name,
                popupSeconds = s.popupSeconds,
                activeStart = s.activeStart,
                activeEnd = s.activeEnd,
                dateOrder = s.dateOrder.name,
                dynamicColors = s.dynamicColors,
            ),
        )
        return json.encodeToString(Payload.serializer(), payload)
    }

    /** Merges reminders by id and applies the saved settings. Returns the number of reminders restored. */
    fun import(app: SmartReminderApp, text: String): Int {
        val payload = json.decodeFromString(Payload.serializer(), text)
        require(payload.app == "smart-reminder") { "This is not a Smart Reminder backup." }
        payload.settings?.let { b ->
            app.settings.update { s ->
                s.copy(
                    aiEngine = enumOr(b.aiEngine, s.aiEngine),
                    geminiModel = b.geminiModel.ifBlank { s.geminiModel },
                    claudeModel = b.claudeModel.ifBlank { s.claudeModel },
                    deadlineDelivery = enumOr(b.deadlineDelivery, s.deadlineDelivery),
                    routineDelivery = enumOr(b.routineDelivery, s.routineDelivery),
                    popupSeconds = b.popupSeconds.coerceIn(2, 8),
                    activeStart = b.activeStart,
                    activeEnd = b.activeEnd,
                    dateOrder = enumOr(b.dateOrder, s.dateOrder),
                    dynamicColors = b.dynamicColors,
                )
            }
        }
        return app.repository.upsertAll(payload.reminders)
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    @Suppress("unused")
    private val keepImports = listOf(AiEngine.AUTO, Delivery.POPUP, DateOrder.DAY_FIRST)
}
