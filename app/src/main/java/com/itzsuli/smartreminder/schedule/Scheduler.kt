package com.itzsuli.smartreminder.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.itzsuli.smartreminder.SmartReminderApp
import com.itzsuli.smartreminder.data.Reminder
import com.itzsuli.smartreminder.data.Settings
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Keeps exactly one AlarmManager alarm armed: the next moment any reminder wants attention.
 * When it fires, [FireEngine] delivers what is due and calls [reschedule] again.
 */
object Scheduler {

    const val ACTION_FIRE = "com.itzsuli.smartreminder.action.FIRE"
    private const val REQUEST_FIRE = 1001
    private const val LOOKAHEAD_DAYS = 45
    private const val TAG = "Scheduler"

    fun reschedule(context: Context) {
        try {
            val app = SmartReminderApp.get(context)
            val state = RuntimeState(context)
            val settings = app.settings.settings.value
            val reminders = app.repository.reminders.value
            val now = LocalDateTime.now()
            val after = maxOf(now, state.lastFire ?: now)

            var next = nextOccurrence(reminders, settings, app.settings.installSalt, after)

            // Deferred pop-ups (screen was off) also count.
            state.retries.values.minOfOrNull { it.at }?.let { retryAt ->
                val retryTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(retryAt), ZoneId.systemDefault())
                val effective = maxOf(retryTime, now.plusMinutes(1))
                if (next == null || effective.isBefore(next)) next = effective
            }

            // Daily heartbeat at the start of the active window, so far-away deadlines get re-evaluated.
            if (reminders.any { it.isActive }) {
                val heartbeat = nextWindowStart(settings, after)
                if (next == null || heartbeat.isBefore(next)) next = heartbeat
            }

            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val pendingIntent = firePendingIntent(context)
            alarmManager.cancel(pendingIntent)
            val target = next ?: return
            val triggerAt = target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
            try {
                if (exact) alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
                else alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } catch (e: SecurityException) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
            Log.d(TAG, "next wake-up at $target (exact=$exact)")
        } catch (t: Throwable) {
            Log.e(TAG, "reschedule failed", t)
        }
    }

    fun firePendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_FIRE,
        Intent(context, AlarmReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun nextWindowStart(settings: Settings, after: LocalDateTime): LocalDateTime {
        val startMinute = settings.activeWindow.first
        val todayStart = after.toLocalDate().atStartOfDay().plusMinutes(startMinute.toLong())
        return if (todayStart.isAfter(after)) todayStart else todayStart.plusDays(1)
    }

    fun nextOccurrence(reminders: List<Reminder>, settings: Settings, salt: Long, after: LocalDateTime): LocalDateTime? {
        var best: LocalDateTime? = null
        for (reminder in reminders) {
            if (!reminder.isActive) continue
            val candidate = nextFor(reminder, settings, salt, after) ?: continue
            if (best == null || candidate.isBefore(best)) best = candidate
        }
        return best
    }

    fun nextFor(reminder: Reminder, settings: Settings, salt: Long, after: LocalDateTime): LocalDateTime? {
        var day = after.toLocalDate()
        for (i in 0 until LOOKAHEAD_DAYS) {
            for (slot in Cadence.slotsFor(reminder, day, settings, salt)) {
                val at = day.atTime(slot)
                if (at.isAfter(after)) return at
            }
            day = day.plusDays(1)
        }
        return null
    }

    /** Slots of [reminder] in the half-open interval ([after], [until]]. */
    fun occurrences(reminder: Reminder, settings: Settings, salt: Long, after: LocalDateTime, until: LocalDateTime): List<LocalDateTime> {
        val out = mutableListOf<LocalDateTime>()
        var day = after.toLocalDate()
        while (!day.isAfter(until.toLocalDate())) {
            for (slot in Cadence.slotsFor(reminder, day, settings, salt)) {
                val at = day.atTime(slot)
                if (at.isAfter(after) && !at.isAfter(until)) out += at
            }
            day = day.plusDays(1)
        }
        return out
    }
}
