package com.v2ray.ang.core

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HotaruHealthPolicyTest {
    @Test fun `rate uses decimal bytes per second and requires enough payload`() {
        assertEquals(500_000L, HotaruHealthPolicy.bytesPerSecond(1_500_000, 3000L))
        assertTrue(HotaruHealthPolicy.isFast(1_500_000, 3000L))
        assertFalse(HotaruHealthPolicy.isFast(1_500_000, 40_000L))
        assertFalse(HotaruHealthPolicy.isFast(4_096, 1L))
    }

    @Test fun `redirects error pages and tiny payloads do not count as bandwidth`() {
        assertTrue(HotaruHealthPolicy.isValidPayload(200, 262_144, "application/octet-stream", "binary"))
        assertTrue(HotaruHealthPolicy.isValidPayload(200, 262_144, "text/javascript; charset=utf-8", "/*! jquery */"))
        assertFalse(HotaruHealthPolicy.isValidPayload(302, 262_144, "application/octet-stream", "binary"))
        assertFalse(HotaruHealthPolicy.isValidPayload(200, 262_144, "text/html", "challenge"))
        assertFalse(HotaruHealthPolicy.isValidPayload(200, 262_144, "application/octet-stream", " <html>challenge"))
        assertFalse(HotaruHealthPolicy.isValidPayload(200, 262_144, "text/javascript", "unusual traffic"))
        assertFalse(HotaruHealthPolicy.isValidPayload(200, 4_096, "application/octet-stream", "binary"))
    }

    @Test fun `expired and future evidence cannot qualify a candidate`() {
        assertTrue(HotaruHealthPolicy.isFresh(1000L, 2000L, 1000L))
        assertFalse(HotaruHealthPolicy.isFresh(1000L, 2001L, 1000L))
        assertFalse(HotaruHealthPolicy.isFresh(2000L, 1000L))
        assertFalse(HotaruHealthPolicy.isFresh(0L, 1000L))
    }

    @Test fun `search restrictions are distinct from transport failures`() {
        assertTrue(HotaruHealthPolicy.isRestricted(200, null, "Our systems detected UNUSUAL TRAFFIC"))
        assertTrue(HotaruHealthPolicy.isRestricted(302, "https://www.google.com/sorry/index", ""))
        assertTrue(HotaruHealthPolicy.isRestricted(429, null, ""))
        assertFalse(HotaruHealthPolicy.isRestricted(-1, null, ""))
        assertFalse(HotaruHealthPolicy.isRestricted(200, null, "ordinary search results"))
        // Restrictions are not a failure or slow-throughput sample.
        assertFalse(HotaruHealthPolicy.shouldSwitch(0, 0, 100_000L, 0L))
    }

    @Test fun `failover needs repeated independent failures and respects cooldown`() {
        assertFalse(HotaruHealthPolicy.shouldSwitch(1, 0, 100_000L, 0L))
        assertFalse(HotaruHealthPolicy.shouldSwitch(0, 1, 100_000L, 0L))
        assertFalse(HotaruHealthPolicy.shouldSwitch(0, 2, 100_000L, 0L))
        assertTrue(HotaruHealthPolicy.shouldSwitch(3, 0, 100_000L, 0L))
        assertTrue(HotaruHealthPolicy.shouldSwitch(2, 6, 100_000L, 0L))
        assertTrue(HotaruHealthPolicy.shouldSwitch(0, 3, 100_000L, 0L))
        assertFalse(HotaruHealthPolicy.shouldSwitch(3, 2, 100_000L, 90_000L))
        assertTrue(HotaruHealthPolicy.shouldSwitch(3, 0, 170_000L, 90_000L))
    }

    @Test fun `slow but usable routes do not rotate indefinitely`() {
        assertTrue(HotaruHealthPolicy.mayExploreSlowAlternative(0))
        assertTrue(HotaruHealthPolicy.mayExploreSlowAlternative(2))
        assertFalse(HotaruHealthPolicy.mayExploreSlowAlternative(3))
        assertFalse(HotaruHealthPolicy.mayExploreSlowAlternative(100))
    }

    @Test fun `late results cannot affect a reloaded or stopped core`() {
        assertTrue(HotaruHealthPolicy.isCurrent("a", 1L, "a", 1L))
        assertFalse(HotaruHealthPolicy.isCurrent("a", 1L, "a", 2L))
        assertFalse(HotaruHealthPolicy.isCurrent("a", 1L, "b", 1L))
        assertFalse(HotaruHealthPolicy.isCurrent("a", 1L, null, 2L))
    }

    @Test fun `history keeps only fresh samples and de-duplicates by timestamp`() {
        val now = 1_000_000L
        val ttl = HotaruHealthPolicy.MEASUREMENT_TTL_MS
        val samples = listOf(
            HotaruHealthPolicy.ThroughputSample(now - ttl - 1, 300_000, 3000L), // stale
            HotaruHealthPolicy.ThroughputSample(now - 1000, 200_000, 2000L),
            HotaruHealthPolicy.ThroughputSample(now - 1000, 999_999, 9999L),   // duplicate timestamp
            HotaruHealthPolicy.ThroughputSample(now - 500, 300_000, 3000L),
        )
        val recent = HotaruHealthPolicy.recentSamples(samples, now)
        assertEquals(2, recent.size)
        assertEquals(now - 1000, recent[0].measuredAt)
        assertEquals(200_000, recent[0].bytes) // first occurrence of a timestamp wins
        assertEquals(now - 500, recent[1].measuredAt)
    }

    @Test fun `history is capped to the most recent max samples`() {
        val now = 1_000_000L
        val samples = (1..10).map {
            HotaruHealthPolicy.ThroughputSample(now - it * 1000L, 300_000, 3000L)
        }
        val recent = HotaruHealthPolicy.recentSamples(samples, now)
        assertEquals(HotaruHealthPolicy.MAX_HISTORY_SAMPLES, recent.size)
        assertEquals(now - 5000, recent.first().measuredAt) // 5th-newest kept after cap
        assertEquals(now - 1000, recent.last().measuredAt)  // newest kept
    }

    @Test fun `legacy single sample becomes provisional not confirmed`() {
        val now = 1_000_000L
        val history = HotaruHealthPolicy.sampleHistory(null, now - 100, 2_000_000, 4000L, now)
        assertEquals(1, history.size)
        val quality = HotaruHealthPolicy.throughputQuality(history, now)
        assertTrue(quality.latestFast)
        assertFalse(quality.confirmedFast) // one sample can never be confirmed-fast
        assertEquals(1, quality.sampleCount)
    }

    @Test fun `confirmed fast needs three samples two fast and a minimum span`() {
        val now = 1_000_000L
        // Three fast samples spanning >= 30s -> confirmed.
        val strong = listOf(
            HotaruHealthPolicy.ThroughputSample(now - 40_000, 1_500_000, 3000L),
            HotaruHealthPolicy.ThroughputSample(now - 20_000, 2_000_000, 4000L),
            HotaruHealthPolicy.ThroughputSample(now - 5_000, 2_500_000, 5000L),
        )
        val q = HotaruHealthPolicy.throughputQuality(strong, now)
        assertEquals(3, q.sampleCount)
        assertEquals(3, q.fastSamples)
        assertEquals(1000, q.reliabilityPermille)
        assertTrue(q.confirmedFast)
        assertEquals(0, HotaruHealthPolicy.qualityTier(q))

        // Three fast samples inside a short span -> not confirmed.
        val burst = listOf(
            HotaruHealthPolicy.ThroughputSample(now - 3_000, 1_500_000, 3000L),
            HotaruHealthPolicy.ThroughputSample(now - 2_000, 2_000_000, 4000L),
            HotaruHealthPolicy.ThroughputSample(now - 1_000, 2_500_000, 5000L),
        )
        val qb = HotaruHealthPolicy.throughputQuality(burst, now)
        assertFalse(qb.confirmedFast)
        assertEquals(1, HotaruHealthPolicy.qualityTier(qb)) // latestFast but not confirmed
    }

    @Test fun `a single fast spike does not outrank repeated fast samples`() {
        val now = 1_000_000L
        val oneSpike = HotaruHealthPolicy.throughputQuality(
            listOf(HotaruHealthPolicy.ThroughputSample(now - 100, 4_500_000, 1000L)), now,
        )
        val repeated = HotaruHealthPolicy.throughputQuality(
            listOf(
                HotaruHealthPolicy.ThroughputSample(now - 40_000, 1_500_000, 3000L),
                HotaruHealthPolicy.ThroughputSample(now - 20_000, 1_500_000, 3000L),
                HotaruHealthPolicy.ThroughputSample(now - 5_000, 1_500_000, 3000L),
            ), now,
        )
        // Confirmed/repeated route wins over a single spike regardless of peak speed.
        assertTrue(HotaruHealthPolicy.compareQuality(repeated, oneSpike) < 0)
    }

    @Test fun `ranking is quality first then conservative speed and deterministic`() {
        val now = 1_000_000L
        val faster = HotaruHealthPolicy.throughputQuality(
            listOf(
                HotaruHealthPolicy.ThroughputSample(now - 40_000, 1_500_000, 3000L),
                HotaruHealthPolicy.ThroughputSample(now - 20_000, 4_500_000, 3000L),
                HotaruHealthPolicy.ThroughputSample(now - 5_000, 4_500_000, 3000L),
            ), now,
        )
        val slower = HotaruHealthPolicy.throughputQuality(
            listOf(
                HotaruHealthPolicy.ThroughputSample(now - 40_000, 1_500_000, 3000L),
                HotaruHealthPolicy.ThroughputSample(now - 20_000, 1_500_000, 3000L),
                HotaruHealthPolicy.ThroughputSample(now - 5_000, 1_500_000, 3000L),
            ), now,
        )
        // Both confirmed-fast, so the higher conservative (median) sample wins.
        assertTrue(HotaruHealthPolicy.compareQuality(faster, slower) < 0)
        // Comparator is antisymmetric.
        assertTrue(HotaruHealthPolicy.compareQuality(slower, faster) > 0)
    }

    @Test fun `failure counts alone do not trigger a switch without cooldown`() {
        // Three failures with a recent switch inside cooldown stays put.
        assertFalse(HotaruHealthPolicy.shouldSwitch(3, 0, 100_000L, 90_000L))
        // After cooldown elapses it may switch.
        assertTrue(HotaruHealthPolicy.shouldSwitch(3, 0, 160_001L, 90_000L))
    }

    @Test fun `watchdog poll delay backs off when all failover candidates are unreachable`() {
        assertEquals(5_000L, HotaruHealthPolicy.watchdogPollDelayMs(failures = 0, consecutiveNullSwitches = 0))
        assertEquals(2_000L, HotaruHealthPolicy.watchdogPollDelayMs(failures = 1, consecutiveNullSwitches = 0))
        assertEquals(10_000L, HotaruHealthPolicy.watchdogPollDelayMs(failures = 3, consecutiveNullSwitches = 1))
        assertEquals(20_000L, HotaruHealthPolicy.watchdogPollDelayMs(failures = 3, consecutiveNullSwitches = 2))
        assertEquals(30_000L, HotaruHealthPolicy.watchdogPollDelayMs(failures = 3, consecutiveNullSwitches = 3))
        assertEquals(30_000L, HotaruHealthPolicy.watchdogPollDelayMs(failures = 3, consecutiveNullSwitches = 9))
    }
}
