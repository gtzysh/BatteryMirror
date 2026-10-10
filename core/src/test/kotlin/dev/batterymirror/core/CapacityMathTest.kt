package dev.batterymirror.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapacityMathTest {
    @Test
    fun chargeFullMatchingDesignIsNotShrink() {
        val result = CapacityMath.assess(base(gaugeFullUah = 6_150_000, gaugeDesignUah = 6_150_000))
        assertEquals(Verdict.NO_SHRINK, result.verdict)
        assertEquals(100.0, result.retentionPercent!!, 0.05)
        assertEquals(0.0, result.lostMah!!, 0.05)
        assertEquals(CapacityMath.SOURCE_CHARGE_FULL, result.fullSourceCode)
        assertEquals(Confidence.HIGH, result.confidence)
        assertEquals(HealthGrade.LIKE_NEW, result.grade)
    }

    @Test
    fun twoPercentBoundaryUsesNinetyEight() {
        val stillNew = CapacityMath.assess(base(gaugeFullUah = 6_027_000, profileTypicalMah = 6150, baseline = BaselineChoice.PROFILE))
        assertEquals(Verdict.NO_SHRINK, stillNew.verdict)
        assertEquals(98.0, stillNew.retentionPercent!!, 0.05)
        val slight = CapacityMath.assess(base(gaugeFullUah = 6_026_000, profileTypicalMah = 6150, baseline = BaselineChoice.PROFILE))
        assertEquals(Verdict.SLIGHT_SHRINK, slight.verdict)
        assertTrue(slight.retentionPercent!! < 98.0)
    }

    @Test
    fun lowerRetentionBecomesShrunkAndDoesNotGrowPastBaseline() {
        val aging = CapacityMath.assess(base(gaugeFullUah = 5_400_000, profileTypicalMah = 6150, baseline = BaselineChoice.PROFILE))
        assertEquals(Verdict.SHRUNK, aging.verdict)
        assertEquals(HealthGrade.AGING, aging.grade)
        assertEquals(750.0, aging.lostMah!!, 0.5)
        val severe = CapacityMath.assess(base(gaugeFullUah = 4_200_000, profileTypicalMah = 6150, baseline = BaselineChoice.PROFILE))
        assertEquals(HealthGrade.SEVERE, severe.grade)
        val above = CapacityMath.assess(base(gaugeFullUah = 6_200_000, gaugeDesignUah = 6_150_000))
        assertEquals(Verdict.NO_SHRINK, above.verdict)
        assertEquals(100.0, above.retentionPercent!!, 0.01)
        assertEquals(0.0, above.lostMah!!, 0.01)
        assertTrue(above.headline.contains("不低于"))
    }

    @Test
    fun unknownPhoneUsesGaugeOrSohInsteadOfZ70() {
        val fromGauge = CapacityMath.assess(base(gaugeFullUah = 4_500_000, gaugeDesignUah = 5_000_000))
        assertEquals(5000.0, fromGauge.designMah!!, 0.1)
        assertEquals("电量计设计容量", fromGauge.designSourceLabel)
        assertEquals(90.0, fromGauge.retentionPercent!!, 0.1)
        val derived = CapacityMath.assess(base(gaugeFullUah = 5_904_000, frameworkSohPercent = 96.0))
        assertEquals(6150.0, derived.designMah!!, 1.0)
        assertEquals("按健康度反推的额定容量", derived.designSourceLabel)
        assertEquals(Verdict.SLIGHT_SHRINK, derived.verdict)
        val directWins = CapacityMath.assess(
            base(gaugeFullUah = 5_904_000, gaugeDesignUah = 6_000_000, frameworkSohPercent = 96.0),
        )
        assertEquals(6000.0, directWins.designMah!!, 0.1)
        assertEquals("电量计设计容量", directWins.designSourceLabel)
    }

    @Test
    fun frameworkSohBeatsNothingAndIsNotZeroWhenMissing() {
        val fromSoh = CapacityMath.assess(
            base(frameworkSohPercent = 96.0, profileTypicalMah = 6150, baseline = BaselineChoice.PROFILE),
        )
        assertEquals(CapacityMath.SOURCE_FRAMEWORK_SOH, fromSoh.fullSourceCode)
        assertEquals(96.0, fromSoh.retentionPercent!!, 0.05)
        assertEquals(5904.0, fromSoh.fullMah!!, 1.0)
        assertEquals(96.0, fromSoh.reportedSohPercent!!, 0.01)
        val missing = CapacityMath.assess(base(frameworkSohPercent = null, gaugeSohPercent = 0.0))
        assertEquals(Verdict.UNKNOWN, missing.verdict)
        assertNull(missing.retentionPercent)
        assertNull(missing.reportedSohPercent)
        val gaugeOnly = CapacityMath.assess(
            base(gaugeSohPercent = 95.0, profileTypicalMah = 6150, baseline = BaselineChoice.PROFILE),
        )
        assertEquals(CapacityMath.SOURCE_GAUGE_SOH, gaugeOnly.fullSourceCode)
        assertEquals(Confidence.MEDIUM, gaugeOnly.confidence)
    }

    @Test
    fun chargeFullWinsOverSohAndLearnedMedianNeedsThree() {
        val preferred = CapacityMath.assess(
            base(
                gaugeFullUah = 6_000_000,
                frameworkSohPercent = 50.0,
                gaugeDesignUah = 6_150_000,
                learnedFullUah = listOf(5_000_000, 5_100_000, 5_200_000),
            ),
        )
        assertEquals(CapacityMath.SOURCE_CHARGE_FULL, preferred.fullSourceCode)
        assertEquals(6000.0, preferred.fullMah!!, 0.1)
        val learned = CapacityMath.assess(
            base(
                gaugeDesignUah = 6_150_000,
                learnedFullUah = listOf(6_000_000, 5_900_000, 6_100_000),
            ),
        )
        assertEquals(CapacityMath.SOURCE_LEARNED, learned.fullSourceCode)
        assertEquals(6000.0, learned.fullMah!!, 0.1)
        assertEquals(Confidence.MEDIUM, learned.confidence)
        val one = CapacityMath.assess(base(gaugeDesignUah = 6_150_000, learnedFullUah = listOf(6_000_000)))
        assertEquals(Confidence.LOW, one.confidence)
    }

    @Test
    fun learnOnlyWhenSettledAndAmbiguousProfileDoesNotAssume6150() {
        val settled = base(levelPercent = 100, remainingUah = 6_000_000, status = ChargeStatus.FULL)
        assertEquals(6_000_000L, CapacityMath.learnCandidate(settled))
        val trickle = base(levelPercent = 99, remainingUah = 5_940_000, currentMicroamps = 20_000, status = ChargeStatus.CHARGING)
        assertEquals(6_000_000L, CapacityMath.learnCandidate(trickle))
        val fast = base(levelPercent = 99, remainingUah = 5_940_000, currentMicroamps = 2_000_000, status = ChargeStatus.CHARGING)
        assertNull(CapacityMath.learnCandidate(fast))
        val inferred = CapacityMath.assess(settled.copy(gaugeDesignUah = 6_150_000))
        assertEquals(CapacityMath.SOURCE_INFERRED, inferred.fullSourceCode)
        val ambiguous = CapacityMath.assess(
            base(profileTypicalMah = 6150, profileAmbiguous = true, baseline = BaselineChoice.AUTO),
        )
        assertEquals(Verdict.UNKNOWN, ambiguous.verdict)
        assertNull(ambiguous.designMah)
        val custom = CapacityMath.assess(
            base(
                gaugeFullUah = 6_000_000,
                gaugeDesignUah = 6_150_000,
                profileAmbiguous = true,
                baseline = BaselineChoice.CUSTOM,
                customDesignMah = 6600,
            ),
        )
        assertEquals(6600.0, custom.designMah!!, 0.1)
        assertEquals("手填", custom.designSourceLabel)
        val rejected = CapacityMath.assess(base(baseline = BaselineChoice.CUSTOM, customDesignMah = 100))
        assertNull(rejected.designMah)
    }

    private fun base(
        gaugeFullUah: Long? = null,
        gaugeDesignUah: Long? = null,
        gaugeSohPercent: Double? = null,
        frameworkSohPercent: Double? = null,
        remainingUah: Long? = null,
        levelPercent: Int? = null,
        status: ChargeStatus = ChargeStatus.DISCHARGING,
        currentMicroamps: Int? = null,
        cycleCount: Int? = null,
        profileTypicalMah: Int? = null,
        profileAmbiguous: Boolean = false,
        baseline: BaselineChoice = BaselineChoice.AUTO,
        customDesignMah: Int? = null,
        learnedFullUah: List<Long> = emptyList(),
    ) = CapacityInput(
        gaugeFullUah = gaugeFullUah,
        gaugeDesignUah = gaugeDesignUah,
        gaugeSohPercent = gaugeSohPercent,
        frameworkSohPercent = frameworkSohPercent,
        remainingUah = remainingUah,
        levelPercent = levelPercent,
        status = status,
        currentMicroamps = currentMicroamps,
        cycleCount = cycleCount,
        profileTypicalMah = profileTypicalMah,
        profileAmbiguous = profileAmbiguous,
        baseline = baseline,
        customDesignMah = customDesignMah,
        learnedFullUah = learnedFullUah,
    )
}
