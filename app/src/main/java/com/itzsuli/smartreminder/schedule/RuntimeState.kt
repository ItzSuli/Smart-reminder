package com.itzsuli.smartreminder.schedule

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** A pop-up that could not be shown (screen off) and will be tried again. */
@Serializable
data class Retry(val at: Long, val count: Int)

/** Small bookkeeping the alarm engine needs between wake-ups. */
class RuntimeState(context: Context) {

    private val prefs = context.getSharedPreferences("runtime", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val retrySerializer = MapSerializer(String.serializer(), Retry.serializer())

    /** Every slot up to (and including) this moment has been handled. */
    var lastFire: LocalDateTime?
        get() = prefs.getLong(KEY_LAST_FIRE, -1L).takeIf { it >= 0 }
            ?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault()) }
        set(value) = prefs.edit {
            if (value == null) remove(KEY_LAST_FIRE)
            else putLong(KEY_LAST_FIRE, value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        }

    var retries: Map<String, Retry>
        get() = prefs.getString(KEY_RETRIES, null)
            ?.let { runCatching { json.decodeFromString(retrySerializer, it) }.getOrNull() }
            ?: emptyMap()
        set(value) = prefs.edit { putString(KEY_RETRIES, json.encodeToString(retrySerializer, value)) }

    private companion object {
        const val KEY_LAST_FIRE = "last_fire"
        const val KEY_RETRIES = "retries"
    }
}
