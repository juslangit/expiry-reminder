package com.expiryreminder.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalTime

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Reminders.ACTION_DAILY_CHECK -> Reminders.checkAndNotify(context)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // Alarms don't survive a reboot. If today's 9:00 check was missed, run it now.
                if (LocalTime.now().hour >= Reminders.CHECK_HOUR) Reminders.checkAndNotify(context)
            }
            else -> return
        }
        Reminders.scheduleDailyCheck(context)
    }
}
