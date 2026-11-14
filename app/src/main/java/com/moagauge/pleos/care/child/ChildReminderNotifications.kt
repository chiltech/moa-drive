package com.moagauge.pleos.care.child

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.moagauge.pleos.MainActivity
import com.moagauge.pleos.R
import java.time.Instant
import java.time.ZoneId

class ChildReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ChildReminderNotifications.ACTION_REMINDER -> ChildReminderNotifications.deliver(context, intent)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> ChildReminderNotifications.reschedule(context)
        }
    }
}

/** Inexact local alarms; permission and channel access are checked before scheduling and posting. */
object ChildReminderNotifications {
    const val ACTION_REMINDER = "com.moagauge.pleos.CHILD_CARE_REMINDER"
    private const val CHANNEL = "child_care_reminders"
    private const val EXTRA_ID = "reminder_id"
    private const val EXTRA_DATE = "due_date"
    private const val EXTRA_TRIGGER = "trigger_at"
    private const val NOTIFICATION_ID = 1

    private fun state(context: Context) = context.applicationContext
        .getSharedPreferences("moa_child_reminder_delivery_v1", Context.MODE_PRIVATE)

    private fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "자녀 예방접종 일정", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "직접 등록한 예방접종 일정의 하루 전 또는 당일 오전 안내"
            })
    }

    fun notificationsAllowed(context: Context): Boolean {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun alarmIntent(context: Context, id: String) = Intent(context, ChildReminderReceiver::class.java).apply {
        action = ACTION_REMINDER
        data = Uri.Builder().scheme("moa-child-reminder").authority("schedule").appendPath(id).build()
        putExtra(EXTRA_ID, id)
    }

    @Synchronized
    fun reschedule(context: Context, data: ChildCareData = ChildCareStore(context).load()) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val notifications = context.getSystemService(NotificationManager::class.java)
        val preferences = state(context)
        val previousIds = preferences.getStringSet("ids", emptySet()).orEmpty().toSet()
        val current = data.reminders.associateBy { it.id }
        val edits = preferences.edit().putStringSet("ids", current.keys)
        val allowed = notificationsAllowed(context)
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        (previousIds + current.keys).forEach { id ->
            val reminder = current[id]
            val deliveredDate = preferences.getString("delivered:$id", null)
            if (reminder == null || reminder.completedOn != null || !reminder.notificationsEnabled ||
                (deliveredDate != null && deliveredDate != reminder.date.toString())) notifications.cancel("child:$id", NOTIFICATION_ID)
            if (reminder == null) edits.remove("delivered:$id")

            val scheduledDate = parseChildReminderDate(preferences.getString("scheduled_date:$id", null).orEmpty())
            val scheduledZone = preferences.getString("scheduled_zone:$id", null)
            val scheduled = if (scheduledDate != null && scheduledZone != null && preferences.contains("scheduled_at:$id")) {
                ChildScheduledReminder(scheduledDate, Instant.ofEpochMilli(preferences.getLong("scheduled_at:$id", 0)), scheduledZone)
            } else null
            val target = if (allowed && reminder != null) childReminderNotificationTime(
                reminder, now, zone, parseChildReminderDate(deliveredDate.orEmpty()), scheduled) else null
            if (target == null || reminder == null) {
                PendingIntent.getBroadcast(context, 0, alarmIntent(context, id), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
                    alarmManager.cancel(it); it.cancel()
                }
                edits.remove("scheduled_date:$id").remove("scheduled_at:$id").remove("scheduled_zone:$id")
            } else {
                val intent = alarmIntent(context, id).putExtra(EXTRA_DATE, reminder.date.toString()).putExtra(EXTRA_TRIGGER, target.toEpochMilli())
                val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                // Replaces the same pending intent without cancelling a delayed, undelivered alarm.
                // A registered trigger in the past is rearmed; a newly entered late reminder is not.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.toEpochMilli(), pending)
                edits.putString("scheduled_date:$id", reminder.date.toString())
                    .putLong("scheduled_at:$id", target.toEpochMilli()).putString("scheduled_zone:$id", zone.id)
            }
        }
        edits.apply()
    }

    @Synchronized
    @SuppressLint("MissingPermission") // Checked above; posting also handles a permission revocation race.
    fun deliver(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val scheduledDate = parseChildReminderDate(intent.getStringExtra(EXTRA_DATE).orEmpty()) ?: return
        val triggerAt = intent.getLongExtra(EXTRA_TRIGGER, Long.MAX_VALUE)
        if (triggerAt > System.currentTimeMillis()) return
        if (!notificationsAllowed(context)) {
            // A blocked delivery is not replayed as a catch-up when access is later enabled.
            state(context).edit().remove("scheduled_date:$id").remove("scheduled_at:$id").remove("scheduled_zone:$id").apply()
            return
        }
        val reminder = ChildCareStore(context).load().reminders.firstOrNull { it.id == id } ?: return
        val preferences = state(context)
        val deliveredFor = parseChildReminderDate(preferences.getString("delivered:$id", null).orEmpty())
        if (!childReminderCanNotify(reminder, scheduledDate, deliveredFor, childLocalDate())) return
        val openApp = PendingIntent.getActivity(context, 3201,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("자녀 케어 · 예방접종 일정")
            .setContentText("${reminder.title} · ${reminder.date.monthValue}월 ${reminder.date.dayOfMonth}일 일정을 확인해 주세요.")
            .setContentIntent(openApp).setAutoCancel(true).setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        val posted = runCatching { context.getSystemService(NotificationManager::class.java).notify("child:$id", NOTIFICATION_ID, notification) }.isSuccess
        if (posted) preferences.edit().putString("delivered:$id", reminder.date.toString())
            .remove("scheduled_date:$id").remove("scheduled_at:$id").remove("scheduled_zone:$id").commit()
    }

    /** User action from the parked form only; no permission prompt is launched automatically. */
    fun openNotificationSettings(context: Context): Boolean {
        ensureChannel(context)
        val appBlocked = (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) ||
            !context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        val intent = Intent(if (appBlocked) Settings.ACTION_APP_NOTIFICATION_SETTINGS else Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        if (!appBlocked) intent.putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL)
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }
}
