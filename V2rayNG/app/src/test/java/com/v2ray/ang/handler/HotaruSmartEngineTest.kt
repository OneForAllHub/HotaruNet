package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HotaruSmartEngineTest {

    @Test
    fun `network profiles separate operator and network without storing identifiers`() {
        val profile = HotaruSmartEngine.networkProfileKey("43211", "123")
        assertEquals(profile, HotaruSmartEngine.networkProfileKey("43211", "123"))
        assertTrue(profile != HotaruSmartEngine.networkProfileKey("43235", "123"))
        assertTrue(profile != HotaruSmartEngine.networkProfileKey("43211", "456"))
        assertFalse(profile.contains("43211"))
        assertEquals("", HotaruSmartEngine.networkProfileKey("43211", ""))
    }

    @Test
    fun `configIdentity generates deterministic SHA-256 fingerprint`() {
        val p1 = ProfileItem(configType = EConfigType.VLESS, server = "1.2.3.4", serverPort = "443", remarks = "Node 1")
        val p2 = ProfileItem(configType = EConfigType.VLESS, server = "1.2.3.4", serverPort = "443", remarks = "Node 1")
        val p3 = ProfileItem(configType = EConfigType.VLESS, server = "5.6.7.8", serverPort = "443", remarks = "Node 2")

        val id1 = HotaruSmartEngine.configIdentity(p1)
        val id2 = HotaruSmartEngine.configIdentity(p2)
        val id3 = HotaruSmartEngine.configIdentity(p3)

        assertNotNull(id1)
        assertEquals(64, id1.length)
        assertEquals(id1, id2)
        assertTrue(id1 != id3)
    }

    @Test
    fun `network kind MTU and timeouts are tuned for high-throughput streaming`() {
        assertEquals(1380, HotaruSmartEngine.NetworkKind.WIFI.mtu)
        assertEquals(1380, HotaruSmartEngine.NetworkKind.ETHERNET.mtu)
        assertEquals(1360, HotaruSmartEngine.NetworkKind.CELLULAR.mtu)
        assertEquals("300,60", HotaruSmartEngine.NetworkKind.WIFI.hevTimeout)
        assertEquals("180,45", HotaruSmartEngine.NetworkKind.CELLULAR.hevTimeout)
    }

    @Test
    fun `extractPrebenchmarkedKilobytesPerSecond handles multi-megabit units accurately`() {
        assertEquals(1024L, HotaruSmartEngine.extractPrebenchmarkedKilobytesPerSecond("HotaruNet #001 | 1.0 MiB/s"))
        assertEquals(2000L, HotaruSmartEngine.extractPrebenchmarkedKilobytesPerSecond("HotaruNet #002 | 2.0 MB/s"))
        assertEquals(512L, HotaruSmartEngine.extractPrebenchmarkedKilobytesPerSecond("HotaruNet #003 | 500 KiB/s"))
        assertEquals(250L, HotaruSmartEngine.extractPrebenchmarkedKilobytesPerSecond("HotaruNet #004 | 250 KB/s"))
        assertEquals(0L, HotaruSmartEngine.extractPrebenchmarkedKilobytesPerSecond("HotaruNet #005 | No Speed"))
    }

    @Test
    fun `candidate scoring is geographically neutral across all countries`() {
        val euProfile = ProfileItem(configType = EConfigType.VLESS, server = "198.51.100.1", serverPort = "443", remarks = "HotaruNet | 🇩🇪 Germany | REALITY 1500KB/s")
        val usProfile = ProfileItem(configType = EConfigType.VLESS, server = "198.51.100.2", serverPort = "443", remarks = "HotaruNet | 🇺🇸 United States | REALITY 1500KB/s")
        val sgProfile = ProfileItem(configType = EConfigType.VLESS, server = "198.51.100.3", serverPort = "443", remarks = "HotaruNet | 🇸🇬 Singapore | REALITY 1500KB/s")

        val euScore = HotaruSmartEngine.computeCandidatePotentialScore(
            profile = euProfile,
            prebenchKbps = 1500L,
            tier = HotaruSmartEngine.TransportTier.REALITY_VISION,
            port = 443,
            isFragile = false,
            recentNetBps = 2_000_000L,
            recentNetLatencyMs = 90L,
        )
        val usScore = HotaruSmartEngine.computeCandidatePotentialScore(
            profile = usProfile,
            prebenchKbps = 1500L,
            tier = HotaruSmartEngine.TransportTier.REALITY_VISION,
            port = 443,
            isFragile = false,
            recentNetBps = 2_000_000L,
            recentNetLatencyMs = 90L,
        )
        val sgScore = HotaruSmartEngine.computeCandidatePotentialScore(
            profile = sgProfile,
            prebenchKbps = 1500L,
            tier = HotaruSmartEngine.TransportTier.REALITY_VISION,
            port = 443,
            isFragile = false,
            recentNetBps = 2_000_000L,
            recentNetLatencyMs = 90L,
        )
        assertEquals(euScore, usScore)
        assertEquals(euScore, sgScore)
    }

    @Test
    fun `scoreVerifiedCandidate favors high download speed over low ping`() {
        val slowLowPingProfile = ProfileItem(configType = EConfigType.VLESS, server = "198.51.100.11", serverPort = "443", remarks = "🇩🇪 LowPing 700KB/s")
        val fastHighPingProfile = ProfileItem(configType = EConfigType.VLESS, server = "198.51.100.12", serverPort = "443", remarks = "🇺🇸 HighSpeed 2000KB/s")
        val candSlowLowPing = HotaruSmartEngine.CandidatePotential("eu", slowLowPingProfile, 700L, false, HotaruSmartEngine.TransportTier.REALITY_VISION, 443, "198.51.100.0/24", 1000L)
        val candFastHighPing = HotaruSmartEngine.CandidatePotential("us", fastHighPingProfile, 2000L, false, HotaruSmartEngine.TransportTier.REALITY_VISION, 443, "198.51.100.0/24", 1000L)

        // 700 KB/s at 80ms = 56 KB in 80ms; 2000 KB/s at 400ms = 800 KB in 400ms (or same 32KB asset downloaded faster in 16ms vs 46ms, using equal payload delay/bytes check)
        val slowLowPingScore = HotaruSmartEngine.scoreVerifiedCandidate(candSlowLowPing, delay = 47L, bytes = 32_768) // ~697 KB/s, 47ms
        // Actually use realistic speeds: 700 KB/s over 32KB takes ~46ms; simulate 700 KB/s vs 2000 KB/s directly with 262KB/512KB payload or delay comparison:
        val fastRemote = HotaruSmartEngine.scoreVerifiedCandidate(candFastHighPing, delay = 200L, bytes = 400_000) // 2,000,000 B/s at 200ms
        val slowLocal = HotaruSmartEngine.scoreVerifiedCandidate(candSlowLowPing, delay = 50L, bytes = 35_000) // 700,000 B/s at 50ms
        assertTrue(fastRemote.finalScore > slowLocal.finalScore)
    }

    @Test
    fun `classifyTransportTier maps protocols and shields correctly`() {
        val directDpi = ProfileItem(configType = EConfigType.CUSTOM_OUTBOUND, remarks = "HotaruNet #000 | ⚡ Direct DPI Shield | Zapret Zero-Hop")
        assertEquals(HotaruSmartEngine.TransportTier.DIRECT_DPI_SHIELD, HotaruSmartEngine.classifyTransportTier(directDpi))

        val warp = ProfileItem(configType = EConfigType.WIREGUARD, remarks = "HotaruNet #000 | 🇪🇺 Cloudflare WARP | Dedicated Turbo (WireGuard)")
        assertEquals(HotaruSmartEngine.TransportTier.WARP_DEDICATED, HotaruSmartEngine.classifyTransportTier(warp))

        val reality = ProfileItem(configType = EConfigType.VLESS, security = AppConfig.REALITY, flow = "xtls-rprx-vision")
        assertEquals(HotaruSmartEngine.TransportTier.REALITY_VISION, HotaruSmartEngine.classifyTransportTier(reality))

        val hy2 = ProfileItem(configType = EConfigType.HYSTERIA2)
        assertEquals(HotaruSmartEngine.TransportTier.HYSTERIA2, HotaruSmartEngine.classifyTransportTier(hy2))

        val xhttp = ProfileItem(configType = EConfigType.VLESS, network = "xhttp", security = AppConfig.TLS)
        assertEquals(HotaruSmartEngine.TransportTier.XHTTP, HotaruSmartEngine.classifyTransportTier(xhttp))

        val xhttpReality = ProfileItem(configType = EConfigType.VLESS, network = "xhttp", security = AppConfig.REALITY)
        assertEquals(HotaruSmartEngine.TransportTier.XHTTP, HotaruSmartEngine.classifyTransportTier(xhttpReality))

        val grpcReality = ProfileItem(configType = EConfigType.VLESS, network = "grpc", security = AppConfig.REALITY)
        assertEquals(HotaruSmartEngine.TransportTier.GRPC, HotaruSmartEngine.classifyTransportTier(grpcReality))
    }

    @Test
    fun `extractHostAndRemarksForHarvest parses both vmess base64 JSON and standard URIs`() {
        val vmessJson = """{"v":"2","ps":"HotaruNet #015 | 🇩🇪 Germany | WS+TLS 1200KB/s","add":"172.66.0.1","port":"443","id":"60441548-b68e-43b2-8191-e3b884be4b3c","net":"ws","tls":"tls"}"""
        val vmessUri = "vmess://" + java.util.Base64.getEncoder().encodeToString(vmessJson.toByteArray(Charsets.UTF_8))
        val (vmessHost, vmessRemarks) = HotaruSmartEngine.extractHostAndRemarksForHarvest(vmessUri)
        assertEquals("172.66.0.1", vmessHost)
        assertEquals("HotaruNet #015 | 🇩🇪 Germany | WS+TLS 1200KB/s", vmessRemarks)

        val vlessUri = "vless://2bdff1df-8659-4318-b6a8-cafda82b594d@198.51.100.42:443?type=tcp&security=reality#HotaruNet%20%23001%20%7C%20REALITY%202400KB/s"
        val (vlessHost, vlessRemarks) = HotaruSmartEngine.extractHostAndRemarksForHarvest(vlessUri)
        assertEquals("198.51.100.42", vlessHost)
        assertEquals("HotaruNet #001 | REALITY 2400KB/s", vlessRemarks)
    }

    @Test
    fun `isCleanHarvestUri rejects scanner server IP and bogus URIs`() {
        assertFalse(HotaruSmartEngine.isCleanHarvestUri("vless://uuid@198.51.100.42:443?type=tcp&security=reality"))
        assertFalse(HotaruSmartEngine.isCleanHarvestUri("vless://uuid@31.56.7.99:443?type=tcp&security=reality"))
        val selfVmessJson = """{"v":"2","ps":"self","add":"198.51.100.42","port":"443","id":"00000000-0000-0000-0000-000000000000"}"""
        val selfVmessUri = "vmess://" + java.util.Base64.getEncoder().encodeToString(selfVmessJson.toByteArray(Charsets.UTF_8))
        assertFalse(HotaruSmartEngine.isCleanHarvestUri(selfVmessUri))
        assertFalse(HotaruSmartEngine.isCleanHarvestUri("vless://uuid@127.0.0.1:443?type=tcp&security=reality"))
        assertFalse(HotaruSmartEngine.isCleanHarvestUri("trojan://pwd@[2607:9d00:2000:29c::9051:bd97]:2097?security=reality"))
        assertTrue(HotaruSmartEngine.isCleanHarvestUri("vless://uuid@198.51.100.42:443?type=tcp&security=reality"))
    }

    @Test
    fun `computeCandidatePotentialScore adapts to user network kind and empirical bandwidth`() {
        val profile = ProfileItem(configType = EConfigType.VLESS, server = "198.51.100.42", serverPort = "443")
        val fastScore = HotaruSmartEngine.computeCandidatePotentialScore(
            profile = profile,
            prebenchKbps = 2500L,
            isEuro = true,
            tier = HotaruSmartEngine.TransportTier.REALITY_VISION,
            port = 443,
            isFragile = false,
            networkKind = HotaruSmartEngine.NetworkKind.CELLULAR,
            downstreamKbps = 12000,
            recentNetBps = 1_800_000L,
            recentNetLatencyMs = 140L,
        )
        val slowScore = HotaruSmartEngine.computeCandidatePotentialScore(
            profile = profile,
            prebenchKbps = 120L,
            isEuro = true,
            tier = HotaruSmartEngine.TransportTier.MULTI_PROTO,
            port = 8443,
            isFragile = false,
            networkKind = HotaruSmartEngine.NetworkKind.CELLULAR,
            downstreamKbps = 12000,
            recentNetBps = 0L,
        )
        val throttledOnUserIspScore = HotaruSmartEngine.computeCandidatePotentialScore(
            profile = profile,
            prebenchKbps = 2500L,
            isEuro = true,
            tier = HotaruSmartEngine.TransportTier.REALITY_VISION,
            port = 443,
            isFragile = false,
            networkKind = HotaruSmartEngine.NetworkKind.CELLULAR,
            downstreamKbps = 12000,
            recentNetBps = 15_000L, // Throttled (< 64 KB/s) on user's ISP despite 2500 KB/s remark
        )
        val unprobedSameRemarkScore = HotaruSmartEngine.computeCandidatePotentialScore(
            profile = profile,
            prebenchKbps = 2500L,
            isEuro = true,
            tier = HotaruSmartEngine.TransportTier.REALITY_VISION,
            port = 443,
            isFragile = false,
            networkKind = HotaruSmartEngine.NetworkKind.CELLULAR,
            downstreamKbps = 12000,
            recentNetBps = 0L,
        )
        assertTrue(fastScore > slowScore)
        assertTrue(fastScore > unprobedSameRemarkScore)
        assertTrue(throttledOnUserIspScore < unprobedSameRemarkScore)
    }

    @Test
    fun `scoreVerifiedCandidate prioritizes real payload download over 204-only fallback regardless of remark`() {
        val p1 = ProfileItem(configType = EConfigType.VLESS, server = "198.51.100.10", serverPort = "443", remarks = "Real Payload 400KB/s")
        val p2 = ProfileItem(configType = EConfigType.VLESS, server = "198.51.100.20", serverPort = "443", remarks = "204 Only 3000KB/s")
        val candPayload = HotaruSmartEngine.CandidatePotential("g1", p1, 400L, false, HotaruSmartEngine.TransportTier.REALITY_VISION, 443, "198.51.100.0/24", 1000L)
        val cand204Only = HotaruSmartEngine.CandidatePotential("g2", p2, 3000L, false, HotaruSmartEngine.TransportTier.REALITY_VISION, 443, "198.51.100.0/24", 1000L)

        val verifiedPayload = HotaruSmartEngine.scoreVerifiedCandidate(candPayload, delay = 240L, bytes = 32768)
        val verified204Only = HotaruSmartEngine.scoreVerifiedCandidate(cand204Only, delay = 180L, bytes = 0)

        assertTrue(verifiedPayload.finalScore > verified204Only.finalScore)
    }

    @Test
    fun `computeAdaptiveTierBonus boosts fast user-network tiers and penalizes failing tiers`() {
        val now = 1_000_000_000L
        val fastStat = HotaruSmartEngine.TierNetworkStat(samples = 3, ewmaKbps = 1200L, failStreak = 0, updatedAt = now)
        val failingStat = HotaruSmartEngine.TierNetworkStat(samples = 3, ewmaKbps = 0L, failStreak = 3, updatedAt = now)
        val staleStat = HotaruSmartEngine.TierNetworkStat(samples = 3, ewmaKbps = 1200L, failStreak = 0, updatedAt = now - 48 * 60 * 60_000L)

        assertTrue(HotaruSmartEngine.computeAdaptiveTierBonus(fastStat, now) > 0L)
        assertTrue(HotaruSmartEngine.computeAdaptiveTierBonus(failingStat, now) < 0L)
        assertEquals(0L, HotaruSmartEngine.computeAdaptiveTierBonus(staleStat, now))
        assertEquals(0L, HotaruSmartEngine.computeAdaptiveTierBonus(null, now))
    }

    @Test
    fun `warp endpoints include non-standard clean Anycast ports`() {
        val endpoints = HotaruWarpManager.CLEAN_WARP_ENDPOINTS
        assertTrue(endpoints.isNotEmpty())
        val ports = endpoints.map { it.second }.toSet()
        // Must include key Iranian anti-censorship ports (500, 854, 894, 908, 1074, 1194, 2408, 4500)
        assertTrue(ports.contains(500))
        assertTrue(ports.contains(894))
        assertTrue(ports.contains(1074))
        assertTrue(ports.contains(4500))
    }

    @Test
    fun `normalizeHostOnly deduplicates IPv4 addresses into slash 24 subnets`() {
        val p1 = ProfileItem(configType = EConfigType.VLESS, server = "198.51.100.1")
        val p2 = ProfileItem(configType = EConfigType.VLESS, server = "198.51.100.254")
        val p3 = ProfileItem(configType = EConfigType.VLESS, server = "198.51.101.1")
        val domain = ProfileItem(configType = EConfigType.VLESS, server = "de.example.com")

        assertEquals("198.51.100.0/24", HotaruSmartEngine.normalizeHostOnly(p1))
        assertEquals("198.51.100.0/24", HotaruSmartEngine.normalizeHostOnly(p2))
        assertEquals("198.51.101.0/24", HotaruSmartEngine.normalizeHostOnly(p3))
        assertEquals("de.example.com", HotaruSmartEngine.normalizeHostOnly(domain))
    }

    @Test
    fun `buildRemainingVerifiedQueue evicts failed and blacklisted candidates while placing next winner last`() {
        val raw = listOf("g1", "g2", "g3", "g4", "g5")
        val blacklisted = setOf("g2", "g4")
        val queue = HotaruSmartEngine.buildRemainingVerifiedQueue(
            rawVerified = raw,
            failedGuid = "g1",
            nextGuid = "g5",
            blacklisted = blacklisted,
        )
        assertEquals(listOf("g3", "g5"), queue)
    }
}
