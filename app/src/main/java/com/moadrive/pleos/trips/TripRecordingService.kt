package com.moadrive.pleos.trips

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.Manifest
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.moadrive.pleos.MainActivity
import com.moadrive.pleos.R
import kotlinx.coroutines.*

class TripRecordingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var runtime: TripRuntime
    private var foregroundReady = false
    private var locationForeground = false

    override fun onCreate() {
        super.onCreate()
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, "차량 주행기록", NotificationManager.IMPORTANCE_LOW))
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification("자동 주행기록 연결 중"),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
            foregroundReady = true
        } catch (_: RuntimeException) {
            TripRuntime.get(this).serviceStartFailed()
            stopSelf()
            return
        }
        runtime = TripRuntime.get(this)
        serviceScope.launch {
            runtime.message.collect { text ->
                if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this@TripRecordingService,
                        Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    notifications.notify(NOTIFICATION_ID, notification(text))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!foregroundReady) return START_NOT_STICKY
        // A killed UI-only connection must not restart as a permanent background logger.
        if (intent == null && (!runtime.enabled.value || !TripRuntime.hasSpeedPermission(this))) {
            stopSelf()
            return START_NOT_STICKY
        }
        runtime.serviceStarted(intent?.action == ACTION_REFRESH)
        // A visible, permission-authorized activity may promote an already running recorder.
        // Boot/sticky starts retain vehicle-only recording until the user opens the app.
        if (!locationForeground && runtime.canPromotePassportLocation()) {
            try {
                val type = (if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0) or
                    (if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification("주행·방문 구역 자동 기록 중"), type)
                locationForeground = true
            } catch (_: RuntimeException) {
                // Keep speed/energy trip recording alive even if location promotion is denied.
                locationForeground = false
            }
        }
        runtime.setPassportLocationAuthorized(locationForeground && runtime.hasFineLocationPermission())
        return if (runtime.enabled.value && TripRuntime.hasSpeedPermission(this)) START_STICKY else START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        if (::runtime.isInitialized) runtime.serviceStopped()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(text: String): Notification {
        val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("모아 드라이브 · 차량 기록")
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val ACTION_REFRESH = "com.moadrive.pleos.REFRESH_VEHICLE_PERMISSIONS"
        private const val CHANNEL = "automatic_trip_recording"
        private const val NOTIFICATION_ID = 1401
    }
}
