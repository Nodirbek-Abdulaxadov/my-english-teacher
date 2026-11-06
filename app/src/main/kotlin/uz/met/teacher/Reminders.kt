package uz.met.teacher

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.app.Notification
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import uz.met.teacher.data.Settings

object Reminders {
    private const val CHANNEL = "daily"

    private fun pendingIntent(context: Context) = PendingIntent.getBroadcast(
        context, 0, Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Sets the daily reminder at [hour], or removes it when the hour is negative. */
    fun schedule(context: Context, hour: Int) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.cancel(pendingIntent(context))
        if (hour < 0) return
        var at = LocalDateTime.now().withHour(hour).withMinute(0).withSecond(0)
        if (at.isBefore(LocalDateTime.now())) at = at.plusDays(1)
        alarms.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            AlarmManager.INTERVAL_DAY,
            pendingIntent(context),
        )
    }

    fun notify(context: Context) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Kunlik eslatma", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            1,
            Notification.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Bugun hali gapirmadingiz")
                .setContentText("Bir necha daqiqa suhbat ketma-ketlikni saqlab qoladi.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Settings(context).lastGoalDate != LocalDate.now().toString()) Reminders.notify(context)
    }
}

/** Alarms don't survive a restart, so this sets the reminder again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Reminders.schedule(context, Settings(context).reminderHour)
    }
}
