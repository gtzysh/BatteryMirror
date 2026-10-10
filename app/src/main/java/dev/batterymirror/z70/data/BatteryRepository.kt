package dev.batterymirror.z70.data

import android.content.Context
import android.os.Build
import android.os.PowerManager
import dev.batterymirror.core.BaselineChoice
import dev.batterymirror.core.CapacityAssessment
import dev.batterymirror.core.CapacityInput
import dev.batterymirror.core.CapacityMath
import dev.batterymirror.core.CapacityPoint
import dev.batterymirror.core.ChargeStatus
import dev.batterymirror.core.DeviceProfile
import dev.batterymirror.core.DeviceProfiles
import dev.batterymirror.core.ProfileChoice
import dev.batterymirror.core.SystemBuildParser
import dev.batterymirror.core.UsageMath
import dev.batterymirror.core.UsageSample
import dev.batterymirror.core.UserSettings
import dev.batterymirror.z70.probe.BatteryProbe
import dev.batterymirror.z70.widget.HomeWidgetUpdater
import dev.batterymirror.z70.probe.DiagnosticLine
import dev.batterymirror.z70.probe.ProbeSnapshot
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 【仓库】把一次采样收成总览、用量、健康、设置共用的状态。
 * 界面大约 2 秒读一次当前值；满 5 秒才记一条样本，满 30 秒才写文件。
 */
class BatteryRepository(
    private val context: Context,
    private val settingsStore: SettingsStore,
    private val historyStore: HistoryStore,
    private val probe: BatteryProbe = BatteryProbe(context),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val memory = mutableListOf<UsageSample>()
    private val pending = mutableListOf<UsageSample>()
    private var loaded = false
    /** 【清空】刚清掉的记录不要在同一次刷新里又记回来。 */
    private var learnPausedUntil = 0L
    private val _state = MutableStateFlow(MirrorUiState())
    val state: StateFlow<MirrorUiState> = _state.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                _state.value = capture(clock())
            } catch (error: Throwable) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = "读取电池失败：${error.message ?: "未知错误"}",
                )
            }
        }
        val latest = _state.value
        if (latest.capturedAt != null) {
            runCatching { HomeWidgetUpdater.updateAll(context, latest) }
        }
    }

    suspend fun flush() = withContext(Dispatchers.IO) {
        mutex.withLock { flushPending(clock(), force = true) }
    }

    suspend fun currentSettings(): UserSettings = settingsStore.current()

    suspend fun setBaseline(value: BaselineChoice) {
        settingsStore.update { it.copy(baseline = value) }
        refresh()
    }

    suspend fun setProfile(value: ProfileChoice) {
        settingsStore.update { it.copy(profileChoice = value) }
        refresh()
    }

    suspend fun setCustomMah(value: Int) {
        if (value !in 1_500..12_000) return
        settingsStore.update { it.copy(customDesignMah = value) }
        refresh()
    }

    suspend fun setMonitor(enabled: Boolean) {
        settingsStore.update { it.copy(monitorEnabled = enabled) }
        refresh()
    }

    suspend fun clearRecords() {
        mutex.withLock {
            historyStore.clear()
            memory.clear()
            pending.clear()
            loaded = true
            learnPausedUntil = clock() + 60_000L
            settingsStore.update { it.copy(learnedFullUah = emptyList(), learnedAtMillis = 0L) }
        }
        refresh()
    }

    private suspend fun capture(now: Long): MirrorUiState {
        if (!loaded) {
            memory.clear()
            memory.addAll(historyStore.loadSamples(now - MEMORY_WINDOW_MS))
            loaded = true
        }
        var settings = settingsStore.current()
        val snapshot = probe.read(now)
        val sample = snapshot.toSample()
        if (sample != null) {
            val last = memory.lastOrNull()
            if (last == null || now - last.epochMillis >= SAMPLE_GAP_MS) {
                memory += sample
                pending += sample
            }
        }
        memory.removeAll { now - it.epochMillis > MEMORY_WINDOW_MS }
        settings = rememberFullCharge(settings, snapshot, now)
        flushPending(now)

        val identity = deviceIdentity()
        val profile = DeviceProfiles.resolve(settings.profileChoice, identity)
        val assessment = CapacityMath.assess(
            CapacityInput(
                gaugeFullUah = snapshot.gaugeFullUah,
                gaugeDesignUah = snapshot.gaugeDesignUah,
                gaugeSohPercent = snapshot.gaugeSohPercent,
                frameworkSohPercent = snapshot.frameworkSohPercent,
                remainingUah = snapshot.remainingUah,
                levelPercent = snapshot.levelPercent,
                status = snapshot.status,
                currentMicroamps = snapshot.currentMicroamps,
                cycleCount = snapshot.cycleCount,
                profileTypicalMah = profile?.typicalMah,
                profileAmbiguous = profile?.ambiguous == true,
                baseline = settings.baseline,
                customDesignMah = settings.customDesignMah,
                learnedFullUah = settings.learnedFullUah,
            ),
        )
        rememberCapacity(now, assessment)
        return buildState(now, settings, snapshot, profile, assessment, identity)
    }

    private suspend fun rememberFullCharge(settings: UserSettings, snapshot: ProbeSnapshot, now: Long): UserSettings {
        val candidate = CapacityMath.learnCandidate(
            CapacityInput(
                gaugeFullUah = snapshot.gaugeFullUah,
                gaugeDesignUah = snapshot.gaugeDesignUah,
                gaugeSohPercent = snapshot.gaugeSohPercent,
                frameworkSohPercent = snapshot.frameworkSohPercent,
                remainingUah = snapshot.remainingUah,
                levelPercent = snapshot.levelPercent,
                status = snapshot.status,
                currentMicroamps = snapshot.currentMicroamps,
                cycleCount = snapshot.cycleCount,
                profileTypicalMah = null,
                profileAmbiguous = false,
                baseline = settings.baseline,
                customDesignMah = settings.customDesignMah,
                learnedFullUah = settings.learnedFullUah,
            ),
        ) ?: return settings
        if (now < learnPausedUntil) return settings
        if (now - settings.learnedAtMillis < LEARN_GAP_MS) return settings
        val updated = settings.copy(
            learnedFullUah = (settings.learnedFullUah + candidate).takeLast(30),
            learnedAtMillis = now,
        )
        settingsStore.replace(updated)
        return updated
    }

    private fun flushPending(now: Long, force: Boolean = false) {
        val first = pending.firstOrNull() ?: return
        if (!force && now - first.epochMillis < FLUSH_MS && pending.size < 12) return
        historyStore.appendSamples(pending.toList(), now)
        pending.clear()
    }

    private fun rememberCapacity(now: Long, assessment: CapacityAssessment) {
        val full = assessment.fullMah ?: return
        if (assessment.fullSourceCode == CapacityMath.SOURCE_NONE || full <= 0.0) return
        historyStore.upsertCapacity(
            CapacityPoint(now, full, assessment.fullSourceCode),
            now - CAPACITY_KEEP_MS,
        )
    }

    private fun buildState(
        now: Long,
        settings: UserSettings,
        snapshot: ProbeSnapshot,
        profile: DeviceProfile?,
        assessment: CapacityAssessment,
        identity: String,
    ): MirrorUiState {
        val fullUah = assessment.fullMah?.times(1000.0)?.toLong()
        val dayStart = startOfDay(now)
        val today = UsageMath.summarize(memory.filter { it.epochMillis >= dayStart }, fullUah)
        val levelSeries = downsample(memory.filter { now - it.epochMillis <= CHART_WINDOW_MS })
        val capacitySeries = historyStore.loadCapacity(now - CAPACITY_KEEP_MS)
            .map { ChartPoint(it.epochMillis, it.fullMah.toFloat()) }
        val dischargeRate = UsageMath.recentAverageMilliamp(memory, now, charging = false)
        val chargeRate = UsageMath.recentAverageMilliamp(memory, now, charging = true)
        val remainingMah = snapshot.remainingUah?.div(1000.0)
            ?: assessment.fullMah?.let { full ->
                snapshot.levelPercent?.let { level -> full * level / 100.0 }
            }
        val fullMah = assessment.fullMah
        val missingMah = if (fullMah != null && remainingMah != null) {
            (fullMah - remainingMah).coerceAtLeast(0.0)
        } else {
            null
        }
        val currentMa = snapshot.currentMicroamps?.div(1000.0)
        val powerMw = if (currentMa != null && snapshot.voltageMv != null) {
            abs(currentMa) * snapshot.voltageMv / 1000.0
        } else {
            null
        }
        val aios = SystemBuildParser.aiosVersion(Build.DISPLAY, Build.VERSION.INCREMENTAL, Build.FINGERPRINT)
        val release = Build.VERSION.RELEASE.ifBlank { snapshotSdk() }
        val headline = if (aios != null) "${SystemBuildParser.aiosLabel(aios)} · Android $release" else "Android $release"
        val modelLine = listOfNotNull(Build.MODEL, profile?.name).joinToString(" · ")
        val ignoring = isIgnoringOptimizations()
        return MirrorUiState(
            loading = false,
            error = if (snapshot.levelPercent == null && snapshot.voltageMv == null) {
                "没有读到电池。请在真机上打开，模拟器通常没有电量计。"
            } else {
                null
            },
            capturedAt = now,
            levelPercent = snapshot.levelPercent,
            status = snapshot.status,
            statusLabel = snapshot.statusLabel,
            pluggedLabel = snapshot.pluggedLabel,
            healthFlagLabel = snapshot.healthFlagLabel,
            technology = snapshot.technology,
            temperatureC = snapshot.temperatureC,
            voltageMv = snapshot.voltageMv,
            currentMa = currentMa,
            powerMw = powerMw,
            dischargeEtaMinutes = if (snapshot.status == ChargeStatus.DISCHARGING || snapshot.status == ChargeStatus.NOT_CHARGING) {
                UsageMath.etaMinutes(remainingMah, dischargeRate)
            } else {
                null
            },
            chargeEtaMinutes = if (snapshot.status == ChargeStatus.CHARGING) {
                UsageMath.etaMinutes(missingMah, chargeRate)
            } else {
                null
            },
            assessment = assessment,
            today = today,
            levelSeries = levelSeries,
            capacitySeries = capacitySeries,
            diagnostics = snapshot.diagnostics + listOf(
                DiagnosticLine("型号", Build.MODEL.orEmpty()),
                DiagnosticLine("版本号", Build.DISPLAY.orEmpty()),
                DiagnosticLine("识别串", identity.take(120)),
            ),
            settings = settings,
            profile = profile,
            systemHeadline = headline,
            modelLine = modelLine,
            displayLine = Build.DISPLAY.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            notes = buildNotes(settings, profile, snapshot, aios, ignoring),
            monitorRunning = settings.monitorEnabled,
            currentCorrected = snapshot.currentCorrected,
            ignoringOptimizations = ignoring,
            sampleCount = memory.size,
        )
    }

    private fun buildNotes(
        settings: UserSettings,
        profile: DeviceProfile?,
        snapshot: ProbeSnapshot,
        aios: String?,
        ignoring: Boolean,
    ): List<String> {
        val notes = mutableListOf<String>()
        if (profile?.ambiguous == true && settings.profileChoice == ProfileChoice.AUTO) notes += profile.note
        if (
            profile == null &&
            settings.profileChoice == ProfileChoice.AUTO &&
            snapshot.gaugeDesignUah == null &&
            snapshot.gaugeFullUah == null &&
            snapshot.frameworkSohPercent == null
        ) {
            notes += "这台手机的电量计没有给出容量。在设置里手填标称毫安时后，才能判断有没有缩水。"
        }
        if (aios == null && profile != null) notes += "版本号里没有星云 AIOS。若容量不对，请在设置里指定机型或手填。"
        if (snapshot.currentCorrected) notes += "电流方向和充放电状态对不上，已按充电为正、放电为负改过来。"
        if (!settings.monitorEnabled) {
            notes += "现在只在这个应用打开时记录。要看一整天，到设置里打开持续记录。"
        } else if (!ignoring) {
            notes += "持续记录开着。息屏后系统仍可能把采样间隔拉长。"
        }
        return notes
    }

    private fun downsample(samples: List<UsageSample>): List<ChartPoint> {
        if (samples.isEmpty()) return emptyList()
        if (samples.size <= 240) {
            return samples.map { ChartPoint(it.epochMillis, it.levelPercent.toFloat()) }
        }
        val bucket = (samples.size + 239) / 240
        return samples.chunked(bucket) { chunk ->
            val mid = chunk[chunk.size / 2]
            ChartPoint(mid.epochMillis, chunk.map { it.levelPercent }.average().toFloat())
        }
    }

    private fun startOfDay(now: Long): Long {
        val zone = ZoneId.systemDefault()
        return Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    }

    private fun deviceIdentity(): String = listOf(
        Build.MANUFACTURER,
        Build.BRAND,
        Build.MODEL,
        Build.DEVICE,
        Build.DISPLAY,
        Build.VERSION.INCREMENTAL,
        Build.VERSION.RELEASE,
    ).joinToString(" ")

    private fun snapshotSdk(): String = Build.VERSION.SDK_INT.toString()

    private fun isIgnoringOptimizations(): Boolean {
        val manager = context.getSystemService(PowerManager::class.java) ?: return false
        return manager.isIgnoringBatteryOptimizations(context.packageName)
    }

    private companion object {
        const val SAMPLE_GAP_MS = 5_000L
        const val FLUSH_MS = 30_000L
        const val MEMORY_WINDOW_MS = 26L * 60 * 60 * 1000
        const val CHART_WINDOW_MS = 24L * 60 * 60 * 1000
        const val CAPACITY_KEEP_MS = 180L * 24 * 60 * 60 * 1000
        const val LEARN_GAP_MS = 12L * 60 * 60 * 1000
    }
}
