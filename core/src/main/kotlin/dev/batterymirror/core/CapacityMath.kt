package dev.batterymirror.core

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 【缩水判断】用现在能充满的容量，对比用户选定的基准。
 * 顺序和阈值见 docs/design.md：先电量计满充，再系统健康度，再节点健康度，再历史中位数，最后才按当前电量反推。
 * 读数高于基准时保持率封顶 100%，不当成电池变大。
 */
object CapacityMath {
    const val NO_SHRINK_RETENTION = 98.0
    const val SLIGHT_RETENTION = 90.0

    /** 【静置】绝对值小于 50mA 才当成「电流很小」，避免快充时把剩余容量记成满充容量。 */
    private const val SETTLE_MICROAMPS = 50_000

    const val SOURCE_CHARGE_FULL = "charge_full"
    const val SOURCE_FRAMEWORK_SOH = "framework_soh"
    const val SOURCE_GAUGE_SOH = "gauge_soh"
    const val SOURCE_LEARNED = "learned"
    const val SOURCE_INFERRED = "inferred"
    const val SOURCE_NONE = "none"

    /**
     * 充满到 99% 以上，并且已经充满或电流很小，才用剩余容量反推一条满充容量。
     * 推不出来就返回 null，调用方不要写成 0。
     */
    fun learnCandidate(input: CapacityInput): Long? {
        val level = input.levelPercent ?: return null
        val remaining = input.remainingUah ?: return null
        if (level < 99) return null
        val current = input.currentMicroamps
        val settled = input.status == ChargeStatus.FULL ||
            (current != null && abs(current) < SETTLE_MICROAMPS)
        if (!settled) return null
        val full = remaining * 100L / level.toLong()
        return SysfsUnits.plausibleFullUah(full)
    }

    fun assess(input: CapacityInput): CapacityAssessment {
        val design = resolveDesign(input)
        val full = resolveFull(input, design)
        val reported = reportedSoh(input)
        val retention = when {
            full.mah != null && design != null && design.mah > 0.0 -> full.mah / design.mah * 100.0
            full.sohPercent != null -> full.sohPercent
            else -> null
        }
        val shownRetention = retention?.coerceAtMost(100.0)
        val lostMah = if (full.mah != null && design != null) {
            (design.mah - full.mah).coerceAtLeast(0.0)
        } else {
            null
        }
        val grade = gradeOf(shownRetention)
        val verdict = verdictOf(shownRetention)
        val aboveBaseline = full.mah != null && design != null && full.mah > design.mah + 0.5
        return CapacityAssessment(
            verdict = verdict,
            verdictTitle = verdictTitle(verdict),
            headline = headline(verdict, lostMah, aboveBaseline),
            retentionPercent = shownRetention,
            lostMah = lostMah,
            fullMah = full.mah,
            designMah = design?.mah,
            designSourceLabel = design?.label ?: "还没有基准",
            fullSourceLabel = full.label,
            fullSourceCode = full.code,
            marketingTypicalMah = input.profileTypicalMah,
            reportedSohPercent = reported.first,
            reportedSohLabel = reported.second,
            grade = grade,
            gradeLabel = gradeLabel(grade),
            confidence = if (shownRetention == null) Confidence.NONE else full.confidence,
            confidenceLabel = confidenceLabel(if (shownRetention == null) Confidence.NONE else full.confidence),
            detail = detail(input, design, full, aboveBaseline),
            cycleCount = input.cycleCount?.takeIf { it in 0..10_000 },
        )
    }

    private data class Design(val mah: Double, val label: String)

    private data class Full(
        val mah: Double?,
        val sohPercent: Double?,
        val code: String,
        val label: String,
        val confidence: Confidence,
    )

    private fun resolveDesign(input: CapacityInput): Design? {
        val gauge = input.gaugeDesignUah
            ?.let { SysfsUnits.plausibleFullUah(it) }
            ?.div(1000.0)
        // 【机型含糊】只看到 NX733J、又没有星云 AIOS 时，不拿 6150 当基准。
        val typical = if (input.profileAmbiguous) {
            null
        } else {
            input.profileTypicalMah?.takeIf { it in SysfsUnits.MIN_FULL_MAH..SysfsUnits.MAX_FULL_MAH }?.toDouble()
        }
        val custom = input.customDesignMah
            ?.takeIf { it in SysfsUnits.MIN_FULL_MAH..SysfsUnits.MAX_FULL_MAH }
            ?.toDouble()
        // 【任意机型】电量计没写设计容量时，用满充容量和健康度反推额定容量，不靠机型表。
        val fromSoh = derivedDesignMah(input)
        return when (input.baseline) {
            BaselineChoice.CUSTOM -> custom?.let { Design(it, "手填") }
            BaselineChoice.PROFILE -> typical?.let { Design(it, "官网典型值") }
            BaselineChoice.AUTO -> when {
                gauge != null -> Design(gauge, "电量计设计容量")
                fromSoh != null -> Design(fromSoh, "按健康度反推的额定容量")
                typical != null -> Design(typical, "官网典型值")
                custom != null -> Design(custom, "手填")
                else -> null
            }
        }
    }

    private fun derivedDesignMah(input: CapacityInput): Double? {
        val full = input.gaugeFullUah?.let { SysfsUnits.plausibleFullUah(it) } ?: return null
        val soh = listOfNotNull(input.frameworkSohPercent, input.gaugeSohPercent)
            .firstOrNull { it in 40.0..100.0 } ?: return null
        val design = (full / 1000.0) / (soh / 100.0)
        if (design < SysfsUnits.MIN_FULL_MAH || design > SysfsUnits.MAX_FULL_MAH) return null
        return design
    }

    private fun resolveFull(input: CapacityInput, design: Design?): Full {
        val gaugeFull = input.gaugeFullUah?.let { SysfsUnits.plausibleFullUah(it) }
        if (gaugeFull != null) {
            return Full(gaugeFull / 1000.0, null, SOURCE_CHARGE_FULL, "电量计满充容量", Confidence.HIGH)
        }
        sohFull(input.frameworkSohPercent, design, SOURCE_FRAMEWORK_SOH, "Android 16 系统健康度", Confidence.HIGH)
            ?.let { return it }
        sohFull(input.gaugeSohPercent, design, SOURCE_GAUGE_SOH, "电量计健康度", Confidence.MEDIUM)
            ?.let { return it }
        val learned = input.learnedFullUah.mapNotNull { SysfsUnits.plausibleFullUah(it) }
        if (learned.isNotEmpty()) {
            val confidence = if (learned.size >= 3) Confidence.MEDIUM else Confidence.LOW
            return Full(median(learned) / 1000.0, null, SOURCE_LEARNED, "充满时记下的容量", confidence)
        }
        val inferred = learnCandidate(input)
        if (inferred != null) {
            return Full(inferred / 1000.0, null, SOURCE_INFERRED, "按当前电量反推", Confidence.LOW)
        }
        return Full(null, null, SOURCE_NONE, "还没有读到", Confidence.NONE)
    }

    /** 健康度本身就是保持率。有基准时再换算成毫安时；没有基准也保留百分比。超出 40% 到 100% 的值丢掉。 */
    private fun sohFull(
        percent: Double?,
        design: Design?,
        code: String,
        label: String,
        confidence: Confidence,
    ): Full? {
        if (percent == null || percent !in 40.0..100.0) return null
        val mah = design?.let { it.mah * percent / 100.0 }
        return Full(mah, percent, code, label, confidence)
    }

    private fun reportedSoh(input: CapacityInput): Pair<Double?, String?> {
        val framework = input.frameworkSohPercent?.takeIf { it in 40.0..100.0 }
        if (framework != null) return framework to "Android 16 系统健康度"
        val gauge = input.gaugeSohPercent?.takeIf { it in 40.0..100.0 }
        if (gauge != null) return gauge to "电量计节点"
        return null to null
    }

    private fun median(values: List<Long>): Long {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    private fun gradeOf(retention: Double?): HealthGrade = when {
        retention == null -> HealthGrade.UNKNOWN
        retention >= NO_SHRINK_RETENTION -> HealthGrade.LIKE_NEW
        retention >= SLIGHT_RETENTION -> HealthGrade.SLIGHT
        retention >= 80.0 -> HealthGrade.AGING
        retention >= 70.0 -> HealthGrade.OBVIOUS
        else -> HealthGrade.SEVERE
    }

    private fun verdictOf(retention: Double?): Verdict = when {
        retention == null -> Verdict.UNKNOWN
        retention >= NO_SHRINK_RETENTION -> Verdict.NO_SHRINK
        retention >= SLIGHT_RETENTION -> Verdict.SLIGHT_SHRINK
        else -> Verdict.SHRUNK
    }

    private fun verdictTitle(verdict: Verdict): String = when (verdict) {
        Verdict.UNKNOWN -> "还判断不了"
        Verdict.NO_SHRINK -> "几乎没有缩减"
        Verdict.SLIGHT_SHRINK -> "轻微缩减"
        Verdict.SHRUNK -> "容量已经缩减"
    }

    private fun headline(verdict: Verdict, lostMah: Double?, aboveBaseline: Boolean): String = when (verdict) {
        Verdict.UNKNOWN -> "还判断不了满充容量有没有缩水"
        Verdict.NO_SHRINK -> if (aboveBaseline) "读数不低于比较基准，看不出缩减" else "几乎没有缩减"
        Verdict.SLIGHT_SHRINK -> lostMah?.let { "轻微缩减，大约少了 ${it.roundToInt()} mAh" } ?: "轻微缩减"
        Verdict.SHRUNK -> lostMah?.let { "容量已经缩减，大约少了 ${it.roundToInt()} mAh" } ?: "容量已经缩减"
    }

    private fun gradeLabel(grade: HealthGrade): String = when (grade) {
        HealthGrade.LIKE_NEW -> "接近新电池"
        HealthGrade.SLIGHT -> "轻微缩减"
        HealthGrade.AGING -> "已经缩减"
        HealthGrade.OBVIOUS -> "缩减明显"
        HealthGrade.SEVERE -> "缩减严重"
        HealthGrade.UNKNOWN -> "还判断不了"
    }

    private fun confidenceLabel(confidence: Confidence): String = when (confidence) {
        Confidence.HIGH -> "高"
        Confidence.MEDIUM -> "中"
        Confidence.LOW -> "低"
        Confidence.NONE -> "无"
    }

    private fun detail(input: CapacityInput, design: Design?, full: Full, aboveBaseline: Boolean): String {
        return buildString {
            append("满充容量来自")
            append(full.label)
            append("。")
            if (design != null) {
                append("比较基准是")
                append(design.label)
                append(" ")
                append(design.mah.roundToInt())
                append("mAh。")
            } else {
                append("还没有比较基准，可以在设置里指定 Z70、Z70S，或手填 1500 到 12000mAh。")
            }
            if (aboveBaseline) append("读数高于基准，不当成容量变多。")
            if (input.profileAmbiguous && input.baseline == BaselineChoice.AUTO) {
                append("型号还分不清是 6150 还是 6600，自动模式没有拿官网典型值当基准。")
            }
            append("和基准差在 2% 以内，先看成几乎没有缩减。")
            input.cycleCount?.takeIf { it in 0..10_000 }?.let { append("系统记了 $it 次循环。") }
        }
    }
}
