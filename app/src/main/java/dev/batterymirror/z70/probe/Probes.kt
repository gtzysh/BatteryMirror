package dev.batterymirror.z70.probe

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import dev.batterymirror.core.ChargeStatus
import dev.batterymirror.core.SysfsUnits
import dev.batterymirror.core.UsageSample
import java.io.File
import kotlin.math.roundToInt

/** 【诊断行】设置页用来展示并复制的一条原始读数。不包含序列号或 IMEI。 */
data class DiagnosticLine(val label: String, val value: String)

/**
 * 【采样】一次读取系统电池广播、BatteryManager，以及 /sys/class/power_supply。
 * Android 16 的健康度是属性 10。SDK 35 没有这个常量，星云没开放时会失败，不能当成 0%。
 */
class BatteryProbe(private val context: Context) {
    fun read(nowMillis: Long = System.currentTimeMillis()): ProbeSnapshot {
        val lines = mutableListOf<DiagnosticLine>()
        fun line(label: String, value: String?) {
            if (value.isNullOrBlank() || lines.size >= 40) return
            lines += DiagnosticLine(label, value.trim().lineSequence().first().take(80))
        }

        val sticky = readSticky()
        val manager = context.getSystemService(BatteryManager::class.java)
        val status = statusOf(sticky)
        val level = levelOf(sticky)
        val intentVoltage = sticky?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.takeIf { it in 2_500..5_000 }
        val intentTenths = sticky?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }
        val technology = sticky?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)

        val frameworkCurrent = manager?.let { intProp(it, BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) }
        val frameworkRemaining = manager?.let { longProp(it, BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) }
        val frameworkSohRaw = manager?.let { intProp(it, BATTERY_PROPERTY_STATE_OF_HEALTH) }
        line("系统电流 µA", frameworkCurrent?.toString())
        line("系统剩余 µAh", frameworkRemaining?.toString())
        line("系统健康度原始值", frameworkSohRaw?.toString() ?: "不可用")

        val sysfs = readSysfs(lines)
        val rawCurrent = SysfsUnits.plausibleCurrentUa(frameworkCurrent) ?: sysfs.currentUa
        val normalized = SysfsUnits.normalizeCurrent(rawCurrent, status)
        val corrected = rawCurrent != null && normalized != null && rawCurrent != normalized
        val remaining = SysfsUnits.frameworkRemainingUah(frameworkRemaining) ?: sysfs.remainingUah
        val temperatureC = SysfsUnits.temperatureC(intentTenths?.toLong()) ?: sysfs.temperatureC
        val voltageMv = intentVoltage ?: sysfs.voltageMv
        val resolvedLevel = level ?: sysfs.levelPercent
        val screenOn = context.getSystemService(PowerManager::class.java)?.isInteractive == true

        return ProbeSnapshot(
            epochMillis = nowMillis,
            levelPercent = resolvedLevel,
            status = status,
            statusLabel = statusLabel(status),
            pluggedLabel = pluggedLabel(sticky),
            healthFlagLabel = healthFlagLabel(sticky),
            technology = technology,
            temperatureC = temperatureC,
            temperatureTenthsC = temperatureC?.let { (it * 10.0).roundToInt() },
            voltageMv = voltageMv,
            currentMicroamps = normalized,
            currentCorrected = corrected,
            remainingUah = remaining,
            gaugeFullUah = sysfs.fullUah,
            gaugeDesignUah = sysfs.designUah,
            gaugeSohPercent = sysfs.sohPercent,
            frameworkSohPercent = SysfsUnits.interpretSoh(frameworkSohRaw?.toLong()),
            cycleCount = sysfs.cycleCount,
            screenInteractive = screenOn,
            diagnostics = lines,
        )
    }

    private fun readSticky(): Intent? = try {
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    } catch (_: Throwable) {
        null
    }

    private fun intProp(manager: BatteryManager, id: Int): Int? = try {
        val value = manager.getIntProperty(id)
        if (value == Int.MIN_VALUE) null else value
    } catch (_: Throwable) {
        null
    }

    private fun longProp(manager: BatteryManager, id: Int): Long? = try {
        val value = manager.getLongProperty(id)
        if (value == Long.MIN_VALUE) null else value
    } catch (_: Throwable) {
        null
    }

    private fun readSysfs(lines: MutableList<DiagnosticLine>): SysfsReading {
        val root = File("/sys/class/power_supply")
        val dirs = root.listFiles()?.filter { it.isDirectory }.orEmpty()
        val ranked = dirs.sortedByDescending { scoreDir(it) }
        var current: Int? = null
        var full: Long? = null
        var design: Long? = null
        var remaining: Long? = null
        var soh: Double? = null
        var cycles: Int? = null
        var voltage: Int? = null
        var temperature: Double? = null
        var level: Int? = null
        for (dir in ranked) {
            fun text(name: String): String? = readNode(dir, name)
            fun add(name: String) {
                val raw = text(name) ?: return
                if (lines.size < 40) lines += DiagnosticLine("${dir.name}/$name", raw.trim().lineSequence().first().take(80))
            }
            add("current_now")
            add("current_avg")
            add("voltage_now")
            add("charge_full")
            add("charge_full_design")
            add("energy_full")
            add("energy_full_design")
            add("design_capacity")
            add("fg_fullcapnom")
            add("fg_full_charge_capacity")
            add("charge_counter")
            add("charge_now")
            add("soh")
            add("battery_soh")
            add("cycle_count")
            add("temp")
            add("capacity")
            if (current == null) {
                current = SysfsUnits.plausibleCurrentUa(asMicroamp(SysfsUnits.parseLong(text("current_now"))))
                    ?: SysfsUnits.plausibleCurrentUa(asMicroamp(SysfsUnits.parseLong(text("current_avg"))))
            }
            val uevent = text("uevent")
            if (full == null) {
                full = SysfsUnits.chargeFullToUah(SysfsUnits.parseLong(text("charge_full")))
                    ?: SysfsUnits.chargeFullToUah(SysfsUnits.ueventLong(uevent, "POWER_SUPPLY_CHARGE_FULL"))
                    ?: SysfsUnits.chargeFullToUah(SysfsUnits.parseLong(text("fg_full_charge_capacity")))
                    // 【额定换算】能量节点按 3.85V 换算，不用当下电压，避免容量跟着电量百分比漂。
                    ?: SysfsUnits.energyToUah(SysfsUnits.parseLong(text("energy_full")), null)
                    ?: SysfsUnits.energyToUah(SysfsUnits.ueventLong(uevent, "POWER_SUPPLY_ENERGY_FULL"), null)
            }
            if (design == null) {
                design = SysfsUnits.chargeFullToUah(SysfsUnits.parseLong(text("charge_full_design")))
                    ?: SysfsUnits.chargeFullToUah(SysfsUnits.ueventLong(uevent, "POWER_SUPPLY_CHARGE_FULL_DESIGN"))
                    ?: SysfsUnits.chargeFullToUah(SysfsUnits.parseLong(text("design_capacity")))
                    ?: SysfsUnits.chargeFullToUah(SysfsUnits.parseLong(text("fg_fullcapnom")))
                    ?: SysfsUnits.energyToUah(SysfsUnits.parseLong(text("energy_full_design")), null)
                    ?: SysfsUnits.energyToUah(
                        SysfsUnits.ueventLong(uevent, "POWER_SUPPLY_ENERGY_FULL_DESIGN"),
                        null,
                    )
            }
            if (remaining == null) {
                remaining = SysfsUnits.chargeRemainingToUah(SysfsUnits.parseLong(text("charge_counter")))
                    ?: SysfsUnits.chargeRemainingToUah(SysfsUnits.parseLong(text("charge_now")))
            }
            if (soh == null) {
                soh = SysfsUnits.interpretSoh(SysfsUnits.parseLong(text("soh")))
                    ?: SysfsUnits.interpretSoh(SysfsUnits.parseLong(text("battery_soh")))
            }
            if (cycles == null) cycles = SysfsUnits.plausibleCycles(SysfsUnits.parseLong(text("cycle_count")))
            if (voltage == null) voltage = SysfsUnits.voltageMvFromMicrovolts(SysfsUnits.parseLong(text("voltage_now")))
            if (temperature == null) temperature = SysfsUnits.temperatureC(SysfsUnits.parseLong(text("temp")))
            if (level == null) {
                level = SysfsUnits.parseLong(text("capacity"))?.toInt()?.takeIf { it in 0..100 }
            }
        }
        return SysfsReading(current, full, design, remaining, soh, cycles, voltage, temperature, level)
    }

    private fun scoreDir(dir: File): Int {
        var score = 0
        if (dir.name.equals("battery", ignoreCase = true)) score += 5
        val type = readNode(dir, "type")
        if (type?.contains("Battery", ignoreCase = true) == true) score += 4
        if (readNode(dir, "charge_full") != null) score += 2
        return score
    }

    /** 节点里的电流可能超过 Int，先丢掉，避免溢成另一个方向。 */
    private fun asMicroamp(value: Long?): Int? {
        if (value == null || value <= Int.MIN_VALUE.toLong() || value > Int.MAX_VALUE.toLong()) return null
        return value.toInt()
    }

    private fun readNode(dir: File, name: String): String? = try {
        val file = File(dir, name)
        if (!file.isFile || !file.canRead()) null else file.readText()
    } catch (_: Throwable) {
        null
    }

    private fun statusOf(intent: Intent?): ChargeStatus {
        val code = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        return when (code) {
            BatteryManager.BATTERY_STATUS_CHARGING -> ChargeStatus.CHARGING
            BatteryManager.BATTERY_STATUS_DISCHARGING -> ChargeStatus.DISCHARGING
            BatteryManager.BATTERY_STATUS_FULL -> ChargeStatus.FULL
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> ChargeStatus.NOT_CHARGING
            else -> ChargeStatus.UNKNOWN
        }
    }

    private fun levelOf(intent: Intent?): Int? {
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: return null
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return null
        return (level * 100 / scale).coerceIn(0, 100)
    }

    private fun statusLabel(status: ChargeStatus): String = when (status) {
        ChargeStatus.CHARGING -> "充电中"
        ChargeStatus.DISCHARGING -> "放电中"
        ChargeStatus.FULL -> "已充满"
        ChargeStatus.NOT_CHARGING -> "未充电"
        ChargeStatus.UNKNOWN -> "状态未知"
    }

    private fun pluggedLabel(intent: Intent?): String {
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return when (plugged) {
            0 -> "未插电"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_AC -> "交流电"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "无线充电"
            BatteryManager.BATTERY_PLUGGED_DOCK -> "扩展坞"
            else -> "已插电"
        }
    }

    private fun healthFlagLabel(intent: Intent?): String {
        val health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        return when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "良好"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "过热"
            BatteryManager.BATTERY_HEALTH_DEAD -> "没电"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "过压"
            BatteryManager.BATTERY_HEALTH_COLD -> "过冷"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "故障"
            else -> "未知"
        }
    }

    private data class SysfsReading(
        val currentUa: Int?,
        val fullUah: Long?,
        val designUah: Long?,
        val remainingUah: Long?,
        val sohPercent: Double?,
        val cycleCount: Int?,
        val voltageMv: Int?,
        val temperatureC: Double?,
        val levelPercent: Int?,
    )

    companion object {
        /** Android 16 BatteryManager.BATTERY_PROPERTY_STATE_OF_HEALTH。 */
        const val BATTERY_PROPERTY_STATE_OF_HEALTH = 10
    }
}

/** 【快照】界面和记录共用的一次读数。电流已经按充电为正、放电为负调整过。 */
data class ProbeSnapshot(
    val epochMillis: Long,
    val levelPercent: Int?,
    val status: ChargeStatus,
    val statusLabel: String,
    val pluggedLabel: String,
    val healthFlagLabel: String,
    val technology: String?,
    val temperatureC: Double?,
    val temperatureTenthsC: Int?,
    val voltageMv: Int?,
    val currentMicroamps: Int?,
    val currentCorrected: Boolean,
    val remainingUah: Long?,
    val gaugeFullUah: Long?,
    val gaugeDesignUah: Long?,
    val gaugeSohPercent: Double?,
    val frameworkSohPercent: Double?,
    val cycleCount: Int?,
    val screenInteractive: Boolean,
    val diagnostics: List<DiagnosticLine>,
) {
    fun toSample(): UsageSample? {
        val level = levelPercent ?: return null
        return UsageSample(
            epochMillis = epochMillis,
            levelPercent = level,
            currentMicroamps = currentMicroamps,
            voltageMv = voltageMv,
            temperatureTenthsC = temperatureTenthsC,
            remainingUah = remainingUah,
            status = status,
            screenInteractive = screenInteractive,
        )
    }
}
