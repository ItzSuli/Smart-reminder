package com.itzsuli.smartreminder.schedule

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.itzsuli.smartreminder.MainActivity
import com.itzsuli.smartreminder.R
import com.itzsuli.smartreminder.data.Reminder
import com.itzsuli.smartreminder.data.ReminderKind

object Notifier {

    const val CHANNEL_DEADLINES = "deadlines"
    const val CHANNEL_ROUTINES = "routines"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DEADLINES, "Deadlines", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Homework, appointments and other one-off things"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ROUTINES, "Daily routines", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Supplements, habits and other everyday things (silent)"
                setShowBadge(false)
            }
        )
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // hasPermission() is checked first and notify() is wrapped in runCatching
    fun show(context: Context, reminder: Reminder) {
        if (!hasPermission(context)) return
        val routine = reminder.kind == ReminderKind.ROUTINE
        val id = notificationId(reminder.id)
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val done = PendingIntent.getBroadcast(
            context, id,
            Intent(context, ActionReceiver::class.java)
                .setAction(ActionReceiver.ACTION_DONE)
                .putExtra(ActionReceiver.EXTRA_ID, reminder.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, if (routine) CHANNEL_ROUTINES else CHANNEL_DEADLINES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${reminder.emoji} ${reminder.title}")
            .setContentText(reminder.details.ifBlank { reminder.rawInput })
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.details.ifBlank { reminder.rawInput }))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(if (routine) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(0, if (routine) "Done today" else "Done", done)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    fun cancel(context: Context, reminderId: String) {
        runCatching { NotificationManagerCompat.from(context).cancel(notificationId(reminderId)) }
    }

    private fun notificationId(reminderId: String) = reminderId.hashCode() and 0x7fffffff
}
