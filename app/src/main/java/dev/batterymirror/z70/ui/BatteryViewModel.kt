package dev.batterymirror.z70.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.batterymirror.core.BaselineChoice
import dev.batterymirror.core.ProfileChoice
import dev.batterymirror.z70.BatteryMirrorApp
import dev.batterymirror.z70.service.MonitorService
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 【界面模型】应用在前台时每 2 秒刷新一次。
 * 持续记录的开关写完设置后再启动服务，避免服务读到旧开关。
 */
class BatteryViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = (app as BatteryMirrorApp).repository
    val state = repository.state

    init {
        viewModelScope.launch {
            while (isActive) {
                repository.refresh()
                delay(2_000)
            }
        }
    }

    fun refreshNow() {
        viewModelScope.launch { repository.refresh() }
    }

    fun setBaseline(value: BaselineChoice) {
        viewModelScope.launch { repository.setBaseline(value) }
    }

    fun setProfile(value: ProfileChoice) {
        viewModelScope.launch { repository.setProfile(value) }
    }

    fun setCustomMah(value: Int) {
        viewModelScope.launch { repository.setCustomMah(value) }
    }

    fun setMonitor(enabled: Boolean, after: () -> Unit = {}) {
        viewModelScope.launch {
            repository.setMonitor(enabled)
            after()
        }
    }

    fun clearRecords() {
        viewModelScope.launch { repository.clearRecords() }
    }

    fun ensureMonitor(context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val enabled = repository.currentSettings().monitorEnabled
            if (enabled && MonitorService.notificationsAllowed(appContext)) {
                MonitorService.start(appContext)
            }
        }
    }
}
