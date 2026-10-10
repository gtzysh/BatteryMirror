package dev.batterymirror.core

import kotlin.math.abs
import kotlin.math.round

/**
 * 【单位】把内核电量计节点和 Android 电池接口的原始数，收成统一单位。
 * 高通节点按内核约定：电流微安、电压微伏、电荷微安时、温度十分之一摄氏度。
 */
object SysfsUnits {
    const val MIN_FULL_MAH = 1500
    const val MAX_FULL_MAH = 12000

    fun parseLong(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        val trimmed = text.trim().lineSequence().firstOrNull()?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val head = trimmed.takeWhile { it.isDigit() || it == '-' || it == '+' || it == '.' }
        if (head.isEmpty() || head == "-" || head == "+" || head == ".") return null
        return head.toLongOrNull() ?: head.toDoubleOrNull()?.let { round(it).toLong() }
    }

    /**
     * 【能量节点】部分手机只给 energy_full，单位是微瓦时或毫瓦时。
     * 用电压换成微安时。不传电压时固定按 3.85V，避免用当下端电压把额定容量算漂。
     */
    fun energyToUah(raw: Long?, voltageMv: Int?): Long? {
        if (raw == null || raw <= 0L) return null
        val mv = voltageMv?.takeIf { it in 3_000..4_500 } ?: 3_850
        val uah = when {
            raw >= 1_000_000L -> raw * 1_000L / mv
            raw in 2_000L..80_000L -> raw * 1_000_000L / mv
            else -> return null
        }
        return plausibleFullUah(uah)
    }

    /** 从 power_supply 的 uevent 里取出一个整数，节点文件读不到时用。 */
    fun ueventLong(text: String?, key: String): Long? {
        if (text.isNullOrBlank()) return null
        val prefix = "$key="
        val line = text.lineSequence().firstOrNull { it.startsWith(prefix) } ?: return null
        return parseLong(line.removePrefix(prefix))
    }

    /** 满充或设计容量。小于 10 万视为毫安时，否则视为微安时。 */
    fun chargeFullToUah(raw: Long?): Long? {
        if (raw == null || raw <= 0) return null
        val uah = if (raw < 100_000L) raw * 1000L else raw
        return plausibleFullUah(uah)
    }

    /**
     * 剩余容量。1500 到 12000 这种整数更像厂商直接写的毫安时；
     * 更大的数按微安时。快没电时几十毫安时的微安时读数不会落在这个区间里。
     */
    fun chargeRemainingToUah(raw: Long?): Long? {
        if (raw == null || raw <= 0) return null
        val uah = when {
            raw in 1_500L..12_000L -> raw * 1000L
            raw < 1_500L -> raw * 1000L
            else -> raw
        }
        return plausibleRemainingUah(uah)
    }

    /** Android BatteryManager 文档写明剩余容量就是微安时，不再猜单位。 */
    fun frameworkRemainingUah(raw: Long?): Long? {
        if (raw == null || raw <= 0L || raw == Long.MIN_VALUE) return null
        return plausibleRemainingUah(raw)
    }

    fun plausibleFullUah(uah: Long?): Long? {
        if (uah == null || uah <= 0L) return null
        val mah = uah / 1000.0
        if (mah < MIN_FULL_MAH || mah > MAX_FULL_MAH) return null
        return uah
    }

    fun plausibleRemainingUah(uah: Long?): Long? {
        if (uah == null || uah <= 0L) return null
        val mah = uah / 1000.0
        if (mah > MAX_FULL_MAH) return null
        return uah
    }

    fun plausibleCurrentUa(ua: Int?): Int? {
        if (ua == null || ua == Int.MIN_VALUE) return null
        if (abs(ua) > 15_000_000) return null
        return ua
    }

    /**
     * 【电流方向】Android 约定正电流是充进电池，负电流是放出。
     * 部分机型节点把放电写成正数，和充放电状态明显矛盾时才翻转。
     */
    fun normalizeCurrent(raw: Int?, status: ChargeStatus): Int? {
        val ua = plausibleCurrentUa(raw) ?: return null
        val contradicts = when (status) {
            ChargeStatus.DISCHARGING -> ua > 80_000
            ChargeStatus.CHARGING -> ua < -80_000
            else -> false
        }
        return if (contradicts) -ua else ua
    }

    /** 90、900、9000 都收成 90%。0 和明显不合理的值丢掉。 */
    fun interpretSoh(raw: Long?): Double? {
        if (raw == null || raw <= 0L) return null
        val candidates = listOf(raw.toDouble(), raw / 10.0, raw / 100.0)
        return candidates.firstOrNull { it in 40.0..100.0 }
    }

    fun temperatureC(raw: Long?): Double? {
        if (raw == null) return null
        // 【温度】内核算十分之一度；若读到毫度，先收成十分之一度。
        val tenths = if (abs(raw) > 2_000L) raw / 100L else raw
        val celsius = tenths / 10.0
        if (celsius < -20.0 || celsius > 80.0) return null
        return celsius
    }

    fun voltageMvFromMicrovolts(raw: Long?): Int? {
        if (raw == null || raw <= 0L) return null
        val mv = if (raw > 20_000L) (raw / 1000L).toInt() else raw.toInt()
        if (mv !in 2_500..5_000) return null
        return mv
    }

    fun plausibleCycles(raw: Long?): Int? {
        if (raw == null || raw < 0L || raw > 10_000L) return null
        return raw.toInt()
    }
}
