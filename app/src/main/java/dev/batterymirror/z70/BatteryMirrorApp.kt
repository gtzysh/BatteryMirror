package dev.batterymirror.z70

import android.app.Application
import dev.batterymirror.z70.data.BatteryRepository
import dev.batterymirror.z70.data.HistoryStore
import dev.batterymirror.z70.data.SettingsStore

/**
 * 【应用】把仓库放在进程里，界面和后台记录读同一份电量。
 */
class BatteryMirrorApp : Application() {
    lateinit var repository: BatteryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = BatteryRepository(
            context = this,
            settingsStore = SettingsStore(this),
            historyStore = HistoryStore(this),
        )
    }
}
