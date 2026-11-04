package com.shiyu.chat.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceCompatibilityTest {
    @Test
    fun mapsPublishedMeinaMixPackagesWithoutInventingAn8Gen3Package() {
        assertEquals("min", meinaPackageSuffixForSoc("SM8350", true, "V68"))
        assertEquals("8gen1", meinaPackageSuffixForSoc("SM8450", true, "V69"))
        assertEquals("8gen2", meinaPackageSuffixForSoc("SM8550", true, "V73"))
        assertEquals("8gen2", meinaPackageSuffixForSoc("SM8650", true, "V75"))
        assertEquals("8gen2", meinaPackageSuffixForSoc("SM8750", true, "V79"))
    }

    @Test
    fun unknownOrNonQualcommDevicesDoNotReceiveAnNpuPackage() {
        assertNull(meinaPackageSuffixForSoc("MT6989", false, null))
        assertNull(meinaPackageSuffixForSoc("GOOGLE-TENSOR", false, null))
        assertNull(meinaPackageSuffixForSoc("UNKNOWN", true, null))
    }

    @Test
    fun mapsKnownHexagonArchitectures() {
        assertEquals("V68", htpForQualcommSoc("SM8350"))
        assertEquals("V69", htpForQualcommSoc("sm8475"))
        assertEquals("V73", htpForQualcommSoc("QCS8550"))
        assertEquals("V75", htpForQualcommSoc("SM8650"))
        assertEquals("V79", htpForQualcommSoc("SM8750"))
        assertEquals("V81", htpForQualcommSoc("SM8850"))
    }
}
