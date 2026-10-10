package dev.batterymirror.core

/**
 * 【数据模型】容量健康和用电量计算用的输入输出。
 * 不依赖 Android，方便把「缩了多少毫安时」单独测准。
 */

enum class ChargeStatus {
    UNKNOWN,
    CHARGING,
    DISCHARGING,
    FULL,
    NOT_CHARGING,
}

/** 满充容量拿什么当比较基准。 */
enum class BaselineChoice {
    AUTO,
    PROFILE,
    CUSTOM,
}

/** 自动识别，或强制指定 Z70 / Z70S。 */
enum class ProfileChoice {
    AUTO,
    Z70_ULTRA,
    Z70S_ULTRA,
    GENERIC,
}

enum class HealthGrade {
    LIKE_NEW,
    SLIGHT,
    AGING,
    OBVIOUS,
    SEVERE,
    UNKNOWN,
}

/** 直接回答「有没有缩减」。 */
enum class Verdict {
    UNKNOWN,
    NO_SHRINK,
    SLIGHT_SHRINK,
    SHRUNK,
}

enum class Confidence {
    HIGH,
    MEDIUM,
    LOW,
    NONE,
}

data class DeviceProfile(
    val id: String,
    val name: String,
    val typicalMah: Int,
    val wiredChargeWatts: Int,
    val ambiguous: Boolean,
    val note: String,
)

/**
 * 一次容量判断需要的原料。
 * 电量单位除了百分比，进算法之前都换成微安时（µAh）。
 */
data class CapacityInput(
    val gaugeFullUah: Long?,
    val gaugeDesignUah: Long?,
    val gaugeSohPercent: Double?,
    /** Android 16 的 BATTERY_PROPERTY_STATE_OF_HEALTH，已经是保持率百分比。 */
    val frameworkSohPercent: Double?,
    val remainingUah: Long?,
    val levelPercent: Int?,
    val status: ChargeStatus,
    val currentMicroamps: Int?,
    val cycleCount: Int?,
    val profileTypicalMah: Int?,
    val profileAmbiguous: Boolean,
    val baseline: BaselineChoice,
    val customDesignMah: Int?,
    val learnedFullUah: List<Long>,
)

data class CapacityAssessment(
    val verdict: Verdict,
    val verdictTitle: String,
    val headline: String,
    val retentionPercent: Double?,
    val lostMah: Double?,
    val fullMah: Double?,
    val designMah: Double?,
    val designSourceLabel: String,
    val fullSourceLabel: String,
    val fullSourceCode: String,
    val marketingTypicalMah: Int?,
    val reportedSohPercent: Double?,
    val reportedSohLabel: String?,
    val grade: HealthGrade,
    val gradeLabel: String,
    val confidence: Confidence,
    val confidenceLabel: String,
    val detail: String,
    val cycleCount: Int?,
)

data class UsageSample(
    val epochMillis: Long,
    val levelPercent: Int,
    val currentMicroamps: Int?,
    val voltageMv: Int?,
    val temperatureTenthsC: Int?,
    val remainingUah: Long?,
    val status: ChargeStatus,
    val screenInteractive: Boolean,
)

data class CapacityPoint(
    val epochMillis: Long,
    val fullMah: Double,
    val sourceCode: String,
)

data class UsageSummary(
    val dischargedMah: Double,
    val chargedMah: Double,
    val integratedMillis: Long,
    val avgDischargeMa: Double?,
    val screenOnAvgMa: Double?,
    val screenOffAvgMa: Double?,
    val usedLevelFallback: Boolean,
    /** 正数表示电量在下降，单位是每小时的百分点数。 */
    val netLevelDropPerHour: Double?,
) {
    companion object {
        val EMPTY = UsageSummary(
            dischargedMah = 0.0,
            chargedMah = 0.0,
            integratedMillis = 0L,
            avgDischargeMa = null,
            screenOnAvgMa = null,
            screenOffAvgMa = null,
            usedLevelFallback = false,
            netLevelDropPerHour = null,
        )
    }
}

data class UserSettings(
    val baseline: BaselineChoice = BaselineChoice.AUTO,
    val profileChoice: ProfileChoice = ProfileChoice.AUTO,
    val customDesignMah: Int? = null,
    val monitorEnabled: Boolean = false,
    val learnedFullUah: List<Long> = emptyList(),
    /** 【学习记录】上一次记下充满容量的时间。12 小时内不重复记，避免一次充满塞进很多条。 */
    val learnedAtMillis: Long = 0L,
)
