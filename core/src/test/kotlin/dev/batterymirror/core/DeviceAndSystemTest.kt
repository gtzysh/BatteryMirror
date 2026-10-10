package dev.batterymirror.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceAndSystemTest {
    @Test
    fun domesticNx733OnNebulaAiosIsZ70Ultra() {
        val identity = "nubia NX733J NebulaAIOS2.0.13MR6_NX733J 16"
        val profile = DeviceProfiles.match(identity)
        assertEquals("z70_ultra", profile?.id)
        assertEquals(6150, profile?.typicalMah)
        assertFalse(profile!!.ambiguous)
        assertEquals("2.0.13", SystemBuildParser.aiosVersion("NebulaAIOS2.0.13MR6_NX733J"))
        assertEquals("星云 AIOS 2.0.13", SystemBuildParser.aiosLabel("2.0.13"))
    }

    @Test
    fun bareNx733StaysAmbiguousAndZ70sIs6600() {
        assertTrue(DeviceProfiles.match("NX733J")!!.ambiguous)
        assertEquals(6150, DeviceProfiles.match("NX733J")!!.typicalMah)
        assertEquals("z70s_ultra", DeviceProfiles.match("NX737J")?.id)
        assertEquals(6600, DeviceProfiles.match("nubia Z70S Ultra NX733J")?.typicalMah)
        assertEquals("z70_ultra", DeviceProfiles.match("NX736J")?.id)
        assertNull(DeviceProfiles.match("Pixel 8"))
        assertNull(DeviceProfiles.resolve(ProfileChoice.GENERIC, "NX733J NebulaAIOS2.0.13MR6_NX733J"))
        assertEquals(6600, DeviceProfiles.resolve(ProfileChoice.Z70S_ULTRA, "NX733J")?.typicalMah)
    }
}
