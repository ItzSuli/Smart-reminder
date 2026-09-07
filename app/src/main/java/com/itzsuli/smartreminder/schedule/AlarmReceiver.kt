package com.itzsuli.smartreminder.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/** Woken by AlarmManager; keeps the process alive just long enough to show the pop-up. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val finished = AtomicBoolean(false)
        val finish = { if (finished.compareAndSet(false, true)) pending.finish() }
        // Never hold the broadcast longer than the system allows.
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 9_500L)
        try {
            FireEngine.fire(context, finish)
        } catch (t: Throwable) {
            Log.e("AlarmReceiver", "fire failed", t)
            finish()
        }
    }
}
