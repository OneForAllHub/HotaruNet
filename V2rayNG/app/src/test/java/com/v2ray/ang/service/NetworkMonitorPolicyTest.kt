package com.v2ray.ang.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetworkMonitorPolicyTest {

    @Test
    fun `initial network callback does not trigger spurious core reload`() {
        assertFalse(
            NetworkMonitor.shouldTriggerRecovery(
                hadNetworkLoss = false,
                lastStableUpstreamId = null,
                currentNetworkId = "101",
            )
        )
    }

    @Test
    fun `same network handle after outage still triggers tunnel recovery`() {
        assertTrue(
            NetworkMonitor.shouldTriggerRecovery(
                hadNetworkLoss = true,
                lastStableUpstreamId = "101",
                currentNetworkId = "101",
            )
        )
    }

    @Test
    fun `handover to a different network handle triggers recovery even without explicit onLost`() {
        assertTrue(
            NetworkMonitor.shouldTriggerRecovery(
                hadNetworkLoss = false,
                lastStableUpstreamId = "101",
                currentNetworkId = "102",
            )
        )
    }

    @Test
    fun `repeated capability updates on same healthy network do not thrash core reload`() {
        assertFalse(
            NetworkMonitor.shouldTriggerRecovery(
                hadNetworkLoss = false,
                lastStableUpstreamId = "101",
                currentNetworkId = "101",
            )
        )
    }
}
