package dev.batterymirror.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SysfsUnitsTest {
    @Test
    fun energyConvertsWithVoltageAndUevent() {
        assertEquals(6_150_000L, SysfsUnits.energyToUah(23_677_500L, 3850))
        assertEquals(6_149_870L, SysfsUnits.energyToUah(23_677L, 3850))
        assertNull(SysfsUnits.energyToUah(100L, 3850))
        val uevent = "POWER_SUPPLY_CHARGE_FULL_DESIGN=6150000\nPOWER_SUPPLY_TEMP=300\n"
        assertEquals(6_150_000L, SysfsUnits.ueventLong(uevent, "POWER_SUPPLY_CHARGE_FULL_DESIGN"))
        assertNull(SysfsUnits.ueventLong(uevent, "POWER_SUPPLY_CHARGE_FULL"))
    }

    @Test
    fun chargeFullAcceptsMilliampAndMicroamp() {
        assertEquals(6_150_000L, SysfsUnits.chargeFullToUah(6150))
        assertEquals(6_150_000L, SysfsUnits.chargeFullToUah(6_150_000))
        assertNull(SysfsUnits.chargeFullToUah(500))
    }

    @Test
    fun remainingKeepsMicroampHourCounter() {
        assertEquals(500_000L, SysfsUnits.chargeRemainingToUah(500_000))
        assertEquals(80_000L, SysfsUnits.chargeRemainingToUah(80_000))
        assertEquals(6_150_000L, SysfsUnits.chargeRemainingToUah(6150))
    }

    @Test
    fun flipsCurrentOnlyWhenStatusContradicts() {
        assertEquals(-500_000, SysfsUnits.normalizeCurrent(500_000, ChargeStatus.DISCHARGING))
        assertEquals(2_000_000, SysfsUnits.normalizeCurrent(-2_000_000, ChargeStatus.CHARGING))
        assertEquals(20_000, SysfsUnits.normalizeCurrent(20_000, ChargeStatus.DISCHARGING))
        assertEquals(-480_000, SysfsUnits.normalizeCurrent(-480_000, ChargeStatus.DISCHARGING))
    }

    @Test
    fun interpretsSohTemperatureAndVoltage() {
        assertEquals(95.0, SysfsUnits.interpretSoh(95)!!, 0.01)
        assertEquals(95.0, SysfsUnits.interpretSoh(950)!!, 0.01)
        assertEquals(95.0, SysfsUnits.interpretSoh(9500)!!, 0.01)
        assertNull(SysfsUnits.interpretSoh(0))
        assertEquals(31.2, SysfsUnits.temperatureC(312)!!, 0.01)
        assertEquals(31.2, SysfsUnits.temperatureC(31200)!!, 0.01)
        assertEquals(4200, SysfsUnits.voltageMvFromMicrovolts(4_200_000))
        assertEquals(4200, SysfsUnits.voltageMvFromMicrovolts(4200))
    }
}
