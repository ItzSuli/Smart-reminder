package com.itzsuli.smartreminder.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Alarms don't survive a reboot (or a time change), so arm the next one again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACCEPTED) return
        Scheduler.reschedule(context)
    }

    private companion object {
        val ACCEPTED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
