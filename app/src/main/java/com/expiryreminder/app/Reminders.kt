package com.expiryreminder.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object Reminders {
    const val ACTION_DAILY_CHECK = "com.expiryreminder.app.DAILY_CHECK"
    const val CHECK_HOUR = 9

    private const val CHANNEL_ID = "expiry"

    // Days before expiry at which a reminder is shown, most urgent first.
    private val STAGES = listOf(0, 1, 7, 30)

    private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    fun formatDate(date: LocalDate): String = date.format(dateFormat)

    /** The reminder stage an item is currently in, or null if it is more than 30 days away. */
    fun stageFor(daysLeft: Long): Int? = STAGES.firstOrNull { daysLeft <= it }

    fun dueText(context: Context, daysLeft: Long): String = when {
        daysLeft < 0 -> context.resources.getQuantityString(
            R.plurals.expired_days_ago, (-daysLeft).toInt(), (-daysLeft).toInt()
        )
        daysLeft == 0L -> context.getString(R.string.expires_today)
        daysLeft == 1L -> context.getString(R.string.expires_tomorrow)
        else -> context.getString(R.string.expires_in_days, daysLeft.toInt())
    }

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        )
        channel.description = context.getString(R.string.channel_description)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Sets an alarm for the next 9:00. The receiver re-arms it after every run. */
    fun scheduleDailyCheck(context: Context) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(CHECK_HOUR, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_DAILY_CHECK)
        val pending = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    }

    /** Notifies once for every item that has entered a new reminder stage since the last check. */
    fun checkAndNotify(context: Context) {
        val items = ItemStore.load(context)
        var changed = false
        val updated = items.map { item ->
            val daysLeft = item.daysLeft()
            val stage = stageFor(daysLeft)
            if (stage != null && stage < item.notifiedStage) {
                notify(context, item, daysLeft)
                changed = true
                item.copy(notifiedStage = stage)
            } else {
                item
            }
        }
        if (changed) ItemStore.save(context, updated)
    }

    fun cancel(context: Context, item: Item) {
        NotificationManagerCompat.from(context).cancel(item.id.hashCode())
    }

    private fun notify(context: Context, item: Item, daysLeft: Long) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val text = context.getString(
            R.string.notification_text, dueText(context, daysLeft), formatDate(item.expiry)
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(item.name)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(item.id.hashCode(), notification)
        } catch (e: SecurityException) {
            // Permission was revoked between the check above and here.
        }
    }
}
