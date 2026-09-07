package com.itzsuli.smartreminder.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.itzsuli.smartreminder.SmartReminderApp
import com.itzsuli.smartreminder.data.ReminderKind

/** Handles the "Done" button on notifications. */
class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DONE) return
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val repo = SmartReminderApp.get(context).repository
        val reminder = repo.get(id) ?: return
        if (reminder.kind == ReminderKind.ROUTINE) repo.setDoneForToday(id, true) else repo.setDone(id, true)
        Notifier.cancel(context, id)
    }

    companion object {
        const val ACTION_DONE = "com.itzsuli.smartreminder.action.DONE"
        const val EXTRA_ID = "reminder_id"
    }
}
