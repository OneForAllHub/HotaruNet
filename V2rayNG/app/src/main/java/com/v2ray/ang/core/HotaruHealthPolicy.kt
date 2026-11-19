package com.v2ray.ang.core

/** Pure decisions shared by the daemon probes and candidate ranking; rates are bytes/second. */
internal object HotaruHealthPolicy {
    const val FAST_BYTES_PER_SECOND = 64_000L
    const val MIN_PAYLOAD_BYTES = 8_192
    const val MEASUREMENT_TTL_MS = 5 * 60_000L
    const val THROUGHPUT_INTERVAL_MS = 35_000L
    const val SWITCH_COOLDOWN_MS = 30_000L
    const val DEAD_COOLDOWN_MS = 10_000L
    const val SLOW_COOLDOWN_MS = 20_000L
    const val SEARCH_INTERVAL_MS = 60 * 60_000L
    const val MAX_SLOW_ROUTE_SWITCHES = 3

    const val MAX_HISTORY_SAMPLES = 5
    private const val MIN_QUALIFICATION_SPAN_MS = 30_000L

    data class ThroughputSample(val measuredAt: Long = 0L, val bytes: Int = 0, val elapsedMs: Long = 0L)
    data class ThroughputQuality(
        val sampleCount: Int = 0,
        val fastSamples: Int = 0,
        val reliabilityPermille: Int = 0,
        val conservativeBytesPerSecond: Long = 0L,
        val latestPayloadValid: Boolean = false,
        val latestFast: Boolean = false,
        val confirmedFast: Boolean = false,
    )

    fun recentSamples(samples: List<ThroughputSample>?, now: Long): List<ThroughputSample> = samples.orEmpty()
        .filter { isFresh(it.measuredAt, now) && it.bytes >= 0 && it.elapsedMs >= 0 }
        .sortedBy { it.measuredAt }
        .distinctBy { it.measuredAt }
        .takeLast(MAX_HISTORY_SAMPLES)

    /** Legacy data is one provisional sample, never fabricated repeated evidence. */
    fun sampleHistory(
        samples: List<ThroughputSample>?, measuredAt: Long, bytes: Int, elapsedMs: Long, now: Long,
    ): List<ThroughputSample> = recentSamples(
        samples?.takeIf { it.isNotEmpty() } ?: listOf(ThroughputSample(measuredAt, bytes, elapsedMs)), now,
    )

    fun throughputQuality(samples: List<ThroughputSample>?, now: Long): ThroughputQuality {
        val recent = recentSamples(samples, now)
        if (recent.isEmpty()) return ThroughputQuality()
        val rates = recent.map { if (it.bytes >= MIN_PAYLOAD_BYTES && it.elapsedMs > 0) bytesPerSecond(it.bytes, it.elapsedMs) else 0L }.sorted()
        val fast = recent.count { isFast(it.bytes, it.elapsedMs) }
        val last = recent.last()
        val latestFast = isFast(last.bytes, last.elapsedMs)
        return ThroughputQuality(
            sampleCount = recent.size,
            fastSamples = fast,
            reliabilityPermille = fast * 1000 / recent.size,
            conservativeBytesPerSecond = rates[(rates.size - 1) / 2],
            latestPayloadValid = last.bytes >= MIN_PAYLOAD_BYTES && last.elapsedMs > 0,
            latestFast = latestFast,
            confirmedFast = recent.size >= 3 && fast * 3 >= recent.size * 2 && latestFast &&
                last.measuredAt - recent.first().measuredAt >= MIN_QUALIFICATION_SPAN_MS,
        )
    }

    fun qualityTier(quality: ThroughputQuality): Int = when {
        quality.confirmedFast -> 0
        quality.latestFast -> 1
        quality.sampleCount == 0 -> 2
        quality.latestPayloadValid -> 3
        else -> 4
    }

    /** Qualification, reliability, then conservative speed; native latency remains a later tie-breaker. */
    fun compareQuality(left: ThroughputQuality, right: ThroughputQuality): Int =
        compareValuesBy(left, right, { qualityTier(it) }, { -it.reliabilityPermille }, { -it.conservativeBytesPerSecond })

    fun mayExploreSlowAlternative(switches: Int): Boolean = switches < MAX_SLOW_ROUTE_SWITCHES

    fun bytesPerSecond(bytes: Int, elapsedMs: Long): Long =
        bytes.toLong() * 1000L / elapsedMs.coerceAtLeast(1L)

    fun isFast(bytes: Int, elapsedMs: Long): Boolean =
        bytes >= MIN_PAYLOAD_BYTES && elapsedMs > 0L && bytesPerSecond(bytes, elapsedMs) >= FAST_BYTES_PER_SECOND

    fun isFresh(measuredAt: Long, now: Long, ttl: Long = MEASUREMENT_TTL_MS): Boolean =
        measuredAt > 0L && now - measuredAt in 0L..ttl

    fun isValidPayload(code: Int, bytes: Int, contentType: String, prefix: String): Boolean {
        val mime = contentType.substringBefore(';').trim().lowercase()
        val expectedType = mime == "application/octet-stream" ||
            mime == "application/javascript" ||
            mime == "text/javascript" ||
            mime == "application/x-javascript" ||
            mime == "text/plain"
        return (code == 200 || code == 206) && bytes >= MIN_PAYLOAD_BYTES && expectedType &&
            !prefix.trimStart().startsWith("<") && !isRestricted(code, null, prefix)
    }

    fun isRestricted(code: Int, location: String?, body: String): Boolean =
        code == 429 || code == 403 || location.orEmpty().contains("/sorry/", ignoreCase = true) ||
            body.contains("unusual traffic", ignoreCase = true) ||
            body.contains("/sorry/", ignoreCase = true) || body.contains("g-recaptcha", ignoreCase = true)

    fun shouldSwitch(
        failures: Int,
        slowSamples: Int,
        now: Long,
        lastSwitch: Long,
        cooldownMs: Long = SWITCH_COOLDOWN_MS,
    ): Boolean =
        (lastSwitch == 0L || now - lastSwitch >= cooldownMs) &&
            (failures >= 3 || (failures >= 2 && slowSamples >= 6) || (slowSamples >= 3))

    fun watchdogPollDelayMs(failures: Int, consecutiveNullSwitches: Int): Long = when {
        consecutiveNullSwitches > 0 -> minOf(30_000L, 10_000L * consecutiveNullSwitches)
        failures > 0 -> 2_000L
        else -> 5_000L
    }

    fun isCurrent(expectedGuid: String, expectedGeneration: Long, guid: String?, generation: Long): Boolean =
        expectedGuid == guid && expectedGeneration == generation
}
