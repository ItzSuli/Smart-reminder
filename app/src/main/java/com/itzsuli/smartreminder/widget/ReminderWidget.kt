package com.itzsuli.smartreminder.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.itzsuli.smartreminder.MainActivity
import com.itzsuli.smartreminder.R
import com.itzsuli.smartreminder.SmartReminderApp
import com.itzsuli.smartreminder.data.Reminder
import com.itzsuli.smartreminder.data.ReminderKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Home-screen widget: today's date, the next few deadlines and events, one-tap Add / Speak. */
class ReminderWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = build(context)
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
    }

    companion object {
        private val ROWS = listOf(R.id.widget_row1, R.id.widget_row2, R.id.widget_row3)
        private val EMOJIS = listOf(R.id.widget_emoji1, R.id.widget_emoji2, R.id.widget_emoji3)
        private val TITLES = listOf(R.id.widget_title1, R.id.widget_title2, R.id.widget_title3)
        private val SUBS = listOf(R.id.widget_sub1, R.id.widget_sub2, R.id.widget_sub3)
        private val CHIPS = listOf(R.id.widget_chip1, R.id.widget_chip2, R.id.widget_chip3)

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, ReminderWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(context)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun build(context: Context): RemoteViews {
            val app = SmartReminderApp.get(context)
            val today = LocalDate.now()
            val upcoming = app.repository.reminders.value
                .filter { it.kind != ReminderKind.ROUTINE && !it.done }
                .filter { it.kind != ReminderKind.EVENT || (it.dueLocalDate?.isBefore(today) != true) }
                .sortedWith(
                    compareBy<Reminder> { it.dueLocalDate == null }
                        .thenBy { it.dueLocalDate }
                        .thenBy { it.dueLocalTime == null }
                        .thenBy { it.dueLocalTime }
                        .thenByDescending { it.createdAt }
                )
                .take(3)

            val views = RemoteViews(context.packageName, R.layout.widget_reminders)
            views.setTextViewText(R.id.widget_date, today.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())))
            views.setTextViewText(R.id.widget_kicker, if (upcoming.isEmpty()) "All clear" else "Up next")

            ROWS.forEachIndexed { index, rowId ->
                val r = upcoming.getOrNull(index)
                if (r == null) {
                    views.setViewVisibility(rowId, View.GONE)
                    return@forEachIndexed
                }
                views.setViewVisibility(rowId, View.VISIBLE)
                views.setTextViewText(EMOJIS[index], r.emoji)
                views.setTextViewText(TITLES[index], r.title)
                val date = r.dueLocalDate
                val days = date?.let { ChronoUnit.DAYS.between(today, it) }
                val sub = when {
                    date == null -> "No date"
                    else -> {
                        val d = date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
                        val t = r.dueLocalTime?.let { " · " + it.format(DateTimeFormatter.ofPattern("HH:mm")) } ?: ""
                        val rel = when {
                            days!! < 0 -> " · overdue"
                            days == 0L -> " · today"
                            days == 1L -> " · tomorrow"
                            days < 14 -> " · in $days days"
                            else -> ""
                        }
                        d + t + rel
                    }
                }
                views.setTextViewText(SUBS[index], sub)
                val chip = when {
                    days == null -> "—"
                    days < 0 -> "late"
                    days == 0L -> "Today"
                    days == 1L -> "Tmrw"
                    days < 7 -> date!!.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
                    else -> "${days}d"
                }
                views.setTextViewText(CHIPS[index], chip)
                val chipBg = when {
                    days != null && days < 0 -> R.drawable.widget_chip_overdue
                    days == 0L -> R.drawable.widget_chip_today
                    r.kind == ReminderKind.EVENT -> R.drawable.widget_chip_event
                    else -> R.drawable.widget_chip
                }
                views.setInt(CHIPS[index], "setBackgroundResource", chipBg)
            }
            views.setViewVisibility(R.id.widget_empty, if (upcoming.isEmpty()) View.VISIBLE else View.GONE)
            views.setOnClickPendingIntent(R.id.widget_add, open(context, MainActivity.ACTION_ADD, 11))
            views.setOnClickPendingIntent(R.id.widget_voice, open(context, MainActivity.ACTION_VOICE, 12))
            views.setOnClickPendingIntent(R.id.widget_root, open(context, null, 13))
            return views
        }

        private fun open(context: Context, action: String?, code: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (action != null) intent.action = action
            return PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
