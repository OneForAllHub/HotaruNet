package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.RulesetItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HotaruV2345UpgradeTest {

    @Test
    fun defaultRoutingKeepsOnlyLanDirectAndStripsAutoIranOrChinaRules() {
        val legacyRules = listOf(
            RulesetItem(remarks = "阻断udp443", outboundTag = AppConfig.TAG_BLOCKED, port = "443", network = "udp"),
            RulesetItem(remarks = "Bypass Iran domains", outboundTag = AppConfig.TAG_DIRECT, domain = listOf("domain:ir", "geosite:category-ir")),
            RulesetItem(remarks = "Bypass Iran IP", outboundTag = AppConfig.TAG_DIRECT, ip = listOf("geoip:ir")),
            RulesetItem(remarks = "绕过中国IP", outboundTag = AppConfig.TAG_DIRECT, ip = listOf("geoip:cn")),
        )

        val cleaned = SettingsManager.sanitizeDefaultRoutingRules(legacyRules)
        assertEquals(2, cleaned.size)
        assertTrue(cleaned.any { it.ip?.contains(AppConfig.GEOIP_PRIVATE) == true })
        assertTrue(cleaned.any { it.domain?.contains(AppConfig.GEOSITE_PRIVATE) == true })
        assertFalse(cleaned.any { it.domain?.contains("domain:ir") == true })
        assertFalse(cleaned.any { it.ip?.contains("geoip:ir") == true })
    }

    @Test
    fun apkUpdaterRestrictsDownloadDomainsStrictlyToHttpsGitHub() {
        assertTrue(
            HotaruApkUpdater.isPermittedDownloadUrl(
                "https://github.com/OneForAllHub/HotaruNet/releases/download/v2.3.45/HotaruNet_2.3.45_arm64-v8a.apk"
            )
        )
        assertTrue(
            HotaruApkUpdater.isPermittedDownloadUrl(
                "https://objects.githubusercontent.com/github-production-release-asset/HotaruNet_2.3.45_x86_64.apk"
            )
        )
        assertFalse(
            HotaruApkUpdater.isPermittedDownloadUrl(
                "http://github.com/OneForAllHub/HotaruNet/releases/download/v2.3.45/HotaruNet.apk"
            )
        )
        assertFalse(
            HotaruApkUpdater.isPermittedDownloadUrl(
                "https://evil-github.com/HotaruNet_2.3.45_arm64-v8a.apk"
            )
        )
    }
}
