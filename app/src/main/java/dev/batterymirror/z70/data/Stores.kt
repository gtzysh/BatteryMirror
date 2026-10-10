package dev.batterymirror.z70.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.batterymirror.core.BaselineChoice
import dev.batterymirror.core.CapacityPoint
import dev.batterymirror.core.ProfileChoice
import dev.batterymirror.core.SampleCodec
import dev.batterymirror.core.UsageSample
import dev.batterymirror.core.UserSettings
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

private val Context.mirrorSettings by preferencesDataStore(name = "battery_mirror_settings")

/**
 * 【设置】比较基准、机型和持续记录开关只存在本机。
 * 学到的满充容量也放在这里，不上传。
 */
class SettingsStore(private val context: Context) {
    suspend fun current(): UserSettings {
        val prefs = context.mirrorSettings.data.first()
        return read(prefs)
    }

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        context.mirrorSettings.edit { prefs ->
            write(prefs, transform(read(prefs)))
        }
    }

    suspend fun replace(settings: UserSettings) {
        context.mirrorSettings.edit { prefs -> write(prefs, settings) }
    }

    private fun read(prefs: Preferences): UserSettings {
        val baseline = prefs[KEY_BASELINE]?.let { runCatching { BaselineChoice.valueOf(it) }.getOrNull() }
            ?: BaselineChoice.AUTO
        val profile = prefs[KEY_PROFILE]?.let { runCatching { ProfileChoice.valueOf(it) }.getOrNull() }
            ?: ProfileChoice.AUTO
        val learned = prefs[KEY_LEARNED]
            .orEmpty()
            .split(',')
            .mapNotNull { it.toLongOrNull() }
        return UserSettings(
            baseline = baseline,
            profileChoice = profile,
            customDesignMah = prefs[KEY_CUSTOM],
            monitorEnabled = prefs[KEY_MONITOR] == true,
            learnedFullUah = learned,
            learnedAtMillis = prefs[KEY_LEARNED_AT] ?: 0L,
        )
    }

    private fun write(prefs: MutablePreferences, settings: UserSettings) {
        prefs[KEY_BASELINE] = settings.baseline.name
        prefs[KEY_PROFILE] = settings.profileChoice.name
        prefs[KEY_MONITOR] = settings.monitorEnabled
        prefs[KEY_LEARNED] = settings.learnedFullUah.joinToString(",")
        prefs[KEY_LEARNED_AT] = settings.learnedAtMillis
        val custom = settings.customDesignMah
        if (custom == null) prefs.remove(KEY_CUSTOM) else prefs[KEY_CUSTOM] = custom
    }

    private companion object {
        val KEY_BASELINE = stringPreferencesKey("baseline")
        val KEY_PROFILE = stringPreferencesKey("profile")
        val KEY_CUSTOM = intPreferencesKey("custom_mah")
        val KEY_MONITOR = booleanPreferencesKey("monitor")
        val KEY_LEARNED = stringPreferencesKey("learned_uah")
        val KEY_LEARNED_AT = longPreferencesKey("learned_at")
    }
}

/**
 * 【历史】样本保留 7 天，容量趋势按天保留 180 天。
 * 文件变大以后，26 小时内的点保留原样，更早的点收成大约 30 秒一条。
 */
class HistoryStore(context: Context) {
    private val dir = File(context.filesDir, "mirror")
    private val samplesFile = File(dir, "samples.jsonl")
    private val capacityFile = File(dir, "capacity.jsonl")
    private val lock = Any()

    fun loadSamples(sinceMillis: Long): List<UsageSample> = synchronized(lock) {
        readSamples().filter { it.epochMillis >= sinceMillis }.sortedBy { it.epochMillis }
    }

    fun appendSamples(samples: List<UsageSample>, nowMillis: Long) = synchronized(lock) {
        if (samples.isEmpty()) return
        dir.mkdirs()
        val text = samples.joinToString(separator = "\n") { SampleCodec.encode(it) } + "\n"
        samplesFile.appendText(text, Charsets.UTF_8)
        if (samplesFile.length() > 8_000_000L) rewriteSamples(nowMillis)
    }

    fun loadCapacity(sinceMillis: Long): List<CapacityPoint> = synchronized(lock) {
        readCapacity().filter { it.epochMillis >= sinceMillis }.sortedBy { it.epochMillis }
    }

    /** 同一天只留一条。来源更差的新读数不覆盖电量计或系统健康度。 */
    fun upsertCapacity(point: CapacityPoint, keepAfter: Long) = synchronized(lock) {
        dir.mkdirs()
        val existing = readCapacity().filter { it.epochMillis >= keepAfter }
        val day = dayOf(point.epochMillis)
        val previous = existing.find { dayOf(it.epochMillis) == day }
        if (previous != null && rank(point.sourceCode) > rank(previous.sourceCode)) return
        if (
            previous != null &&
            rank(point.sourceCode) == rank(previous.sourceCode) &&
            abs(previous.fullMah - point.fullMah) <= point.fullMah * 0.005
        ) {
            return
        }
        val merged = existing.filter { dayOf(it.epochMillis) != day } + point
        writeCapacity(merged.sortedBy { it.epochMillis })
    }

    fun clear() = synchronized(lock) {
        samplesFile.delete()
        capacityFile.delete()
    }

    private fun readSamples(): List<UsageSample> {
        if (!samplesFile.isFile) return emptyList()
        return samplesFile.readLines(Charsets.UTF_8).mapNotNull { SampleCodec.decode(it) }
    }

    private fun rewriteSamples(nowMillis: Long) {
        val keepAfter = nowMillis - SAMPLE_KEEP_MS
        val recentAfter = nowMillis - RECENT_MS
        val sorted = readSamples().filter { it.epochMillis >= keepAfter }.sortedBy { it.epochMillis }
        val kept = mutableListOf<UsageSample>()
        var lastOld = Long.MIN_VALUE
        for (sample in sorted) {
            if (sample.epochMillis >= recentAfter) {
                kept += sample
            } else if (sample.epochMillis - lastOld >= 30_000L) {
                kept += sample
                lastOld = sample.epochMillis
            }
        }
        samplesFile.writeText(
            if (kept.isEmpty()) "" else kept.joinToString(separator = "\n") { SampleCodec.encode(it) } + "\n",
            Charsets.UTF_8,
        )
    }

    private fun readCapacity(): List<CapacityPoint> {
        if (!capacityFile.isFile) return emptyList()
        return capacityFile.readLines(Charsets.UTF_8).mapNotNull { SampleCodec.decodeCapacity(it) }
    }

    private fun writeCapacity(points: List<CapacityPoint>) {
        capacityFile.writeText(
            if (points.isEmpty()) "" else points.joinToString(separator = "\n") { SampleCodec.encodeCapacity(it) } + "\n",
            Charsets.UTF_8,
        )
    }

    private fun dayOf(epochMillis: Long): String {
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
    }

    private fun rank(code: String): Int = when (code) {
        "charge_full" -> 0
        "framework_soh" -> 1
        "gauge_soh" -> 2
        "learned" -> 3
        "inferred" -> 4
        else -> 9
    }

    private companion object {
        const val SAMPLE_KEEP_MS = 7L * 24 * 60 * 60 * 1000
        const val RECENT_MS = 26L * 60 * 60 * 1000
    }
}
