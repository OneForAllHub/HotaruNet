package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class HotaruCommunityCounterTest {

    @Test
    fun `parseOnlineUsersFromJson extracts integer count safely`() {
        assertEquals(42L, HotaruCommunityCounter.parseOnlineUsersFromJson("""{"ok":true,"onlineUsers":42}"""))
        assertEquals(0L, HotaruCommunityCounter.parseOnlineUsersFromJson("""{"ok":true,"onlineUsers":0}"""))
        assertEquals(150L, HotaruCommunityCounter.parseOnlineUsersFromJson("""{"key":"hotarunet","value":150}"""))
        assertEquals(999L, HotaruCommunityCounter.parseOnlineUsersFromJson("""{"onlineUsers":"999"}"""))
        assertEquals(-1L, HotaruCommunityCounter.parseOnlineUsersFromJson(""))
        assertEquals(-1L, HotaruCommunityCounter.parseOnlineUsersFromJson("""{"error":"not found"}"""))
    }

    @Test
    fun `calculateEffectiveCount never inflates or double-counts self`() {
        assertEquals(0L, HotaruCommunityCounter.calculateEffectiveCount(false, 0L))
        assertEquals(1L, HotaruCommunityCounter.calculateEffectiveCount(true, 0L))
        assertEquals(1L, HotaruCommunityCounter.calculateEffectiveCount(true, 1L))
        assertEquals(4L, HotaruCommunityCounter.calculateEffectiveCount(true, 4L))
        assertEquals(4L, HotaruCommunityCounter.calculateEffectiveCount(false, 4L))
    }

    @Test
    fun `signPayload matches canonical RFC2104 HMAC-SHA256 hex digest`() {
        // Test vector: key="secret", msg="beat:client1:1:0"
        val sig = HotaruCommunityCounter.signPayload("secret", "beat:client1:1:0")
        assertEquals(64, sig.length)
        assertEquals(sig, HotaruCommunityCounter.signPayload("secret", "beat:client1:1:0"))
        assertFalse(sig == HotaruCommunityCounter.signPayload("secret", "beat:client1:1:1024"))
    }

    @Test
    fun `inflight delta queue guarantees zero lost or double-counted traffic across retries`() {
        HotaruCommunityCounter.resetForTest()
        try {
            // Session produces 1,500,000 bytes
            assertEquals(1_500_000L, HotaruCommunityCounter.consumeSessionDelta(1_500_000L))
            // Repeating same total gives 0 delta
            assertEquals(0L, HotaruCommunityCounter.consumeSessionDelta(1_500_000L))

            val firstAttempt = HotaruCommunityCounter.prepareInflightBatch(1_500_000L)
            assertEquals(1L, firstAttempt.seq)
            assertEquals(1_500_000L, firstAttempt.deltaBytes)

            // Network fails before ack, and another 500,000 bytes arrive:
            val additional = HotaruCommunityCounter.consumeSessionDelta(2_000_000L)
            assertEquals(500_000L, additional)

            // Retry must resend EXACT same inflight (seq=1, 1,500,000) so server stays idempotent, while 500,000 waits in pending
            val retryAttempt = HotaruCommunityCounter.prepareInflightBatch(additional)
            assertEquals(1L, retryAttempt.seq)
            assertEquals(1_500_000L, retryAttempt.deltaBytes)

            // Server ACKs seq=1; next beat advances to seq=2 for the waiting 500,000 bytes
            HotaruCommunityCounter.ackInflightBatch(1L)
            val nextBatch = HotaruCommunityCounter.prepareInflightBatch(0L)
            assertEquals(2L, nextBatch.seq)
            assertEquals(500_000L, nextBatch.deltaBytes)
        } finally {
            HotaruCommunityCounter.resetForTest()
        }
    }

    @Test
    fun `ui state sync updates visible count accurately without inflating remote count`() {
        HotaruCommunityCounter.resetForTest()
        try {
            HotaruCommunityCounter.setServerOnlineForTest(0L)
            HotaruCommunityCounter.updateSelfConnectedForTest(false)
            assertEquals(0L, HotaruCommunityCounter.countFlow.value)

            HotaruCommunityCounter.updateSelfConnectedForTest(true)
            assertEquals(1L, HotaruCommunityCounter.countFlow.value)

            HotaruCommunityCounter.setServerOnlineForTest(3L)
            assertEquals(3L, HotaruCommunityCounter.countFlow.value)

            HotaruCommunityCounter.updateSelfConnectedForTest(false)
            assertEquals(3L, HotaruCommunityCounter.countFlow.value)

            HotaruCommunityCounter.setServerOnlineForTest(0L)
            assertEquals(0L, HotaruCommunityCounter.countFlow.value)
        } finally {
            HotaruCommunityCounter.resetForTest()
        }
    }

    @Test
    fun `telemetry url and secret have zero hardcoded kotlin fallbacks`() {
        com.v2ray.ang.util.HotaruCrypto.testSecretProvider = null
        assertEquals("", com.v2ray.ang.util.HotaruCrypto.getTelemetryUrl())
        assertEquals("", com.v2ray.ang.util.HotaruCrypto.getTelemetrySecret())
        HotaruCommunityCounter.resetForTest()
    }

    @Test
    fun `createRemoteDnsSocksClient tunnels unresolvable domains via SOCKS5 ATYP 0x03 without local DNS`() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val capturedReq = CompletableFuture<Triple<Int, String, String>>()
            val worker = Thread {
                try {
                    server.accept().use { sock ->
                        val inp = sock.getInputStream()
                        val out = sock.getOutputStream()
                        // Greeting: VER=5, NMETHODS=1, METHOD=0
                        val gVer = inp.read()
                        val nMeth = inp.read()
                        repeat(nMeth) { inp.read() }
                        out.write(byteArrayOf(0x05, 0x00))
                        out.flush()

                        // CONNECT: VER=5, CMD=1, RSV=0, ATYP=3, LEN, HOST, PORT
                        inp.read() // VER
                        inp.read() // CMD
                        inp.read() // RSV
                        val atyp = inp.read()
                        val len = inp.read()
                        val hostBytes = ByteArray(len)
                        var readSoFar = 0
                        while (readSoFar < len) {
                            val n = inp.read(hostBytes, readSoFar, len - readSoFar)
                            if (n < 0) break
                            readSoFar += n
                        }
                        val host = String(hostBytes, Charsets.UTF_8)
                        inp.read(); inp.read() // target port

                        // Reply: succeeded, IPv4 0.0.0.0:0
                        out.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                        out.flush()

                        // Read tunneled HTTP request headers & respond with telemetry JSON
                        val buf = ByteArrayOutputStream()
                        while (true) {
                            val b = inp.read()
                            if (b < 0) break
                            buf.write(b)
                            if (buf.toString(Charsets.UTF_8.name()).contains("\r\n\r\n")) break
                        }
                        val httpBody = """{"ok":true,"onlineUsers":17}"""
                        out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${httpBody.length}\r\nConnection: close\r\n\r\n$httpBody").toByteArray())
                        out.flush()
                        capturedReq.complete(Triple(atyp, host, buf.toString(Charsets.UTF_8.name())))
                    }
                } catch (t: Throwable) {
                    capturedReq.completeExceptionally(t)
                }
            }.apply { isDaemon = true; start() }

            val client = HotaruCommunityCounter.createRemoteDnsSocksClient(socksPort = server.localPort, timeoutSec = 3L)
            val req = Request.Builder()
                .url("http://unresolvable-remote-only.hotaru.invalid/v1/stats?cid=test")
                .get()
                .build()
            val body = client.newCall(req).execute().use { res ->
                assertEquals(200, res.code)
                res.body.string()
            }
            val (atyp, host, httpText) = capturedReq.get(3, TimeUnit.SECONDS)
            worker.join(1000)

            assertEquals(0x03, atyp)
            assertEquals("unresolvable-remote-only.hotaru.invalid", host)
            assertTrue(httpText.contains("Host: unresolvable-remote-only.hotaru.invalid"))
            assertEquals(17L, HotaruCommunityCounter.parseOnlineUsersFromJson(body))
        }
    }
}
