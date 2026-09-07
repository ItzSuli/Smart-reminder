package com.itzsuli.smartreminder.io

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** An event that can become a deadline reminder. */
data class ImportEvent(
    val key: String,
    val title: String,
    val date: LocalDate,
    val time: LocalTime?,
    val details: String,
    val source: String,
)

object CalendarSource {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Upcoming calendar instances (expanded recurrences) for the next [days] days. */
    fun upcoming(context: Context, days: Long = 30): List<ImportEvent> {
        if (!hasPermission(context)) return emptyList()
        val now = System.currentTimeMillis()
        val end = now + days * 24 * 60 * 60 * 1000
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(now.toString())
            .appendPath(end.toString())
            .build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
        )
        val out = mutableListOf<ImportEvent>()
        val zone = ZoneId.systemDefault()
        runCatching {
            context.contentResolver.query(uri, projection, null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { c ->
                while (c.moveToNext()) {
                    val title = c.getString(1)?.trim().orEmpty()
                    if (title.isEmpty()) continue
                    val begin = c.getLong(2)
                    val allDay = c.getInt(3) == 1
                    val instant = Instant.ofEpochMilli(begin)
                    val date: LocalDate
                    val time: LocalTime?
                    if (allDay) {
                        date = instant.atZone(ZoneOffset.UTC).toLocalDate()
                        time = null
                    } else {
                        val local = instant.atZone(zone)
                        date = local.toLocalDate()
                        time = local.toLocalTime().withSecond(0).withNano(0)
                    }
                    out += ImportEvent(
                        key = "cal:${c.getLong(0)}:$begin",
                        title = title,
                        date = date,
                        time = time,
                        details = c.getString(4)?.trim().orEmpty(),
                        source = c.getString(5)?.trim().orEmpty().ifEmpty { "Calendar" },
                    )
                }
            }
        }
        return out.distinctBy { it.key }
    }
}
