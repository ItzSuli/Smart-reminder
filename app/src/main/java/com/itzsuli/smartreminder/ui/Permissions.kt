package com.itzsuli.smartreminder.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.itzsuli.smartreminder.schedule.Notifier
import com.itzsuli.smartreminder.schedule.Popup

data class PermissionStatus(
    val notifications: Boolean,
    val overlay: Boolean,
    val exactAlarms: Boolean,
    val batteryUnrestricted: Boolean,
) {
    val allGood: Boolean get() = notifications && overlay && exactAlarms && batteryUnrestricted
}

object Permissions {

    fun status(context: Context): PermissionStatus {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || (alarmManager?.canScheduleExactAlarms() ?: true)
        val pm = context.getSystemService(PowerManager::class.java)
        val battery = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        return PermissionStatus(
            notifications = Notifier.hasPermission(context),
            overlay = Popup.canDraw(context),
            exactAlarms = exact,
            batteryUnrestricted = battery,
        )
    }

    private fun packageUri(context: Context): Uri = Uri.parse("package:${context.packageName}")

    fun overlayIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri(context))

    fun exactAlarmIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri(context)) else null

    fun batteryIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context))

    fun notificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun appSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))
}
