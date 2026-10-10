package dev.batterymirror.core

/**
 * 【用电量】把两次采样收成毫安时。
 * 间隔不超过 3 分钟且两端都有电流时做电流积分；更长的间隔改用剩余容量差值；再没有就用电量百分比乘满充容量。
 * 超过 6 小时的空洞丢掉，避免把一瞬间的电流当成一直没变。
 */
object UsageMath {
    const val SHORT_GAP_MS = 3L * 60 * 1000
    const val MAX_GAP_MS = 6L * 60 * 60 * 1000
    const val RATE_WINDOW_MS = 15L * 60 * 1000
    const val RATE_MIN_MS = 2L * 60 * 1000

    fun summarize(samples: List<UsageSample>, fullUah: Long?): UsageSummary {
        val sorted = samples.sortedBy { it.epochMillis }
        if (sorted.size < 2) return UsageSummary.EMPTY
        var discharged = 0.0
        var charged = 0.0
        var integrated = 0L
        var usedLevel = false
        var screenOnMah = 0.0
        var screenOnMillis = 0L
        var screenOffMah = 0.0
        var screenOffMillis = 0L
        for (index in 0 until sorted.lastIndex) {
            val start = sorted[index]
            val end = sorted[index + 1]
            val dt = end.epochMillis - start.epochMillis
            if (dt <= 0L || dt > MAX_GAP_MS) continue
            val mah = segmentMah(start, end, dt, fullUah) ?: continue
            integrated += dt
            if (mah >= 0.0) charged += mah else discharged += -mah
            // 【亮屏息屏】只用短间隔里的真实电流，电量百分比估算不进平均值。
            val currentBacked = dt <= SHORT_GAP_MS && start.currentMicroamps != null && end.currentMicroamps != null
            if (mah < 0.0 && currentBacked && start.screenInteractive == end.screenInteractive) {
                if (start.screenInteractive) {
                    screenOnMah += -mah
                    screenOnMillis += dt
                } else {
                    screenOffMah += -mah
                    screenOffMillis += dt
                }
            }
            if (usedLevelFallback(start, end, dt)) usedLevel = true
        }
        val span = sorted.last().epochMillis - sorted.first().epochMillis
        val netDrop = if (span >= 60_000L) {
            (sorted.first().levelPercent - sorted.last().levelPercent) * 3_600_000.0 / span
        } else {
            null
        }
        return UsageSummary(
            dischargedMah = discharged,
            chargedMah = charged,
            integratedMillis = integrated,
            avgDischargeMa = averageMilliamp(discharged, integrated),
            screenOnAvgMa = averageMilliamp(screenOnMah, screenOnMillis),
            screenOffAvgMa = averageMilliamp(screenOffMah, screenOffMillis),
            usedLevelFallback = usedLevel,
            netLevelDropPerHour = netDrop,
        )
    }

    /**
     * 最近 15 分钟里，间隔短、方向一致的电流平均值。
     * charging 为 true 时只统计充入，否则只统计放出。覆盖不足 2 分钟就返回 null。
     */
    fun recentAverageMilliamp(samples: List<UsageSample>, nowMillis: Long, charging: Boolean): Double? {
        val windowStart = nowMillis - RATE_WINDOW_MS
        val sorted = samples
            .filter { it.epochMillis in windowStart..nowMillis }
            .sortedBy { it.epochMillis }
        if (sorted.size < 2) return null
        var mah = 0.0
        var covered = 0L
        for (index in 0 until sorted.lastIndex) {
            val start = sorted[index]
            val end = sorted[index + 1]
            val dt = end.epochMillis - start.epochMillis
            val startUa = start.currentMicroamps
            val endUa = end.currentMicroamps
            if (dt <= 0L || dt > SHORT_GAP_MS || startUa == null || endUa == null) continue
            val avgUa = (startUa + endUa) / 2.0
            val segment = avgUa * dt / 3_600_000_000.0
            val matches = if (charging) segment > 0.0 else segment < 0.0
            if (!matches) continue
            mah += if (segment > 0.0) segment else -segment
            covered += dt
        }
        if (covered < RATE_MIN_MS) return null
        return averageMilliamp(mah, covered)
    }

    /** 剩余毫安时除以毫安，得到分钟。电流太小或算不出来时返回 null。 */
    fun etaMinutes(capacityMah: Double?, rateMilliamp: Double?): Double? {
        if (capacityMah == null || rateMilliamp == null || rateMilliamp <= 1.0) return null
        if (capacityMah <= 0.0) return 0.0
        val minutes = capacityMah / rateMilliamp * 60.0
        if (minutes.isNaN() || minutes.isInfinite()) return null
        return minutes
    }

    private fun segmentMah(start: UsageSample, end: UsageSample, dt: Long, fullUah: Long?): Double? {
        val startUa = start.currentMicroamps
        val endUa = end.currentMicroamps
        if (dt <= SHORT_GAP_MS && startUa != null && endUa != null) {
            val avgUa = (startUa + endUa) / 2.0
            return avgUa * dt / 3_600_000_000.0
        }
        val startRemaining = start.remainingUah
        val endRemaining = end.remainingUah
        if (startRemaining != null && endRemaining != null) {
            return (endRemaining - startRemaining) / 1000.0
        }
        if (fullUah != null && fullUah > 0L) {
            val deltaPercent = end.levelPercent - start.levelPercent
            return deltaPercent / 100.0 * (fullUah / 1000.0)
        }
        return null
    }

    private fun usedLevelFallback(start: UsageSample, end: UsageSample, dt: Long): Boolean {
        if (dt <= 0L || dt > MAX_GAP_MS) return false
        val hasCurrent = dt <= SHORT_GAP_MS && start.currentMicroamps != null && end.currentMicroamps != null
        if (hasCurrent) return false
        if (start.remainingUah != null && end.remainingUah != null) return false
        return true
    }

    private fun averageMilliamp(mah: Double, millis: Long): Double? {
        if (millis <= 0L || mah <= 0.0) return null
        val hours = millis / 3_600_000.0
        if (hours <= 0.0) return null
        return mah / hours
    }
}
