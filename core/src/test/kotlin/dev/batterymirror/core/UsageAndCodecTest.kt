package dev.batterymirror.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageAndCodecTest {
    @Test
    fun shortGapIntegratesCurrentAndLongGapUsesRemaining() {
        val hourSlice = UsageMath.summarize(
            listOf(
                sample(0, current = -3_600_000),
                sample(10_000, current = -3_600_000),
            ),
            fullUah = null,
        )
        assertEquals(10.0, hourSlice.dischargedMah, 0.05)
        assertEquals(0.0, hourSlice.chargedMah, 0.05)
        assertFalse(hourSlice.usedLevelFallback)

        val exactlyThreeMinutes = UsageMath.summarize(
            listOf(sample(0, current = -1_000_000), sample(UsageMath.SHORT_GAP_MS, current = -1_000_000)),
            fullUah = null,
        )
        assertEquals(50.0, exactlyThreeMinutes.dischargedMah, 0.05)

        val charged = UsageMath.summarize(
            listOf(sample(0, current = 1_000_000), sample(36_000, current = 1_000_000)),
            fullUah = null,
        )
        assertEquals(0.0, charged.dischargedMah, 0.05)
        assertEquals(10.0, charged.chargedMah, 0.05)

        val remainingGap = UsageMath.summarize(
            listOf(
                sample(0, current = -2_000_000, remaining = 5_000_000),
                sample(UsageMath.SHORT_GAP_MS + 1, current = -2_000_000, remaining = 4_990_000),
            ),
            fullUah = null,
        )
        assertEquals(10.0, remainingGap.dischargedMah, 0.05)
    }

    @Test
    fun longHoleIsDroppedAndLevelIsFallback() {
        val dropped = UsageMath.summarize(
            listOf(
                sample(0, remaining = 5_000_000),
                sample(UsageMath.MAX_GAP_MS + 1, remaining = 1_000_000),
            ),
            fullUah = null,
        )
        assertEquals(0.0, dropped.dischargedMah, 0.01)
        assertEquals(0L, dropped.integratedMillis)

        val sixHours = UsageMath.summarize(
            listOf(sample(0, remaining = 5_000_000), sample(UsageMath.MAX_GAP_MS, remaining = 4_000_000)),
            fullUah = null,
        )
        assertEquals(1000.0, sixHours.dischargedMah, 0.1)

        val byLevel = UsageMath.summarize(
            listOf(sample(0, level = 80), sample(60_000, level = 70)),
            fullUah = 6_150_000,
        )
        assertEquals(615.0, byLevel.dischargedMah, 0.1)
        assertTrue(byLevel.usedLevelFallback)
        assertEquals(600.0, byLevel.netLevelDropPerHour!!, 0.1)
    }

    @Test
    fun screenAveragesIgnoreChangedScreenAndEtaNeedsTwoMinutes() {
        val summary = UsageMath.summarize(
            listOf(
                sample(0, current = -500_000, screen = true),
                sample(60_000, current = -500_000, screen = true),
                sample(120_000, current = -200_000, screen = false),
                sample(180_000, current = -200_000, screen = false),
                sample(240_000, current = -900_000, screen = true),
            ),
            fullUah = null,
        )
        assertEquals(500.0, summary.screenOnAvgMa!!, 1.0)
        assertEquals(200.0, summary.screenOffAvgMa!!, 1.0)

        val samples = listOf(
            sample(0, current = -500_000),
            sample(60_000, current = -500_000),
            sample(120_000, current = -500_000),
        )
        assertEquals(500.0, UsageMath.recentAverageMilliamp(samples, 120_000, charging = false)!!, 1.0)
        assertNull(UsageMath.recentAverageMilliamp(samples.take(2), 60_000, charging = false))
        assertEquals(60.0, UsageMath.etaMinutes(500.0, 500.0)!!, 0.1)
        assertNull(UsageMath.etaMinutes(500.0, null))
    }

    @Test
    fun codecRoundTripsSamplesAndCapacity() {
        val sample = UsageSample(
            epochMillis = 1_700_000_000_000,
            levelPercent = 87,
            currentMicroamps = -480_000,
            voltageMv = 4120,
            temperatureTenthsC = 312,
            remainingUah = 5_200_000,
            status = ChargeStatus.DISCHARGING,
            screenInteractive = true,
        )
        assertEquals(sample, SampleCodec.decode(SampleCodec.encode(sample)))
        val point = CapacityPoint(1_700_000_000_000, 5904.0, CapacityMath.SOURCE_FRAMEWORK_SOH)
        assertEquals(point, SampleCodec.decodeCapacity(SampleCodec.encodeCapacity(point)))
        assertNull(SampleCodec.decode("not-json"))
        assertNull(SampleCodec.decodeCapacity("{\"t\":1}"))
    }

    private fun sample(
        time: Long,
        level: Int = 80,
        current: Int? = null,
        remaining: Long? = null,
        screen: Boolean = false,
    ) = UsageSample(
        epochMillis = time,
        levelPercent = level,
        currentMicroamps = current,
        voltageMv = 4000,
        temperatureTenthsC = 300,
        remainingUah = remaining,
        status = if ((current ?: 0) > 0) ChargeStatus.CHARGING else ChargeStatus.DISCHARGING,
        screenInteractive = screen,
    )
}
