package com.v2ray.ang.handler

import androidx.annotation.VisibleForTesting
import com.v2ray.ang.util.HotaruCrypto
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.SocketAddress
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.net.SocketFactory
import kotlin.math.max

/**
 * Privacy-preserving live presence & aggregate VPN traffic telemetry client.
 *
 * Replaces legacy static hit counters with HMAC-authenticated session heartbeats (60s cadence,
 * 180s server TTL, explicit /leave on disconnect) and idempotent delta-byte accounting via
 * Supabase Edge Functions + PostgreSQL. Endpoint URL and HMAC key live exclusively inside
 * `libhotaruguard.so`.
 */
object HotaruCommunityCounter {
    private const val TAG = "HotaruTelemetry"
    private const val HEARTBEAT_INTERVAL_MS = 60_000L
    private const val MAX_SINGLE_DELTA_BYTES = 20L * 1024L * 1024L * 1024L

    const val PREF_KEY_CLIENT_ID = "pref_hotaru_client_id_v1"
    const val PREF_KEY_TELEMETRY_SEQ = "pref_hotaru_telemetry_seq_v1"
    const val PREF_KEY_PENDING_BYTES = "pref_hotaru_pending_bytes_v1"
    const val PREF_KEY_INFLIGHT_SEQ = "pref_hotaru_inflight_seq_v1"
    const val PREF_KEY_INFLIGHT_BYTES = "pref_hotaru_inflight_bytes_v1"
    const val PREF_KEY_LIVE_ONLINE = "pref_hotaru_live_online_v1"

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val batchLock = Any()

    @Volatile
    private var heartbeatJob: Job? = null

    @Volatile
    private var lastAccountedSessionBytes: Long = 0L

    private val lastBeatSuccessWallMs = AtomicLong(0L)
    private val lastPulseAttemptWallMs = AtomicLong(0L)

    // Fallback in-memory mirrors used when MMKV is unavailable in standalone JVM unit tests.
    private var memClientId: String? = null
    private var memSeq: Long = 0L
    private var memPendingBytes: Long = 0L
    private var memInflightSeq: Long = 0L
    private var memInflightBytes: Long = 0L
    private var memOnlineUsers: Long = 0L

    private val directClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(7, TimeUnit.SECONDS)
            .build()
    }

    /**
     * OkHttp client whose SocketFactory performs SOCKS5 ATYP=0x03 (DOMAINNAME) through local Xray
     * while bypassing device OS DNS completely so local ISP DNS poisoning never drops telemetry.
     */
    @VisibleForTesting
    internal fun createRemoteDnsSocksClient(socksPort: Int, timeoutSec: Long = 5L): OkHttpClient {
        val remoteOnlyDns = Dns { hostname ->
            listOf(InetAddress.getByAddress(hostname, byteArrayOf(127, 0, 0, 1)))
        }
        val factory = object : SocketFactory() {
            override fun createSocket(): Socket = Socks5TunnelSocket("127.0.0.1", socksPort)
            override fun createSocket(host: String?, port: Int): Socket =
                Socks5TunnelSocket("127.0.0.1", socksPort).apply {
                    connect(InetSocketAddress(host.orEmpty(), port), (timeoutSec * 1000L).toInt())
                }
            override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket =
                createSocket(host, port)
            override fun createSocket(host: InetAddress?, port: Int): Socket =
                Socks5TunnelSocket("127.0.0.1", socksPort).apply {
                    connect(InetSocketAddress(host, port), (timeoutSec * 1000L).toInt())
                }
            override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
                createSocket(address, port)
        }
        return OkHttpClient.Builder()
            .dns(remoteOnlyDns)
            .proxy(Proxy.NO_PROXY)
            .socketFactory(factory)
            .connectTimeout(timeoutSec, TimeUnit.SECONDS)
            .readTimeout(timeoutSec, TimeUnit.SECONDS)
            .callTimeout(timeoutSec + 2L, TimeUnit.SECONDS)
            .build()
    }

    private class Socks5TunnelSocket(
        private val socksHost: String,
        private val socksPort: Int,
    ) : Socket() {
        override fun connect(endpoint: SocketAddress) = connect(endpoint, 5000)

        override fun connect(endpoint: SocketAddress, timeout: Int) {
            val target = endpoint as? InetSocketAddress ?: throw IOException("Unsupported endpoint: $endpoint")
            val host = target.address?.hostName?.takeIf { it.isNotBlank() } ?: target.hostString
            val port = target.port
            val effectiveTimeout = if (timeout > 0) timeout else 5000
            super.connect(InetSocketAddress(socksHost, socksPort), effectiveTimeout)
            soTimeout = effectiveTimeout
            performSocks5Connect(this, host, port)
        }
    }

    private fun readByteExact(input: InputStream): Int {
        val b = input.read()
        if (b < 0) throw IOException("Unexpected EOF during SOCKS5 handshake")
        return b and 0xFF
    }

    private fun performSocks5Connect(socket: Socket, host: String, port: Int) {
        val hostBytes = host.toByteArray(Charsets.UTF_8)
        if (hostBytes.size > 255) throw IOException("Hostname too long for SOCKS5")
        val out = socket.getOutputStream()
        val input = socket.getInputStream()

        out.write(byteArrayOf(0x05, 0x01, 0x00))
        out.flush()
        if (readByteExact(input) != 0x05 || readByteExact(input) != 0x00) {
            throw IOException("SOCKS5 greeting rejected")
        }

        val req = ByteArray(4 + 1 + hostBytes.size + 2)
        req[0] = 0x05
        req[1] = 0x01
        req[2] = 0x00
        req[3] = 0x03
        req[4] = hostBytes.size.toByte()
        System.arraycopy(hostBytes, 0, req, 5, hostBytes.size)
        req[req.size - 2] = (port shr 8).toByte()
        req[req.size - 1] = (port and 0xFF).toByte()
        out.write(req)
        out.flush()

        val ver = readByteExact(input)
        val rep = readByteExact(input)
        readByteExact(input) // RSV
        val atyp = readByteExact(input)
        if (ver != 0x05 || rep != 0x00) {
            throw IOException("SOCKS5 connect failed code=$rep")
        }
        val skipBytes = when (atyp) {
            0x01 -> 6
            0x03 -> readByteExact(input) + 2
            0x04 -> 18
            else -> throw IOException("Unknown SOCKS5 ATYP=$atyp")
        }
        repeat(skipBytes) { readByteExact(input) }
    }

    private val _isSelfConnected = MutableStateFlow(false)
    private val _serverOnlineFlow = MutableStateFlow(0L)
    private val _countFlow = MutableStateFlow(0L)
    val countFlow: StateFlow<Long> = _countFlow.asStateFlow()

    @Volatile
    private var isInitialized = false

    fun initIfNeeded() {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            isInitialized = true
            val cachedOnline = decodeLong(PREF_KEY_LIVE_ONLINE, 0L)
            if (cachedOnline > 0L) {
                _serverOnlineFlow.value = cachedOnline
            }
            updateTotalCount()
        }
    }

    /**
     * Daemon-only hook invoked when VPN core loop starts.
     * Resets per-connection watermark, fires an immediate heartbeat, and starts the 60s pulse loop.
     */
    fun onDaemonVpnConnected(socksPortProvider: () -> Int = { SettingsManager.getSocksPort() }) {
        initIfNeeded()
        _isSelfConnected.value = true
        synchronized(batchLock) {
            lastAccountedSessionBytes = 0L
        }
        lastBeatSuccessWallMs.set(0L)
        updateTotalCount()

        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            // Immediate beat on connect + fast warm-up retries so cold tunnels still register right away
            if (!sendBeatOnce(deltaSessionBytes = 0L, socksPort = socksPortProvider())) {
                delay(2_000L)
                if (isActive && !sendBeatOnce(deltaSessionBytes = 0L, socksPort = socksPortProvider())) {
                    delay(4_000L)
                    if (isActive) {
                        sendBeatOnce(deltaSessionBytes = 0L, socksPort = socksPortProvider())
                    }
                }
            }
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                val totalSession = runCatching { NotificationManager.currentSessionTrafficBytes() }.getOrDefault(0L)
                val delta = consumeSessionDelta(totalSession)
                sendBeatOnce(deltaSessionBytes = delta, socksPort = socksPortProvider())
            }
        }
    }

    /**
     * Triggered immediately when phone internet restores or screen wakes while VPN is active,
     * ensuring the very first post-recovery telemetry pulse registers without waiting for the 60s loop.
     */
    fun onDaemonNetworkOrWakeRecovery(
        socksPortProvider: () -> Int = { SettingsManager.getSocksPort() },
        minGapMs: Long = 10_000L,
    ) {
        if (!_isSelfConnected.value) return
        val now = System.currentTimeMillis()
        val lastAttempt = lastPulseAttemptWallMs.get()
        if (now - lastAttempt < minGapMs && lastBeatSuccessWallMs.get() >= lastAttempt) {
            return
        }
        lastPulseAttemptWallMs.set(now)
        scope.launch {
            val totalSession = runCatching { NotificationManager.currentSessionTrafficBytes() }.getOrDefault(0L)
            val delta = consumeSessionDelta(totalSession)
            if (!sendBeatOnce(deltaSessionBytes = delta, socksPort = socksPortProvider())) {
                delay(2_000L)
                sendBeatOnce(deltaSessionBytes = 0L, socksPort = socksPortProvider())
            }
        }
    }

    /**
     * Daemon-only blocking flush executed inside stopCoreLoop() worker coroutine while SOCKS5 may still be up.
     */
    fun onDaemonVpnStopping(finalSessionBytes: Long, socksPort: Int) {
        initIfNeeded()
        heartbeatJob?.cancel()
        heartbeatJob = null
        _isSelfConnected.value = false

        val finalDelta = consumeSessionDelta(finalSessionBytes)
        sendLeaveSync(finalDelta, socksPort)
        synchronized(batchLock) {
            lastAccountedSessionBytes = 0L
        }
        updateTotalCount()
    }

    /**
     * UI-process sync: never mints fake connects on recomposition; updates self-presence and polls /v1/stats.
     */
    fun syncUiState(connected: Boolean) {
        initIfNeeded()
        _isSelfConnected.value = connected
        val cached = decodeLong(PREF_KEY_LIVE_ONLINE, _serverOnlineFlow.value)
        _serverOnlineFlow.value = max(0L, cached)
        updateTotalCount()
        refreshRemoteCountAsync(useProxy = connected)
    }

    fun refreshRemoteCountAsync(useProxy: Boolean = _isSelfConnected.value) {
        initIfNeeded()
        scope.launch {
            val count = fetchRemoteActiveUsersSync(useProxy = useProxy)
            if (count >= 0L) {
                applyServerOnlineCount(count)
            }
        }
    }

    @VisibleForTesting
    internal fun setServerOnlineForTest(count: Long) {
        _serverOnlineFlow.value = max(0L, count)
        updateTotalCount()
    }

    @VisibleForTesting
    internal fun updateSelfConnectedForTest(connected: Boolean) {
        _isSelfConnected.value = connected
        updateTotalCount()
    }

    @VisibleForTesting
    internal fun resetForTest() {
        synchronized(batchLock) {
            lastAccountedSessionBytes = 0L
            memClientId = null
            memSeq = 0L
            memPendingBytes = 0L
            memInflightSeq = 0L
            memInflightBytes = 0L
            memOnlineUsers = 0L
        }
        _isSelfConnected.value = false
        _serverOnlineFlow.value = 0L
        _countFlow.value = 0L
    }

    internal fun consumeSessionDelta(currentSessionTotalBytes: Long): Long {
        if (currentSessionTotalBytes <= 0L) return 0L
        synchronized(batchLock) {
            val delta = (currentSessionTotalBytes - lastAccountedSessionBytes).coerceAtLeast(0L)
            if (currentSessionTotalBytes > lastAccountedSessionBytes) {
                lastAccountedSessionBytes = currentSessionTotalBytes
            }
            return delta
        }
    }

    private fun updateTotalCount() {
        _countFlow.value = calculateEffectiveCount(_isSelfConnected.value, _serverOnlineFlow.value)
    }

    private fun applyServerOnlineCount(serverCount: Long) {
        val clean = max(0L, serverCount)
        encodeLong(PREF_KEY_LIVE_ONLINE, clean)
        _serverOnlineFlow.value = clean
        updateTotalCount()
    }

    internal data class TelemetryBatch(val seq: Long, val deltaBytes: Long)

    internal fun prepareInflightBatch(additionalDeltaBytes: Long): TelemetryBatch {
        synchronized(batchLock) {
            val pending = (decodeLong(PREF_KEY_PENDING_BYTES, 0L) + max(0L, additionalDeltaBytes)).coerceAtLeast(0L)
            val existingSeq = decodeLong(PREF_KEY_INFLIGHT_SEQ, 0L)
            val existingBytes = decodeLong(PREF_KEY_INFLIGHT_BYTES, 0L)

            if (existingSeq > 0L && existingBytes > 0L) {
                encodeLong(PREF_KEY_PENDING_BYTES, pending)
                return TelemetryBatch(existingSeq, existingBytes)
            }

            val sendBytes = pending.coerceAtMost(MAX_SINGLE_DELTA_BYTES)
            val nextSeq = decodeLong(PREF_KEY_TELEMETRY_SEQ, 0L) + 1L
            encodeLong(PREF_KEY_TELEMETRY_SEQ, nextSeq)

            if (sendBytes > 0L) {
                encodeLong(PREF_KEY_PENDING_BYTES, pending - sendBytes)
                encodeLong(PREF_KEY_INFLIGHT_SEQ, nextSeq)
                encodeLong(PREF_KEY_INFLIGHT_BYTES, sendBytes)
            } else {
                encodeLong(PREF_KEY_INFLIGHT_SEQ, 0L)
                encodeLong(PREF_KEY_INFLIGHT_BYTES, 0L)
            }
            return TelemetryBatch(nextSeq, sendBytes)
        }
    }

    internal fun ackInflightBatch(seq: Long) {
        synchronized(batchLock) {
            val inflightSeq = decodeLong(PREF_KEY_INFLIGHT_SEQ, 0L)
            if (inflightSeq == seq) {
                encodeLong(PREF_KEY_INFLIGHT_SEQ, 0L)
                encodeLong(PREF_KEY_INFLIGHT_BYTES, 0L)
            }
        }
    }

    internal fun getOrCreateClientId(): String {
        val existing = runCatching { MmkvManager.decodeSettingsString(PREF_KEY_CLIENT_ID) }.getOrNull()
        if (!existing.isNullOrBlank()) return existing
        val mem = memClientId
        if (!mem.isNullOrBlank()) return mem
        val generated = UUID.randomUUID().toString()
        memClientId = generated
        runCatching { MmkvManager.encodeSettings(PREF_KEY_CLIENT_ID, generated) }
        return generated
    }

    private fun sendBeatOnce(deltaSessionBytes: Long, socksPort: Int): Boolean {
        val baseUrl = HotaruCrypto.getTelemetryUrl().trimEnd('/')
        val secret = HotaruCrypto.getTelemetrySecret()
        if (baseUrl.isEmpty() || secret.isEmpty()) return false

        val cid = getOrCreateClientId()
        val batch = prepareInflightBatch(deltaSessionBytes)
        val sig = signPayload(secret, "beat:$cid:${batch.seq}:${batch.deltaBytes}")
        val ver = com.v2ray.ang.BuildConfig.VERSION_NAME
        val json = """{"cid":"$cid","seq":${batch.seq},"delta":${batch.deltaBytes},"platform":"android","ver":"$ver"}"""

        val online = executePost("$baseUrl/v1/beat", json, sig, preferSocksPort = socksPort)
        if (online >= 0L) {
            lastBeatSuccessWallMs.set(System.currentTimeMillis())
            ackInflightBatch(batch.seq)
            applyServerOnlineCount(online)
            return true
        }
        return false
    }

    private fun sendLeaveSync(finalDeltaBytes: Long, socksPort: Int) {
        val baseUrl = HotaruCrypto.getTelemetryUrl().trimEnd('/')
        val secret = HotaruCrypto.getTelemetrySecret()
        if (baseUrl.isEmpty() || secret.isEmpty()) {
            // Preserve unsent traffic bytes locally for the next connection session even if vault unavailable
            if (finalDeltaBytes > 0L) {
                synchronized(batchLock) {
                    encodeLong(PREF_KEY_PENDING_BYTES, decodeLong(PREF_KEY_PENDING_BYTES, 0L) + finalDeltaBytes)
                }
            }
            return
        }

        val cid = getOrCreateClientId()
        val batch = prepareInflightBatch(finalDeltaBytes)
        val sig = signPayload(secret, "leave:$cid:${batch.seq}:${batch.deltaBytes}")
        val ver = com.v2ray.ang.BuildConfig.VERSION_NAME
        val json = """{"cid":"$cid","seq":${batch.seq},"delta":${batch.deltaBytes},"platform":"android","ver":"$ver"}"""

        val online = executePost("$baseUrl/v1/leave", json, sig, preferSocksPort = socksPort, socksTimeoutSec = 2L)
        if (online >= 0L) {
            ackInflightBatch(batch.seq)
            applyServerOnlineCount(online)
        }
    }

    private fun fetchRemoteActiveUsersSync(useProxy: Boolean): Long {
        val baseUrl = HotaruCrypto.getTelemetryUrl().trimEnd('/')
        val secret = HotaruCrypto.getTelemetrySecret()
        if (baseUrl.isEmpty() || secret.isEmpty()) return -1L

        val cid = getOrCreateClientId()
        val sig = signPayload(secret, "stats:$cid")
        val url = "$baseUrl/v1/stats?cid=$cid"

        val socksPort = if (useProxy) runCatching { SettingsManager.getSocksPort() }.getOrDefault(0) else 0
        val clients = if (socksPort > 0) listOf(createRemoteDnsSocksClient(socksPort), directClient) else listOf(directClient)
        for (client in clients) {
            val online = runCatching {
                val req = Request.Builder()
                    .url(url)
                    .get()
                    .header("User-Agent", "HotaruNet/2.3")
                    .header("X-Hotaru-Sig", sig)
                    .build()
                client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) return@runCatching -1L
                    parseOnlineUsersFromJson(res.body.string())
                }
            }.getOrDefault(-1L)
            if (online >= 0L) return online
        }
        return -1L
    }

    private fun executePost(
        url: String,
        jsonBody: String,
        sigHex: String,
        preferSocksPort: Int,
        socksTimeoutSec: Long = 5L,
    ): Long {
        val clients = if (preferSocksPort > 0) {
            listOf(createRemoteDnsSocksClient(preferSocksPort, socksTimeoutSec), directClient)
        } else {
            listOf(directClient)
        }
        for (client in clients) {
            val online = runCatching {
                val req = Request.Builder()
                    .url(url)
                    .post(jsonBody.toRequestBody(JSON_MEDIA))
                    .header("User-Agent", "HotaruNet/2.3")
                    .header("X-Hotaru-Sig", sigHex)
                    .build()
                client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) {
                        LogUtil.w(TAG, "Telemetry HTTP ${res.code}")
                        return@runCatching -1L
                    }
                    parseOnlineUsersFromJson(res.body.string())
                }
            }.getOrDefault(-1L)
            if (online >= 0L) return online
        }
        return -1L
    }

    fun signPayload(secret: String, message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(message.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    fun parseOnlineUsersFromJson(json: String): Long {
        if (json.isBlank()) return -1L
        val onlineMatch = Regex(""""onlineUsers"\s*:\s*"?(\d+)"?""").find(json)
        if (onlineMatch != null) return onlineMatch.groupValues[1].toLongOrNull() ?: -1L
        val valueMatch = Regex(""""value"\s*:\s*"?(\d+)"?""").find(json)
        return valueMatch?.groupValues?.get(1)?.toLongOrNull() ?: -1L
    }

    fun calculateEffectiveCount(selfConnected: Boolean, serverTotalUsers: Long): Long {
        val clean = max(0L, serverTotalUsers)
        return if (selfConnected) max(1L, clean) else clean
    }

    private fun decodeLong(key: String, default: Long): Long {
        val fromMmkv = runCatching { MmkvManager.decodeSettingsLong(key, Long.MIN_VALUE) }.getOrNull()
        if (fromMmkv != null && fromMmkv != Long.MIN_VALUE) return fromMmkv
        return when (key) {
            PREF_KEY_TELEMETRY_SEQ -> memSeq
            PREF_KEY_PENDING_BYTES -> memPendingBytes
            PREF_KEY_INFLIGHT_SEQ -> memInflightSeq
            PREF_KEY_INFLIGHT_BYTES -> memInflightBytes
            PREF_KEY_LIVE_ONLINE -> memOnlineUsers
            else -> default
        }
    }

    private fun encodeLong(key: String, value: Long) {
        when (key) {
            PREF_KEY_TELEMETRY_SEQ -> memSeq = value
            PREF_KEY_PENDING_BYTES -> memPendingBytes = value
            PREF_KEY_INFLIGHT_SEQ -> memInflightSeq = value
            PREF_KEY_INFLIGHT_BYTES -> memInflightBytes = value
            PREF_KEY_LIVE_ONLINE -> memOnlineUsers = value
        }
        runCatching { MmkvManager.encodeSettings(key, value) }
    }
}
