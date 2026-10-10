package dev.batterymirror.z70.data

import dev.batterymirror.core.CapacityAssessment
import dev.batterymirror.core.ChargeStatus
import dev.batterymirror.core.DeviceProfile
import dev.batterymirror.core.UsageSummary
import dev.batterymirror.core.UserSettings
import dev.batterymirror.z70.probe.DiagnosticLine

/** 界面上的一个曲线点。value 对电量是百分比，对容量趋势是毫安时。 */
data class ChartPoint(
    val epochMillis: Long,
    val value: Float,
)

/**
 * 【界面状态】一次刷新后的整机快照。
 * 总览、用量、健康、设置读的是同一份，避免四个页面各算各的。
 */
data class MirrorUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val capturedAt: Long? = null,
    val levelPercent: Int? = null,
    val status: ChargeStatus = ChargeStatus.UNKNOWN,
    val statusLabel: String = "正在读取",
    val pluggedLabel: String = "",
    val healthFlagLabel: String = "",
    val technology: String? = null,
    val temperatureC: Double? = null,
    val voltageMv: Int? = null,
    val currentMa: Double? = null,
    val powerMw: Double? = null,
    val dischargeEtaMinutes: Double? = null,
    val chargeEtaMinutes: Double? = null,
    val assessment: CapacityAssessment? = null,
    val today: UsageSummary = UsageSummary.EMPTY,
    val levelSeries: List<ChartPoint> = emptyList(),
    val capacitySeries: List<ChartPoint> = emptyList(),
    val diagnostics: List<DiagnosticLine> = emptyList(),
    val settings: UserSettings = UserSettings(),
    val profile: DeviceProfile? = null,
    val systemHeadline: String = "",
    val modelLine: String = "",
    val displayLine: String = "",
    val sdkInt: Int = 0,
    val notes: List<String> = emptyList(),
    val monitorRunning: Boolean = false,
    val currentCorrected: Boolean = false,
    val ignoringOptimizations: Boolean = false,
    val sampleCount: Int = 0,
)
