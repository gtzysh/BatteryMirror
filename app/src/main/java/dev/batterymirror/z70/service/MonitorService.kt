package dev.batterymirror.z70.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.batterymirror.z70.BatteryMirrorApp
import dev.batterymirror.z70.MainActivity
import dev.batterymirror.z70.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 【持续记录】前台服务每 5 秒采一次。
 * 不持有唤醒锁。通知保持低优先级，只说明正在记录整机电量。
 */
class MonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        if (loopJob?.isActive == true) return START_STICKY
        val repository = (application as BatteryMirrorApp).repository
        loopJob = scope.launch {
            while (isActive) {
                val enabled = repository.currentSettings().monitorEnabled
                if (!enabled || !notificationsAllowed(this@MonitorService)) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    break
                }
                repository.refresh()
                delay(5_000)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        // 【退出】Application.onCreate 里已经建好仓库，这里直接刷出还没落盘的采样。
        val repository = (application as? BatteryMirrorApp)?.repository
        if (repository != null) {
            Thread {
                kotlinx.coroutines.runBlocking(Dispatchers.IO) { repository.flush() }
            }.apply {
                start()
                join(1_000)
            }
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.monitor_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
            channel.description = getString(R.string.monitor_channel_desc)
            channel.setSound(null, null)
            manager.createNotificationChannel(channel)
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_battery)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("正在记录电流、电量和满充容量")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "battery_mirror_monitor"
        private const val NOTIFICATION_ID = 70

        fun notificationsAllowed(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < 33) return true
            return ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        fun start(context: Context) {
            val intent = Intent(context, MonitorService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }
    }
}
