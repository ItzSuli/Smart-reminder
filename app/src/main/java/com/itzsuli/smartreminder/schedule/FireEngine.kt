package com.itzsuli.smartreminder.schedule

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import com.itzsuli.smartreminder.SmartReminderApp
import com.itzsuli.smartreminder.data.Delivery
import com.itzsuli.smartreminder.data.Reminder
import com.itzsuli.smartreminder.data.ReminderKind
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random

/** Figures out what is due right now and shows it the way the settings ask for. */
object FireEngine {

    private const val TAG = "FireEngine"
    private const val MAX_RETRIES = 3

    fun fire(context: Context, onFinished: () -> Unit) {
        val app = SmartReminderApp.get(context)
        val settings = app.settings.settings.value
        val salt = app.settings.installSalt
        val state = RuntimeState(context)
        val zone = ZoneId.systemDefault()

        val now = LocalDateTime.now()
        val until = now.plusMinutes(1)
        val untilMillis = until.atZone(zone).toInstant().toEpochMilli()
        val after = state.lastFire?.let { maxOf(it, now.minusHours(2)) } ?: now.minusMinutes(2)

        val reminders = app.repository.reminders.value
        val due = reminders.filter { it.isActive && Scheduler.occurrences(it, settings, salt, after, until).isNotEmpty() }
        val retries = state.retries
        val retryDue = retries.filter { it.value.at <= untilMillis }.keys
            .mapNotNull { id -> reminders.firstOrNull { it.id == id && it.isActive } }
        val all = (due + retryDue).distinctBy { it.id }
        val newRetries = retries.filter { it.value.at > untilMillis }.toMutableMap()
        state.lastFire = until

        if (all.isEmpty()) {
            state.retries = newRetries
            Scheduler.reschedule(context)
            onFinished()
            return
        }

        val screenUsable = isScreenUsable(context)
        val overlayAllowed = Popup.canDraw(context)
        val popups = mutableListOf<Reminder>()
        val notifications = mutableListOf<Reminder>()
        val deferred = mutableListOf<Reminder>()
        for (reminder in all) {
            val delivery = if (reminder.kind == ReminderKind.DEADLINE) settings.deadlineDelivery else settings.routineDelivery
            when (delivery) {
                Delivery.POPUP -> if (screenUsable) popups += reminder else deferred += reminder
                Delivery.POPUP_THEN_NOTIFICATION -> if (screenUsable && overlayAllowed) popups += reminder else notifications += reminder
                Delivery.NOTIFICATION -> notifications += reminder
            }
        }
        for (reminder in deferred) {
            val count = (retries[reminder.id]?.count ?: 0) + 1
            if (count <= MAX_RETRIES) {
                val delayMinutes = 12L + Random.nextInt(0, 11)
                newRetries[reminder.id] = Retry(at = now.plusMinutes(delayMinutes).atZone(zone).toInstant().toEpochMilli(), count = count)
            } else {
                newRetries.remove(reminder.id)
            }
        }
        state.retries = newRetries
        Scheduler.reschedule(context)

        notifications.forEach { Notifier.show(context, it) }
        Log.d(TAG, "due=${all.size} popups=${popups.size} notifications=${notifications.size} deferred=${deferred.size}")

        when {
            popups.isEmpty() -> onFinished()
            overlayAllowed -> {
                if (popups.any { it.kind == ReminderKind.DEADLINE }) Haptics.tick(context)
                Popup.show(context, popups, settings.popupSeconds * 1000L, onFinished)
            }
            else -> {
                // No overlay permission: a plain toast is the least annoying thing left.
                val text = if (popups.size == 1) "${popups[0].emoji} ${popups[0].title}"
                else popups.joinToString("\n") { "${it.emoji} ${it.title}" }
                runCatching { Toast.makeText(context.applicationContext, text, Toast.LENGTH_LONG).show() }
                onFinished()
            }
        }
    }

    fun isScreenUsable(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java)
        val km = context.getSystemService(KeyguardManager::class.java)
        val interactive = pm?.isInteractive ?: true
        val locked = km?.isKeyguardLocked ?: false
        return interactive && !locked
    }
}
