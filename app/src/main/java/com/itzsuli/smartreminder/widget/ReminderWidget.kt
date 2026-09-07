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
import com.itzsuli.smartreminder.ui.dueLabel
import java.time.LocalDate

/** Home-screen widget: the next few deadlines plus one-tap "Add" and "Speak" buttons. */
class ReminderWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = build(context)
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
    }

    companion object {
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
                .filter { it.kind == ReminderKind.DEADLINE && !it.done }
                .sortedWith(
                    compareBy<Reminder> { it.dueLocalDate == null }
                        .thenBy { it.dueLocalDate }
                        .thenBy { it.dueLocalTime == null }
                        .thenBy { it.dueLocalTime }
                        .thenByDescending { it.createdAt }
                )
                .take(3)

            val views = RemoteViews(context.packageName, R.layout.widget_reminders)
            val rows = listOf(R.id.widget_row1, R.id.widget_row2, R.id.widget_row3)
            rows.forEachIndexed { index, id ->
                val r = upcoming.getOrNull(index)
                if (r == null) {
                    views.setViewVisibility(id, View.GONE)
                } else {
                    val due = r.dueLocalDate?.let { dueLabel(it, r.dueLocalTime, today).text } ?: "no date"
                    views.setViewVisibility(id, View.VISIBLE)
                    views.setTextViewText(id, "${r.emoji} ${r.title}  ·  $due")
                }
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
