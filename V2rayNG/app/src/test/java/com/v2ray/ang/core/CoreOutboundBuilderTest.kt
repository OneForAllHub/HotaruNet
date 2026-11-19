package com.v2ray.ang.core

import com.v2ray.ang.enums.EConfigType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoreOutboundBuilderTest {

    @Test
    fun `applyTcpLatencyDefaults enables TcpNoDelay and keepalive for TCP proxy protocols`() {
        for (type in listOf(EConfigType.VLESS, EConfigType.VMESS, EConfigType.TROJAN, EConfigType.SHADOWSOCKS)) {
            val outbound = CoreOutboundBuilder.createInitOutbound(type)!!
            CoreOutboundBuilder.applyTcpLatencyDefaults(outbound)
            val sockopt = outbound.streamSettings?.sockopt
            assertTrue(sockopt?.TcpNoDelay == true, "Expected TcpNoDelay for $type")
            assertEquals(25, sockopt?.tcpKeepAliveIdle, "Expected 25s tcpKeepAliveIdle for $type")
        }
    }

    @Test
    fun `applyTcpLatencyDefaults leaves UDP-native WireGuard and Hysteria2 outbounds untouched`() {
        for (type in listOf(EConfigType.WIREGUARD, EConfigType.HYSTERIA2, EConfigType.MASQUE)) {
            val outbound = CoreOutboundBuilder.createInitOutbound(type)!!
            CoreOutboundBuilder.applyTcpLatencyDefaults(outbound)
            assertNull(outbound.streamSettings?.sockopt, "Did not expect TCP sockopt on $type")
        }
    }
}
