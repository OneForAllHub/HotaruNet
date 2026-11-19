package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.net.ssl.SSLSocketFactory

/**
 * Bounded HTTPS/HTTP checks through the running core SOCKS5 inbound.
 * Uses raw SOCKS5 with ATYP=0x03 (DOMAINNAME) to force remote proxy DNS resolution,
 * preventing local DNS poisoning from corrupting probe targets or triggering false failover.
 */
internal object HotaruTunnelProbe {
    data class Response(
        val code: Int = -1,
        val bytes: Int = 0,
        val elapsedMs: Long = 0L,
        val validPayload: Boolean = false,
        val restricted: Boolean = false,
    )

    suspend fun connectivity(port: Int): Response {
        // Race two independent Anycast CDNs concurrently so a blackholed tunnel fails in ~4s instead of ~12s.
        val primary = coroutineScope {
            val deferreds = listOf(
                async { request(port, "https://cp.cloudflare.com/generate_204", 0, payload = false, timeoutMs = 4000L) },
                async { request(port, "https://www.gstatic.com/generate_204", 0, payload = false, timeoutMs = 4000L) },
            ).toMutableList()
            var lastResponse = Response()
            while (deferreds.isNotEmpty()) {
                val (winner, res) = select<Pair<kotlinx.coroutines.Deferred<Response>, Response>> {
                    for (d in deferreds) {
                        d.onAwait { value -> d to value }
                    }
                }
                deferreds.remove(winner)
                lastResponse = res
                if (res.code == 204) {
                    for (remaining in deferreds) remaining.cancel()
                    return@coroutineScope res
                }
            }
            lastResponse
        }
        if (primary.code == 204) return primary
        // Only try third endpoint if one primary actually received an HTTP response (not a socket timeout/drop)
        if (primary.code > 0) {
            val yt = request(port, "https://www.youtube.com/generate_204", 0, payload = false, timeoutMs = 3500L)
            if (yt.code == 204) return yt
        }
        return Response()
    }

    suspend fun throughput(port: Int): Response = withTimeoutOrNull(18_000L) {
        var last = Response()
        for (url in listOf(
            "https://speed.cloudflare.com/__down?bytes=131072",
            "https://cdn.jsdelivr.net/npm/axios@1.7.0/dist/axios.min.js",
            "https://ajax.googleapis.com/ajax/libs/jquery/3.7.1/jquery.min.js",
        )) {
            last = request(port, url, 131_072, payload = true, timeoutMs = 8_000L)
            if (last.validPayload) return@withTimeoutOrNull last
        }
        last
    } ?: Response()

    suspend fun search(port: Int): Response = request(
        port, "https://www.google.com/search?q=weather", 16_384, payload = false, timeoutMs = 6000L
    )

    internal suspend fun request(
        port: Int,
        urlString: String,
        maxBytes: Int,
        payload: Boolean,
        timeoutMs: Long = 12_000L
    ): Response = withTimeoutOrNull(timeoutMs) {
        withContext(Dispatchers.IO) {
            val startNs = System.nanoTime()
            val parsedUrl = try { URL(urlString) } catch (_: Exception) { return@withContext Response() }
            val host = parsedUrl.host
            val isHttps = parsedUrl.protocol.equals("https", ignoreCase = true)
            val targetPort = if (parsedUrl.port > 0) parsedUrl.port else if (isHttps) 443 else 80
            val path = if (parsedUrl.path.isNullOrEmpty()) "/" else parsedUrl.file

            var rawSocket: Socket? = null
            val cancelHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
                if (cause != null) {
                    try { rawSocket?.close() } catch (_: Exception) {}
                }
            }
            try {
                rawSocket = Socket()
                val socketTimeout = minOf(timeoutMs.toInt(), 8000)
                rawSocket.soTimeout = socketTimeout
                rawSocket.connect(InetSocketAddress("127.0.0.1", port), minOf(timeoutMs.toInt(), 5000))
                val inStream: InputStream = rawSocket.getInputStream()
                val outStream: OutputStream = rawSocket.getOutputStream()

                // 1. SOCKS5 Greet: VER=5, NMETHODS=1, METHOD=0 (No Auth)
                outStream.write(byteArrayOf(0x05, 0x01, 0x00))
                outStream.flush()
                val ver = inStream.read()
                val meth = inStream.read()
                if (ver != 0x05 || meth != 0x00) return@withContext Response()

                // 2. SOCKS5 Connect with ATYP=0x03 (DOMAINNAME) -> Remote proxy resolves DNS
                val hostBytes = host.toByteArray(Charsets.UTF_8)
                val req = ByteArray(4 + 1 + hostBytes.size + 2)
                req[0] = 0x05 // VER
                req[1] = 0x01 // CMD CONNECT
                req[2] = 0x00 // RSV
                req[3] = 0x03 // ATYP DOMAINNAME
                req[4] = hostBytes.size.toByte()
                System.arraycopy(hostBytes, 0, req, 5, hostBytes.size)
                req[req.size - 2] = (targetPort shr 8).toByte()
                req[req.size - 1] = (targetPort and 0xFF).toByte()
                outStream.write(req)
                outStream.flush()

                // 3. Read SOCKS5 Response header
                val repVer = inStream.read()
                val repCode = inStream.read()
                if (repVer != 0x05 || repCode != 0x00) return@withContext Response()
                inStream.read() // RSV
                val atyp = inStream.read()
                val skipCount = when (atyp) {
                    0x01 -> 6
                    0x03 -> {
                        val dlen = inStream.read()
                        dlen + 2
                    }
                    0x04 -> 18
                    else -> 0
                }
                var skipped = 0
                while (skipped < skipCount) {
                    val r = inStream.read()
                    if (r < 0) break
                    skipped++
                }

                // 4. If HTTPS, wrap socket with TLS and enforce remote SNI
                val (finalIn, finalOut) = if (isHttps) {
                    val sslSocket = ((SSLSocketFactory.getDefault() as SSLSocketFactory)
                        .createSocket(rawSocket, host, targetPort, true)) as javax.net.ssl.SSLSocket
                    sslSocket.soTimeout = socketTimeout
                    try {
                        val sslParams = sslSocket.sslParameters
                        sslParams.serverNames = listOf(javax.net.ssl.SNIHostName(host))
                        sslSocket.sslParameters = sslParams
                    } catch (e: Exception) {
                        LogUtil.w(AppConfig.TAG, "Failed to set SNI for $host: ${e.message}")
                    }
                    sslSocket.startHandshake()
                    Pair(sslSocket.getInputStream(), sslSocket.getOutputStream())
                } else {
                    Pair(inStream, outStream)
                }

                // 5. Send HTTP request (measure payload throughput from GET write so cold SOCKS/TLS RTT does not skew 128 KiB rate)
                val httpStartNs = System.nanoTime()
                val httpReq = "GET $path HTTP/1.1\r\nHost: $host\r\nUser-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64)\r\nAccept: */*\r\nConnection: close\r\n\r\n"
                finalOut.write(httpReq.toByteArray(Charsets.UTF_8))
                finalOut.flush()

                // 6. Read HTTP response headers
                val headerBuffer = ByteArray(4096)
                var headerLen = 0
                while (headerLen < headerBuffer.size) {
                    val b = finalIn.read()
                    if (b < 0) break
                    headerBuffer[headerLen++] = b.toByte()
                    if (headerLen >= 4 &&
                        headerBuffer[headerLen - 4] == '\r'.code.toByte() &&
                        headerBuffer[headerLen - 3] == '\n'.code.toByte() &&
                        headerBuffer[headerLen - 2] == '\r'.code.toByte() &&
                        headerBuffer[headerLen - 1] == '\n'.code.toByte()
                    ) {
                        break
                    }
                }
                val headers = String(headerBuffer, 0, headerLen, Charsets.UTF_8)
                val statusLine = headers.lineSequence().firstOrNull().orEmpty()
                val parts = statusLine.split(" ")
                val statusCode = if (parts.size >= 2) parts[1].toIntOrNull() ?: -1 else -1

                var contentType = ""
                var location: String? = null
                for (line in headers.lines()) {
                    val lower = line.lowercase()
                    if (lower.startsWith("content-type:")) {
                        contentType = line.substringAfter(":").trim()
                    } else if (lower.startsWith("location:")) {
                        location = line.substringAfter(":").trim()
                    }
                }

                var bytes = 0
                val bodyPrefix = StringBuilder()
                if (statusCode == 200 && maxBytes > 0) {
                    val buffer = ByteArray(8192)
                    while (bytes < maxBytes) {
                        val count = finalIn.read(buffer, 0, minOf(buffer.size, maxBytes - bytes))
                        if (count < 0) break
                        if (bytes < 16_384) {
                            bodyPrefix.append(String(buffer, 0, minOf(count, 16_384 - bytes), Charsets.UTF_8))
                        }
                        bytes += count
                    }
                }

                val prefixStr = bodyPrefix.toString()
                val restricted = HotaruHealthPolicy.isRestricted(statusCode, location, prefixStr)
                val measureBaseNs = if (payload && maxBytes > 0) httpStartNs else startNs
                val elapsedMs = ((System.nanoTime() - measureBaseNs) / 1_000_000L).coerceAtLeast(1L)
                Response(
                    code = statusCode,
                    bytes = bytes,
                    elapsedMs = elapsedMs,
                    validPayload = payload && !restricted && HotaruHealthPolicy.isValidPayload(statusCode, bytes, contentType, prefixStr),
                    restricted = restricted,
                )
            } catch (e: Exception) {
                LogUtil.w(AppConfig.TAG, "Hotaru probe failed component=SOCKS operation=$urlString: ${e.message}")
                Response()
            } finally {
                cancelHandle?.dispose()
                try { rawSocket?.close() } catch (_: Exception) {}
            }
        }
    } ?: Response()
}
