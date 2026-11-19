package com.v2ray.ang.handler

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HotaruBundledPoolTest {
    @Test fun `collector binary units and legacy decimal units remain scan hints`() {
        assertEquals(100L, HotaruSmartEngine.extractPrebenchmarkedKilobytesPerSecond("100 KB/s"))
        assertEquals(100L, HotaruSmartEngine.extractPrebenchmarkedKilobytesPerSecond("97.7KiB/s"))
        assertEquals(153L, HotaruSmartEngine.extractPrebenchmarkedKilobytesPerSecond("150 KiB/s"))
        assertEquals(0L, HotaruSmartEngine.extractPrebenchmarkedKilobytesPerSecond("100 Mbps"))
        assertEquals(0L, HotaruSmartEngine.extractPrebenchmarkedKilobytesPerSecond(null))
    }
    @Test fun `one valid fast server is not reimported on every launch`() {
        assertFalse(SettingsManager.needsHotaruPoolUpgrade(1, 5, 5))
        assertFalse(SettingsManager.needsHotaruPoolUpgrade(4, 5, 5))
    }
    @Test fun `node scoring treats all countries equally regardless of geographic remark`() {
        val de = ProfileItem(configType = EConfigType.VLESS, server = "198.51.100.1", serverPort = "443", remarks = "HotaruNet #001 | 🇩🇪 Germany")
        val us = ProfileItem(configType = EConfigType.VLESS, server = "198.51.100.2", serverPort = "443", remarks = "HotaruNet #010 | 🇺🇸 United States")
        val jp = ProfileItem(configType = EConfigType.VLESS, server = "198.51.100.3", serverPort = "443", remarks = "HotaruNet #014 | 🇯🇵 Japan")
        val deScore = HotaruSmartEngine.computeCandidatePotentialScore(de, 2000L, tier = HotaruSmartEngine.TransportTier.REALITY_VISION, port = 443, isFragile = false)
        val usScore = HotaruSmartEngine.computeCandidatePotentialScore(us, 2000L, tier = HotaruSmartEngine.TransportTier.REALITY_VISION, port = 443, isFragile = false)
        val jpScore = HotaruSmartEngine.computeCandidatePotentialScore(jp, 2000L, tier = HotaruSmartEngine.TransportTier.REALITY_VISION, port = 443, isFragile = false)
        assertEquals(deScore, usScore)
        assertEquals(deScore, jpScore)
    }
    @Test fun `empty owned group or older feed version requires import`() {
        assertTrue(SettingsManager.needsHotaruPoolUpgrade(0, 5, 5))
        assertTrue(SettingsManager.needsHotaruPoolUpgrade(40, 4, 5))
        assertFalse(SettingsManager.needsHotaruPoolUpgrade(40, 6, 5))
    }
}
