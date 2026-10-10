package dev.batterymirror.z70.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** 【文案】把毫安、瓦和时长收成界面上的短句。 */

fun formatClock(epochMillis: Long?, pattern: String = "HH:mm:ss"): String {
    if (epochMillis == null) return ""
    val formatter = DateTimeFormatter.ofPattern(pattern).withZone(ZoneId.systemDefault())
    return formatter.format(Instant.ofEpochMilli(epochMillis))
}

fun formatDurationMinutes(minutes: Double?): String {
    if (minutes == null || minutes.isNaN() || minutes.isInfinite() || minutes < 0.0) return "还不好说"
    if (minutes > 72 * 60) return "超过 3 天"
    val total = minutes.roundToInt()
    val hours = total / 60
    val remain = total % 60
    return when {
        hours <= 0 -> "$remain 分钟"
        remain == 0 -> "$hours 小时"
        else -> "$hours 小时 $remain 分钟"
    }
}

fun formatSpan(millis: Long): String {
    val minutes = millis / 60_000L
    val hours = minutes / 60
    val remain = minutes % 60
    return if (hours > 0) "$hours 小时 $remain 分钟" else "$remain 分钟"
}

fun formatWatt(powerMw: Double?): String {
    if (powerMw == null) return "—"
    val watts = powerMw / 1000.0
    return if (watts < 1.0) String.format(Locale.US, "%.2f W", watts) else String.format(Locale.US, "%.1f W", watts)
}

fun formatCurrent(currentMa: Double?): String {
    if (currentMa == null) return "电流不可用"
    val magnitude = abs(currentMa)
    val number = if (magnitude >= 100) magnitude.roundToInt().toString() else String.format(Locale.US, "%.0f", magnitude)
    return when {
        currentMa >= 30 -> "充电 $number mA"
        currentMa <= -30 -> "放电 $number mA"
        else -> "几乎不耗电"
    }
}

fun formatVolt(voltageMv: Int?): String {
    if (voltageMv == null) return "—"
    return String.format(Locale.US, "%.2f V", voltageMv / 1000.0)
}

fun formatTemp(celsius: Double?): String {
    if (celsius == null) return "—"
    return String.format(Locale.US, "%.1f°C", celsius)
}

fun formatMah(value: Double?): String = if (value == null) "—" else "${value.roundToInt()} mAh"

fun formatPercent(value: Double?): String {
    if (value == null) return "—"
    return String.format(Locale.US, "%.1f%%", value)
}

fun formatSignedMah(value: Double): String = "${value.roundToInt()} mAh"
