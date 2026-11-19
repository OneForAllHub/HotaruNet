package com.v2ray.ang.core

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HotaruTunnelProbeTest {
    @Test fun `whole request timeout returns error response when proxy is unreachable`() = runBlocking {
        // Probe port 59999 which has no listener; should fail fast and return code = -1
        val result = HotaruTunnelProbe.request(59999, "https://probe.invalid", 262_144, true, timeoutMs = 500L)
        assertEquals(-1, result.code)
        assertEquals(0, result.bytes)
        assertEquals(false, result.validPayload)
    }

    @Test fun `connectivity returns error quickly when proxy port has no listener`() = runBlocking {
        val result = HotaruTunnelProbe.connectivity(59999)
        assertEquals(-1, result.code)
    }
}
