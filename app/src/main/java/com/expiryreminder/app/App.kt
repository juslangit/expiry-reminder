package com.expiryreminder.app

import android.app.Application
import com.google.android.material.color.DynamicColors

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        Reminders.createChannel(this)
        Reminders.scheduleDailyCheck(this)
    }
}
