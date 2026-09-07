package com.itzsuli.smartreminder

import android.app.Application
import android.content.Context
import com.itzsuli.smartreminder.data.ReminderRepository
import com.itzsuli.smartreminder.data.SettingsStore
import com.itzsuli.smartreminder.schedule.Notifier

class SmartReminderApp : Application() {

    lateinit var settings: SettingsStore
        private set
    lateinit var repository: ReminderRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        repository = ReminderRepository(this)
        Notifier.ensureChannels(this)
    }

    companion object {
        fun get(context: Context): SmartReminderApp = context.applicationContext as SmartReminderApp
    }
}
