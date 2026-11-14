package com.moagauge.pleos.startup

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.moagauge.pleos.MainActivity
import com.moagauge.pleos.R

/**
 * Pleos exposes no documented public boarding-to-foreground launch hook for this app.
 * BOOT_COMPLETED offers a user-tapped launch notification; it never starts an activity,
 * service, route, overlay, or repeating poll from the background.
 */
class StartupBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        StartupLaunch.onSystemBoot(context, intent.action)
    }
}

object StartupLaunch {
    private const val CHANNEL = "app_startup"
    private const val NOTIFICATION_ID = 3102
    private const val HANDLED_BOOT = "handled_boot"

    private fun state(context: Context) = context.applicationContext.getSharedPreferences("startup_state", Context.MODE_PRIVATE)
    private fun bootCount(context: Context): Int? = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
    }.getOrNull()?.takeIf { it >= 0 }

    fun notificationsAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "앱 실행 안내", NotificationManager.IMPORTANCE_LOW).apply {
                description = "차량 시스템 시작 후 MOA DRIVE를 여는 알림"
                setSound(null, null)
            })
    }

    @Synchronized
    @SuppressLint("MissingPermission") // notificationsAllowed checks permission; notify also handles a revocation race.
    fun onSystemBoot(context: Context, action: String?) {
        val preferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val enabled = runCatching { preferences.getBoolean("auto_launch_on_boarding", false) }.getOrDefault(false)
        val boot = bootCount(context)
        val handled = runCatching { state(context).getInt(HANDLED_BOOT, -1) }.getOrDefault(-1)
        if (StartupPolicy.bootAction(action, enabled, notificationsAllowed(context), boot, handled) != StartupBootAction.SHOW_NOTIFICATION) return
        createChannel(context)
        val launch = Intent(context, MainActivity::class.java).apply {
            this.action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(context, NOTIFICATION_ID, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("MOA DRIVE 실행 준비")
            .setContentText("차량 시스템이 시작됐어요. 알림을 눌러 앱을 여세요.")
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true).setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        // Permission can be revoked between the check and notify. Do not mark a failed offer handled.
        val posted = runCatching { context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification) }.isSuccess
        if (posted && boot != null) state(context).edit().putInt(HANDLED_BOOT, boot).apply()
    }

    /** Opening an existing task must not reroute it or leave a stale launch offer behind. */
    @Synchronized
    fun onAppVisible(context: Context) {
        cancelNotification(context)
        bootCount(context)?.let { state(context).edit().putInt(HANDLED_BOOT, it).apply() }
    }

    fun cancelNotification(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    /** Called only by the parked Settings button, never from the boot receiver. */
    fun openNotificationSettings(context: Context): Boolean {
        createChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        val runtimePermissionDenied = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        val appBlocked = runtimePermissionDenied || !manager.areNotificationsEnabled()
        val intent = Intent(if (appBlocked) Settings.ACTION_APP_NOTIFICATION_SETTINGS else Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        if (!appBlocked) intent.putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL)
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }
}
