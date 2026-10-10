package dev.batterymirror.z70.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.batterymirror.z70.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 【开机】只有用户打开过持续记录，才在开机后恢复采样。
 * 系统拒绝后台启动前台服务时直接停下，下次打开应用会再试。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val enabled = SettingsStore(appContext).current().monitorEnabled
                if (enabled && MonitorService.notificationsAllowed(appContext)) {
                    MonitorService.start(appContext)
                }
            } catch (_: Throwable) {
                // 开机阶段系统可能不允许拉起前台服务。
            } finally {
                pending.finish()
            }
        }
    }
}
