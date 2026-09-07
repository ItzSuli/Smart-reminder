package com.itzsuli.smartreminder.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/** SharedPreferences-backed settings exposed as a StateFlow so Compose updates live. */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    /**
     * Random number generated once per installation. It seeds the reminder schedule,
     * so the exact times can't be predicted even by reading the source code.
     */
    val installSalt: Long
        get() {
            if (!prefs.contains(KEY_SALT)) prefs.edit { putLong(KEY_SALT, Random.nextLong()) }
            return prefs.getLong(KEY_SALT, 0L)
        }

    fun update(transform: (Settings) -> Settings) {
        val next = transform(_settings.value)
        write(next)
        _settings.value = next
    }

    private fun read(): Settings = Settings(
        apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
        model = prefs.getString(KEY_MODEL, Settings.DEFAULT_MODEL)?.ifBlank { Settings.DEFAULT_MODEL } ?: Settings.DEFAULT_MODEL,
        deadlineDelivery = enum(prefs.getString(KEY_DEADLINE_DELIVERY, null), Delivery.POPUP_THEN_NOTIFICATION),
        routineDelivery = enum(prefs.getString(KEY_ROUTINE_DELIVERY, null), Delivery.POPUP),
        popupSeconds = prefs.getInt(KEY_POPUP_SECONDS, 3).coerceIn(2, 8),
        activeStart = prefs.getInt(KEY_ACTIVE_START, 8 * 60),
        activeEnd = prefs.getInt(KEY_ACTIVE_END, 22 * 60),
        dateOrder = enum(prefs.getString(KEY_DATE_ORDER, null), DateOrder.DAY_FIRST),
        dynamicColors = prefs.getBoolean(KEY_DYNAMIC_COLORS, false),
        setupCardDismissed = prefs.getBoolean(KEY_SETUP_DISMISSED, false),
    )

    private fun write(s: Settings) = prefs.edit {
        putString(KEY_API_KEY, s.apiKey.trim())
        putString(KEY_MODEL, s.model.trim())
        putString(KEY_DEADLINE_DELIVERY, s.deadlineDelivery.name)
        putString(KEY_ROUTINE_DELIVERY, s.routineDelivery.name)
        putInt(KEY_POPUP_SECONDS, s.popupSeconds)
        putInt(KEY_ACTIVE_START, s.activeStart)
        putInt(KEY_ACTIVE_END, s.activeEnd)
        putString(KEY_DATE_ORDER, s.dateOrder.name)
        putBoolean(KEY_DYNAMIC_COLORS, s.dynamicColors)
        putBoolean(KEY_SETUP_DISMISSED, s.setupCardDismissed)
    }

    private inline fun <reified T : Enum<T>> enum(name: String?, default: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default

    private companion object {
        const val KEY_API_KEY = "api_key"
        const val KEY_MODEL = "model"
        const val KEY_DEADLINE_DELIVERY = "deadline_delivery"
        const val KEY_ROUTINE_DELIVERY = "routine_delivery"
        const val KEY_POPUP_SECONDS = "popup_seconds"
        const val KEY_ACTIVE_START = "active_start"
        const val KEY_ACTIVE_END = "active_end"
        const val KEY_DATE_ORDER = "date_order"
        const val KEY_DYNAMIC_COLORS = "dynamic_colors"
        const val KEY_SETUP_DISMISSED = "setup_dismissed"
        const val KEY_SALT = "install_salt"
    }
}
