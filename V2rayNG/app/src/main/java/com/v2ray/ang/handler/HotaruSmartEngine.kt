package com.v2ray.ang.handler

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.CoreConfigManager
import com.v2ray.ang.core.CoreNativeManager
import com.v2ray.ang.core.HotaruHealthPolicy
import java.security.MessageDigest
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.extension.isComplexType
import com.v2ray.ang.service.RealPingExecutionLimiter
import com.v2ray.ang.util.HotaruCrypto
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * Autonomous Smart Auto-Pilot engine for HotaruNet.
 *
 * Adapts transport parameters (MTU, tunnel timeouts, DNS) to the active client network,
 * performs a two-stage bounded speed + real-tunnel verification scan across all imported nodes,
 * persists verified candidates in multi-process MMKV, and handles seamless failover when
 * an active node stalls or degrades.
 */
object HotaruSmartEngine {

    private const val LOG_TAG = "HotaruSmartEngine"
    // A new key excludes legacy queues that mixed TCP-only candidates with HTTP-verified ones.
    private const val KEY_VERIFIED_GUIDS = "hotaru_smart_http_verified_guids_v2"
    private const val KEY_UNDERLYING_NETWORK = "hotaru_underlying_network"
    private const val KEY_SEARCH_CHECK_MS = "hotaru_search_check_ms"

    internal data class TunnelEvidence(
        val schemaVersion: Int = 1,
        val samples: List<HotaruHealthPolicy.ThroughputSample>? = null,
        val fingerprint: String = "",
        val network: String = "",
        val measuredAt: Long = 0L,
        val bytes: Int = 0,
        val elapsedMs: Long = 0L,
        val searchCheckedAt: Long = 0L,
        val searchRestricted: Boolean = false,
        val latencyMs: Long = 0L,
        val latencyMeasuredAt: Long = 0L,
    )

    private fun fingerprint(profile: ProfileItem): String = MessageDigest.getInstance("SHA-256")
        .digest(JsonUtil.toJson(profile).toByteArray()).joinToString("") { byte -> "%02x".format(byte) }

    internal fun configIdentity(profile: ProfileItem): String = fingerprint(profile.duplicateIdentity())

    private fun profileFingerprint(guid: String): String? = MmkvManager.decodeServerConfig(guid)?.let(::configIdentity)

    private fun evidenceNetwork(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return ""
        val active = cm.activeNetwork ?: return ""
        val caps = cm.getNetworkCapabilities(active) ?: return ""
        val network = if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            MmkvManager.decodeSettingsString(KEY_UNDERLYING_NETWORK).orEmpty()
        } else active.toString()
        if (network.isEmpty()) return ""
        // Include the available operator without storing it in clear text. The Android
        // network handle distinguishes Wi-Fi, cellular and individual connections.
        val operator = runCatching {
            (context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager)
                ?.networkOperator.orEmpty()
        }.getOrDefault("")
        return networkProfileKey(operator, network)
    }

    internal fun networkProfileKey(operator: String, network: String): String {
        if (network.isEmpty()) return ""
        val identity = "$operator|$network"
        return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun evidenceKey(guid: String, network: String) = "hotaru_evidence_v3_${guid}_$network"
    private fun identityEvidenceKey(fingerprint: String, network: String) = "hotaru_evidence_id_v3_${fingerprint}_$network"
    private fun tierStatsKey(network: String, tier: TransportTier) = "hotaru_tier_stats_v1_${network}_${tier.name}"

    internal data class TierNetworkStat(
        val samples: Int = 0,
        val ewmaKbps: Long = 0L,
        val failStreak: Int = 0,
        val updatedAt: Long = 0L,
    )

    internal fun computeAdaptiveTierBonus(stat: TierNetworkStat?, now: Long = System.currentTimeMillis()): Long {
        if (stat == null || stat.updatedAt <= 0L || now - stat.updatedAt > 24 * 60 * 60_000L) return 0L
        if (stat.failStreak >= 3) return -2600L
        if (stat.failStreak == 2) return -1200L
        if (stat.samples > 0 && stat.ewmaKbps >= 64L) {
            return (stat.ewmaKbps * 4L).coerceIn(200L, 3600L)
        }
        return 0L
    }

    private fun tierStat(network: String, tier: TransportTier): TierNetworkStat? {
        if (network.isEmpty()) return null
        val raw = MmkvManager.decodeSettingsString(tierStatsKey(network, tier)) ?: return null
        return JsonUtil.fromJsonSafe(raw, TierNetworkStat::class.java)
    }

    private fun recordTierOutcome(network: String, tier: TransportTier, empiricalBps: Long, success: Boolean) {
        if (network.isEmpty()) return
        val now = System.currentTimeMillis()
        val prev = tierStat(network, tier) ?: TierNetworkStat()
        val sampleKbps = (empiricalBps / 1000L).coerceAtLeast(0L)
        val next = if (success && sampleKbps > 0L) {
            val blended = if (prev.samples == 0 || prev.ewmaKbps <= 0L) sampleKbps else ((prev.ewmaKbps * 6L + sampleKbps * 4L) / 10L)
            TierNetworkStat(samples = prev.samples + 1, ewmaKbps = blended, failStreak = 0, updatedAt = now)
        } else {
            TierNetworkStat(samples = prev.samples + 1, ewmaKbps = prev.ewmaKbps, failStreak = prev.failStreak + 1, updatedAt = now)
        }
        MmkvManager.encodeSettings(tierStatsKey(network, tier), JsonUtil.toJson(next))
    }

    fun noteUnderlyingNetwork(network: String) {
        MmkvManager.encodeSettings(KEY_UNDERLYING_NETWORK, network)
    }

    private fun evidence(context: Context, guid: String): TunnelEvidence? {
        val network = evidenceNetwork(context).takeIf { it.isNotEmpty() } ?: return null
        val profile = MmkvManager.decodeServerConfig(guid) ?: return null
        val idFp = configIdentity(profile)
        val raw = MmkvManager.decodeSettingsString(evidenceKey(guid, network))?.takeIf { it.isNotEmpty() }
            ?: MmkvManager.decodeSettingsString(identityEvidenceKey(idFp, network))?.takeIf { it.isNotEmpty() }
            ?: return null
        val value = JsonUtil.fromJsonSafe(raw, TunnelEvidence::class.java) ?: return null
        val fingerprintMatches = value.fingerprint == idFp ||
            (value.schemaVersion == 1 && value.fingerprint == fingerprint(profile))
        return value.takeIf { fingerprintMatches && it.network.isNotEmpty() && it.network == network }
    }

    fun recordThroughput(context: Context, guid: String, bytes: Int, elapsedMs: Long) {
        val previous = evidence(context, guid)
        val now = System.currentTimeMillis()
        val history = previous?.let { sampleHistory(it, now) }.orEmpty()
        val fp = profileFingerprint(guid) ?: return
        val value = TunnelEvidence(
            schemaVersion = 2,
            samples = HotaruHealthPolicy.recentSamples(history + HotaruHealthPolicy.ThroughputSample(now, bytes, elapsedMs), now),
            fingerprint = fp,
            network = evidenceNetwork(context),
            measuredAt = now, bytes = bytes, elapsedMs = elapsedMs,
            searchCheckedAt = previous?.searchCheckedAt ?: 0L,
            searchRestricted = previous?.searchRestricted ?: false,
            latencyMs = previous?.latencyMs ?: 0L,
            latencyMeasuredAt = previous?.latencyMeasuredAt ?: 0L,
        )
        if (value.network.isNotEmpty()) {
            val json = JsonUtil.toJson(value)
            MmkvManager.encodeSettings(evidenceKey(guid, value.network), json)
            MmkvManager.encodeSettings(identityEvidenceKey(fp, value.network), json)
        }
    }

    private fun recordLatency(context: Context, guid: String, delayMs: Long, expectedNetwork: String) {
        if (delayMs <= 0L || expectedNetwork.isEmpty()) return
        val network = evidenceNetwork(context).takeIf { it == expectedNetwork } ?: return
        val fp = profileFingerprint(guid) ?: return
        val current = evidence(context, guid) ?: TunnelEvidence(
            schemaVersion = 2, fingerprint = fp, network = network,
        )
        val json = JsonUtil.toJson(current.copy(
            latencyMs = delayMs, latencyMeasuredAt = System.currentTimeMillis(),
        ))
        MmkvManager.encodeSettings(evidenceKey(guid, network), json)
        MmkvManager.encodeSettings(identityEvidenceKey(fp, network), json)
    }

    fun shouldCheckSearch(): Boolean = !HotaruHealthPolicy.isFresh(
        MmkvManager.decodeSettingsLong(KEY_SEARCH_CHECK_MS, 0L), System.currentTimeMillis(), HotaruHealthPolicy.SEARCH_INTERVAL_MS,
    )

    fun reserveSearchCheck() = MmkvManager.encodeSettings(KEY_SEARCH_CHECK_MS, System.currentTimeMillis())

    fun recordSearchRestriction(context: Context, guid: String, restricted: Boolean) {
        val previous = evidence(context, guid) ?: return
        val json = JsonUtil.toJson(previous.copy(
            searchCheckedAt = System.currentTimeMillis(), searchRestricted = restricted,
        ))
        MmkvManager.encodeSettings(evidenceKey(guid, previous.network), json)
        if (previous.fingerprint.isNotEmpty()) {
            MmkvManager.encodeSettings(identityEvidenceKey(previous.fingerprint, previous.network), json)
        }
    }

    internal fun sampleHistory(value: TunnelEvidence, now: Long): List<HotaruHealthPolicy.ThroughputSample> =
        HotaruHealthPolicy.sampleHistory(value.samples, value.measuredAt, value.bytes, value.elapsedMs, now)

    private fun quality(context: Context, guid: String, now: Long = System.currentTimeMillis()): HotaruHealthPolicy.ThroughputQuality =
        HotaruHealthPolicy.throughputQuality(evidence(context, guid)?.let { sampleHistory(it, now) }, now)

    private fun evidenceRank(context: Context, guid: String): Int = HotaruHealthPolicy.qualityTier(quality(context, guid))

    private fun routeQualityComparator(context: Context, guids: List<String>): Comparator<String> {
        val now = System.currentTimeMillis()
        val snapshots = guids.associateWith { guid ->
            val value = evidence(context, guid)
            val quality = HotaruHealthPolicy.throughputQuality(value?.let { sampleHistory(it, now) }, now)
            val restricted = value?.searchRestricted == true &&
                HotaruHealthPolicy.isFresh(value.searchCheckedAt, now, HotaruHealthPolicy.SEARCH_INTERVAL_MS)
            quality to restricted
        }
        val unknown = HotaruHealthPolicy.ThroughputQuality() to false
        return Comparator { left, right ->
            val a = snapshots[left] ?: unknown
            val b = snapshots[right] ?: unknown
            HotaruHealthPolicy.compareQuality(a.first, b.first).takeIf { it != 0 }
                ?: a.second.compareTo(b.second)
        }
    }
    private const val KEY_LAST_SCAN_MS = "hotaru_smart_last_scan_ms"
    private const val KEY_LAST_HARVEST_MS = "hotaru_smart_last_harvest_ms"
    private const val KEY_LAST_FEED_SHA256 = "hotaru_last_feed_sha256"
    private const val KEY_ACTIVE_NET_KIND = "hotaru_smart_net_kind"
    private const val KEY_NET_SUMMARY_EN = "hotaru_net_summary_en"
    private const val KEY_NET_SUMMARY_FA = "hotaru_net_summary_fa"

    private const val SCAN_CACHE_TTL_MS = 10 * 60_000L
    private const val LIVE_HARVEST_INTERVAL_MS = 10 * 60_000L
    private const val LIVE_HARVEST_HEALTHY_INTERVAL_MS = 30 * 60_000L
    private const val MAX_POOL_SERVERS = 450
    private const val STAGE1_CONCURRENCY = 32
    private const val STAGE1_TIMEOUT_MS = 1500
    private const val STAGE1_FALLBACK_TIMEOUT_MS = 2200
    private const val STAGE1_TOP_CANDIDATES = 96
    private const val STAGE2_CONCURRENCY = 12
    private const val STAGE2_TIMEOUT_MS = 4000L
    private const val STAGE3_TIMEOUT_MS = 6500L
    private const val STAGE3_CANDIDATES = 8
    private const val STAGE3_GOOGLE_URL = "https://www.gstatic.com/generate_204"
    private const val STAGE3_PRIMARY_URL = "https://www.gstatic.com/generate_204"
    private const val STAGE3_FALLBACK_URL = "https://cp.cloudflare.com/generate_204"
    private const val STAGE3_YOUTUBE_URL = "https://www.gstatic.com/generate_204"
    private const val STAGE3_CF_PAYLOAD_URL = "https://speed.cloudflare.com/__down?bytes=32768"
    private const val STAGE3_PAYLOAD_URL = STAGE3_CF_PAYLOAD_URL
    private const val STAGE3_FALLBACK_PAYLOAD_URL = "https://cdn.jsdelivr.net/npm/axios@1.7.0/dist/axios.min.js"
    private const val PAYLOAD_PROBE_BYTES = 32_768
    private const val FRAGILE_PENALTY_MS = 1200L

    private val PREBENCH_KBPS_REGEX = Regex("""(\d+(?:\.\d+)?)\s*(KiB|KB|MiB|MB)/s""", RegexOption.IGNORE_CASE)

    private val URI_REGEX = Regex(
        """(?:vless|vmess|trojan|ss|hysteria2|hy2)://[^\s<>\"'`\\]+""",
        RegexOption.IGNORE_CASE,
    )

    private val MIRROR_SUB_URLS = emptyList<String>()
    private val TELEGRAM_WEB_SOURCES = emptyList<String>()

    internal enum class TransportTier(val quota: Int, val speedBonusMs: Long) {
        REALITY_VISION(quota = 4, speedBonusMs = 400L),
        WS_TLS(quota = 4, speedBonusMs = 380L),
        XHTTP(quota = 3, speedBonusMs = 300L),
        HYSTERIA2(quota = 3, speedBonusMs = 250L),
        GRPC(quota = 2, speedBonusMs = 150L),
        WARP_DEDICATED(quota = 2, speedBonusMs = 100L),
        MULTI_PROTO(quota = 2, speedBonusMs = 50L),
        DIRECT_DPI_SHIELD(quota = 1, speedBonusMs = -1000L),
    }

    enum class NetworkKind(val mtu: Int, val hevTimeout: String, val labelEn: String, val labelFa: String) {
        WIFI(mtu = 1380, hevTimeout = "300,60", labelEn = "Wi-Fi", labelFa = "وای‌فای"),
        ETHERNET(mtu = 1380, hevTimeout = "300,60", labelEn = "Ethernet", labelFa = "شبکه سیمی"),
        CELLULAR(mtu = 1360, hevTimeout = "180,45", labelEn = "Mobile Data", labelFa = "اینترنت همراه"),
        OTHER(mtu = 1360, hevTimeout = "300,60", labelEn = "Auto Network", labelFa = "شبکه خودکار"),
    }

    data class ClientNetworkProfile(
        val kind: NetworkKind,
        val mtu: Int,
        val hevRwTimeout: String,
        val downstreamKbps: Int,
        val isMetered: Boolean,
        val summaryEn: String,
        val summaryFa: String,
    )

    data class SelectionResult(
        val guid: String,
        val remarks: String,
        val delayMs: Long,
        val verifiedGuids: List<String>,
        val networkProfile: ClientNetworkProfile,
    )

    internal data class CandidatePotential(
        val guid: String,
        val profile: ProfileItem,
        val prebenchKbps: Long,
        val isEuro: Boolean = false,
        val tier: TransportTier,
        val port: Int,
        val hostKey: String,
        val potentialScore: Long,
    )

    internal data class VerifiedCandidate(
        val guid: String,
        val profile: ProfileItem,
        val delayMs: Long,
        val prebenchKbps: Long,
        val tier: TransportTier,
        val finalScore: Long,
    )

    private data class Stage1Candidate(
        val guid: String,
        val serverKey: String,
        val hostKey: String,
        val tier: TransportTier,
        val configType: EConfigType,
        val security: String?,
        val port: Int,
        val remarks: String,
        val tcpMs: Long,
        val isFragile: Boolean,
        val stage1Score: Long,
        val scoreAdjustment: Long,
    )

    private data class Stage2Candidate(
        val candidate: Stage1Candidate,
        val realDelayMs: Long,
        val score: Long,
    )

    enum class ConnectStage(
        val stepIndex: Int,
        val totalSteps: Int,
        val titleFa: String,
        val subtitleFa: String,
        val titleEn: String,
        val badgeTag: String,
    ) {
        IDLE(0, 4, "", "", "", ""),
        WARM_RECONNECT(
            3, 4,
            "اتصال فوق‌سریع به مسیر تاییدشده...",
            "تست سرعت لحظه‌ای بهترین سرور قبلی روی شبکه شما ⚡",
            "Instant warm reconnect...",
            "FAST",
        ),
        UPDATING_NODES(
            1, 4,
            "دریافت سریع نودهای تازه...",
            "هوتارو داره جدیدترین سرورهای کلود رو همزمان چک می‌کنه ✨",
            "Syncing fresh cloud nodes...",
            "SYNC",
        ),
        TCP_SWEEP(
            2, 4,
            "اسکن نوری پینگ و پورت‌ها...",
            "پیدا کردن بازترین مسیرها روی اینترنت شما ⚡",
            "Sweeping fastest ports on your ISP...",
            "PING",
        ),
        SPEED_TEST_WAVE_1(
            3, 4,
            "تست سرعت واقعی دانلود (موج ۱)...",
            "سنجش پهنای باند واقعی روی برترین کاندیدها 🚀",
            "Benchmarking real download speed (Wave 1)...",
            "SPEED 1",
        ),
        SPEED_TEST_WAVE_2(
            3, 4,
            "تست عمیق سرعت دانلود (موج ۲)...",
            "بررسی نودهای بیشتر برای بالاترین سرعت ممکن 🔥",
            "Deep bandwidth verification (Wave 2)...",
            "SPEED 2",
        ),
        SPEED_TEST_WAVE_3(
            3, 4,
            "جستجوی نهایی پرسرعت‌ترین مسیر...",
            "تست آخرین گروه سرورها برای عبور از محدودیت 🛰️",
            "Final wave high-throughput scan...",
            "SPEED 3",
        ),
        CLOUD_RECOVERY(
            3, 4,
            "ریکاوری هوشمند از کلود...",
            "دریافت و تست مسیرهای جایگزین ضد فیلتر 🛡️",
            "Smart cloud recovery scan...",
            "SHIELD",
        ),
        LOCKING_TUNNEL(
            4, 4,
            "قفل روی سریع‌ترین تانل!",
            "آماده پرواز! در حال روشن کردن اتصال امن 🦋",
            "Locking fastest tunnel & starting VPN!",
            "LOCKED",
        ),
    }

    private val _connectStageFlow = MutableStateFlow(ConnectStage.IDLE)
    val connectStageFlow: StateFlow<ConnectStage> = _connectStageFlow.asStateFlow()

    fun resetConnectStage() {
        _connectStageFlow.value = ConnectStage.IDLE
    }

    private val scanMutex = Mutex()
    private val feedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val blacklistedGuids = ConcurrentHashMap.newKeySet<String>()
    private val threadCounter = AtomicInteger(1)
    private val xrayExecutor = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "HotaruSmartXray-${threadCounter.getAndIncrement()}").apply {
            isDaemon = true
        }
    }

    private fun resolvePhysicalNetworkCapabilities(cm: ConnectivityManager?): NetworkCapabilities? {
        if (cm == null) return null
        val activeCaps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        if (activeCaps != null && !activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            return activeCaps
        }
        val nonVpnCaps = cm.allNetworks.mapNotNull { net ->
            cm.getNetworkCapabilities(net)?.takeIf { caps ->
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
        return nonVpnCaps.firstOrNull { it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) }
            ?: nonVpnCaps.firstOrNull { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }
            ?: nonVpnCaps.firstOrNull { it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) }
            ?: nonVpnCaps.firstOrNull()
            ?: activeCaps
    }

    /**
     * Reads active OS network capabilities and tunes MTU, tunnel timeout, and Iran DNS invariants
     * into multi-process MMKV storage so both UI and :daemon processes use optimal parameters.
     */
    fun optimizeForClientNetwork(context: Context): ClientNetworkProfile {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = resolvePhysicalNetworkCapabilities(cm)

        val kind = when {
            caps == null -> NetworkKind.OTHER
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.ETHERNET
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkKind.CELLULAR
            else -> NetworkKind.OTHER
        }

        val downstreamKbps = caps?.linkDownstreamBandwidthKbps?.coerceAtLeast(0) ?: 0
        val isMetered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false

        val tm = context.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
        val rawCarrier = if (kind == NetworkKind.CELLULAR) {
            runCatching {
                tm?.simOperatorName?.takeIf { it.isNotBlank() } ?: tm?.networkOperatorName?.takeIf { it.isNotBlank() }
            }.getOrNull()?.trim()
        } else null

        val (carrierEn, carrierFa) = when {
            rawCarrier == null -> Pair(kind.labelEn, kind.labelFa)
            rawCarrier.contains("Irancell", ignoreCase = true) || rawCarrier.contains("MTN", ignoreCase = true) -> Pair("MTN Irancell", "ایرانسل")
            rawCarrier.contains("MCI", ignoreCase = true) || rawCarrier.contains("TCI", ignoreCase = true) || rawCarrier.contains("Telecommunication", ignoreCase = true) -> Pair("MCI Mobile", "همراه اول")
            rawCarrier.contains("Rightel", ignoreCase = true) -> Pair("Rightel", "رایتل")
            rawCarrier.contains("Shatel", ignoreCase = true) -> Pair("Shatel Mobile", "شاتل موبایل")
            else -> Pair(rawCarrier, rawCarrier)
        }

        MmkvManager.encodeSettings(AppConfig.PREF_VPN_MTU, kind.mtu.toString())
        MmkvManager.encodeSettings(AppConfig.PREF_HEV_TUNNEL_RW_TIMEOUT, kind.hevTimeout)
        MmkvManager.encodeSettings(AppConfig.PREF_LOCAL_DNS_ENABLED, true)
        MmkvManager.encodeSettings(AppConfig.PREF_REMOTE_DNS, AppConfig.DNS_PROXY)
        MmkvManager.encodeSettings(AppConfig.PREF_DOMESTIC_DNS, AppConfig.DNS_DIRECT)
        MmkvManager.encodeSettings(AppConfig.PREF_VPN_DNS, AppConfig.DNS_VPN)
        MmkvManager.encodeSettings(AppConfig.PREF_IPV6_ENABLED, false)
        MmkvManager.encodeSettings(AppConfig.PREF_FAKE_DNS_ENABLED, true)
        // Enable smart per-protocol fragment: CoreOutboundBuilder strictly applies tlshello ONLY to standard TLS (skipping REALITY)
        MmkvManager.encodeSettings(AppConfig.PREF_FRAGMENT_ENABLED, true)
        MmkvManager.encodeSettings(AppConfig.PREF_FRAGMENT_PACKETS, "tlshello")
        MmkvManager.encodeSettings(AppConfig.PREF_FRAGMENT_LENGTH, "100-200")
        MmkvManager.encodeSettings(AppConfig.PREF_FRAGMENT_INTERVAL, "10-20")
        MmkvManager.encodeSettings(KEY_ACTIVE_NET_KIND, kind.name)

        val summaryEn = "$carrierEn • MTU ${kind.mtu}"
        val summaryFa = "$carrierFa • MTU ${kind.mtu}"
        MmkvManager.encodeSettings(KEY_NET_SUMMARY_EN, summaryEn)
        MmkvManager.encodeSettings(KEY_NET_SUMMARY_FA, summaryFa)

        Log.i(
            LOG_TAG,
            "optimizeForClientNetwork kind=${kind.name} mtu=${kind.mtu} downstreamKbps=$downstreamKbps metered=$isMetered"
        )
        return ClientNetworkProfile(
            kind = kind,
            mtu = kind.mtu,
            hevRwTimeout = kind.hevTimeout,
            downstreamKbps = downstreamKbps,
            isMetered = isMetered,
            summaryEn = summaryEn,
            summaryFa = summaryFa,
        )
    }

    fun hasPhysicalInternet(context: Context): Boolean = runCatching {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@runCatching true
        cm.allNetworks.any { net ->
            val caps = cm.getNetworkCapabilities(net) ?: return@any false
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }.getOrDefault(true)

    fun clearTransientSessionBlocks() {
        blacklistedGuids.clear()
        MmkvManager.encodeSettings(KEY_LAST_SCAN_MS, 0L)
    }

    /**
     * Marks a server GUID as failed/unhealthy for this session and persists negative delay in MMKV.
     */
    fun blacklistServer(guid: String?) {
        if (guid.isNullOrBlank()) return
        val profile = MmkvManager.decodeServerConfig(guid)
        if (profile?.configType == EConfigType.CUSTOM_OUTBOUND || profile?.configType == EConfigType.WIREGUARD) {
            // Never permanently blacklist local Direct DPI Shield or WARP shields
            return
        }
        blacklistedGuids.add(guid)
        MmkvManager.encodeSettings("hotaru_evidence_$guid", "")
        val updated = getVerifiedServerGuids().filter { it != guid }
        MmkvManager.encodeSettings(KEY_VERIFIED_GUIDS, JsonUtil.toJson(updated))
        MmkvManager.encodeServerTestDelayMillis(guid, -1L)
    }

    fun markServerFailed(guid: String?) = blacklistServer(guid)

    private val refreshMutex = Mutex()

    /**
     * Silently harvests fresh multi-protocol nodes from the primary encrypted HotaruNet feed
     * plus Iran-accessible jsDelivr aggregator mirrors and Telegram web channels.
     */
    suspend fun refreshSubscriptionSilently(context: Context, force: Boolean = false): Int = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            val now = System.currentTimeMillis()
            val lastHarvest = MmkvManager.decodeSettingsLong(KEY_LAST_HARVEST_MS, 0L)
            val existingCount = MmkvManager.decodeServerList(AppConfig.HOTARU_SUB_ID).size
            val harvestInterval = if (existingCount >= 40) LIVE_HARVEST_HEALTHY_INTERVAL_MS else LIVE_HARVEST_INTERVAL_MS
            if (!force && existingCount >= 20 && (now - lastHarvest) < harvestInterval) {
                return@withLock 0
            }

            val feedTimeoutMs = if (force && existingCount > 0) 3200L else 6000L
            val primaryUris = withTimeoutOrNull(feedTimeoutMs) {
                fetchPrimaryFeed()
            } ?: return@withLock 0

            if (primaryUris.isEmpty()) {
                return@withLock 0
            }

            val combined = LinkedHashSet<String>(MAX_POOL_SERVERS)
            val ipHostCounts = HashMap<String, Int>()
            fun addUris(list: List<String>) {
                for (rawUri in list) {
                    if (combined.size >= MAX_POOL_SERVERS) break
                    val uri = rawUri.trim()
                    if (!isCleanHarvestUri(uri)) continue
                    val (hostKey, _) = extractHostAndRemarksForHarvest(uri)
                    if (hostKey.isNotEmpty()) {
                        val count = ipHostCounts[hostKey] ?: 0
                        if (count >= 2) continue
                        ipHostCounts[hostKey] = count + 1
                    }
                    combined.add(uri)
                }
            }

            addUris(primaryUris)

            if (combined.isEmpty()) return@withLock 0

            val batchText = combined.joinToString("\n")
            val feedHash = sha256Hex(batchText)
            val prevFeedHash = MmkvManager.decodeSettingsString(KEY_LAST_FEED_SHA256)
            if (!prevFeedHash.isNullOrEmpty() && prevFeedHash == feedHash && existingCount >= 20) {
                val ts = System.currentTimeMillis()
                MmkvManager.encodeSettings(KEY_LAST_HARVEST_MS, ts)
                MmkvManager.decodeSubscription(AppConfig.HOTARU_SUB_ID)?.let { sub ->
                    sub.lastUpdated = ts
                    MmkvManager.encodeSubscription(AppConfig.HOTARU_SUB_ID, sub)
                }
                Log.i(
                    LOG_TAG,
                    "refreshSubscriptionSilently: feed unchanged (hash=${feedHash.take(8)}, nodes=$existingCount), skipped MMKV rewrite"
                )
                return@withLock 0
            }

            val shouldReplace = existingCount == 0 || combined.size >= 5
            // importBatchConfig parses first and saveServerProfiles publishes only this subscription's index.
            // Never delete the last usable pool before parsing/import succeeds.
            val (importedCount, _) = AngConfigManager.importBatchConfig(batchText, AppConfig.HOTARU_SUB_ID, !shouldReplace)
            if (importedCount > 0) {
                runCatching { HotaruDirectDpiManager.ensureDirectDpiProfile() }
                runCatching { HotaruWarpManager.ensureDedicatedWarpProfile() }
                val ts = System.currentTimeMillis()
                MmkvManager.encodeSettings(KEY_LAST_FEED_SHA256, feedHash)
                MmkvManager.encodeSettings(KEY_LAST_HARVEST_MS, ts)
                MmkvManager.decodeSubscription(AppConfig.HOTARU_SUB_ID)?.let { sub ->
                    sub.lastUpdated = ts
                    MmkvManager.encodeSubscription(AppConfig.HOTARU_SUB_ID, sub)
                }
                Log.i(
                    LOG_TAG,
                    "refreshSubscriptionSilently: imported=$importedCount primary=${primaryUris.size} hash=${feedHash.take(8)}"
                )
            }
            importedCount
        }
    }

    internal fun computeCandidatePotentialScore(
        profile: ProfileItem,
        prebenchKbps: Long,
        isEuro: Boolean = false,
        tier: TransportTier,
        port: Int,
        isFragile: Boolean,
        networkKind: NetworkKind = NetworkKind.OTHER,
        downstreamKbps: Int = 0,
        recentNetBps: Long = 0L,
        recentNetLatencyMs: Long = 0L,
        adaptiveTierBonus: Long = 0L,
    ): Long {
        var score = 0L

        // 1. Empirical bandwidth measured directly on the user's current network
        val userNetKbps = (recentNetBps / 1000L).coerceAtLeast(0L)
        if (userNetKbps >= 64L) {
            // Proven fast on user's active network: dominates candidate ranking
            score += 8000L + (userNetKbps * 16L).coerceIn(0L, 32000L)
        } else if (recentNetBps > 0L) {
            // Measured on user's network and found below 64 KB/s threshold: penalize throttled node
            score -= 4000L
        }

        // 2. Mild latency tie-breaker (high-throughput distant regions must never be starved for having higher ping)
        if (recentNetLatencyMs in 1L..3000L) {
            score += (1200L - recentNetLatencyMs).coerceIn(-150L, 150L)
        }

        // 2b. Per-user-network adaptive transport-tier learning
        score += adaptiveTierBonus

        // 3. Scanner-verified server download speed (KB/s) — primary pre-probe indicator regardless of country/ping
        val effectivePrebench = when (tier) {
            TransportTier.WARP_DEDICATED -> 575L
            TransportTier.DIRECT_DPI_SHIELD -> 0L
            else -> prebenchKbps.coerceAtLeast(0L)
        }
        score += (effectivePrebench * 6L).coerceIn(0L, 12000L)

        // 4. Network-Adaptive Anti-Censorship Transport Tier Priority
        score += when (tier) {
            TransportTier.REALITY_VISION -> 4200L
            TransportTier.XHTTP -> if (networkKind == NetworkKind.CELLULAR) 3800L else 3000L
            TransportTier.WS_TLS -> if (networkKind == NetworkKind.CELLULAR) 3400L else 2500L
            TransportTier.HYSTERIA2 -> if (networkKind == NetworkKind.WIFI || networkKind == NetworkKind.ETHERNET || downstreamKbps >= 15000) 3600L else 2400L
            TransportTier.WARP_DEDICATED -> 3000L
            TransportTier.GRPC -> 1600L
            TransportTier.MULTI_PROTO -> 400L
            TransportTier.DIRECT_DPI_SHIELD -> -25000L
        }

        // 5. Port priority (Cellular DPI heavily favors TCP/UDP 443)
        score += when (port) {
            443 -> if (networkKind == NetworkKind.CELLULAR) 1200L else 700L
            8443, 2053, 2083, 2087, 2096 -> -100L
            else -> if (networkKind == NetworkKind.CELLULAR) -700L else -400L
        }

        // 6. Config health
        if (profile.configType == EConfigType.SHADOWSOCKS) score -= 1800L
        if (isFragile) score -= 20000L

        return score
    }

    internal fun scoreVerifiedCandidate(
        cand: CandidatePotential,
        delay: Long,
        bytes: Int,
    ): VerifiedCandidate {
        val empiricalBps = if (bytes > 0) {
            HotaruHealthPolicy.bytesPerSecond(bytes, delay)
        } else {
            0L
        }
        // Server throughput (Bps) dominates; ping only acts as a mild tie-breaker between comparable speeds.
        val throughputScore = empiricalBps / 100L
        val throughputLatencyRatio = (empiricalBps / delay.coerceAtLeast(1L)).coerceAtMost(empiricalBps / 250L)
        val prebenchHint = (cand.prebenchKbps / 15L).coerceIn(0L, 500L)
        val payloadBonus = if (bytes >= HotaruHealthPolicy.MIN_PAYLOAD_BYTES) 5000L else -5000L
        val delayTieBreaker = (delay / 5L).coerceAtMost(500L)
        val finalScore = payloadBonus + throughputScore + (throughputLatencyRatio / 2L) + cand.tier.speedBonusMs + prebenchHint - delayTieBreaker
        return VerifiedCandidate(cand.guid, cand.profile, delay, cand.prebenchKbps, cand.tier, finalScore)
    }

    internal fun buildStratifiedCandidateBatch(queue: List<CandidatePotential>, targetSize: Int = 10): List<CandidatePotential> {
        if (queue.size <= targetSize) return queue
        val selected = LinkedHashSet<CandidatePotential>(targetSize)
        val byTier = queue.groupBy { it.tier }

        val priorityTiers = listOf(
            TransportTier.REALITY_VISION,
            TransportTier.HYSTERIA2,
            TransportTier.XHTTP,
            TransportTier.WS_TLS,
            TransportTier.GRPC,
            TransportTier.MULTI_PROTO,
        )
        var depth = 0
        while (selected.size < targetSize) {
            var addedInRound = false
            for (tier in priorityTiers) {
                if (selected.size >= targetSize) break
                val list = byTier[tier] ?: continue
                if (depth < list.size) {
                    selected.add(list[depth])
                    addedInRound = true
                }
            }
            if (!addedInRound) break
            depth++
        }

        for (cand in queue) {
            if (selected.size >= targetSize) break
            selected.add(cand)
        }
        return selected.toList()
    }

    /**
     * High-speed parallel finding and testing pipeline:
     * 1. Loads verified servers from GitHub and ranks them for the user's active network (Wi-Fi/Cellular/ISP), speed, and bandwidth.
     * 2. Performs a fast parallel reachability + RTT sweep across stratified candidates on the user's internet connection.
     * 3. Benchmarks stratified reachable candidates in parallel with a real 32 KiB download payload over Xray.
     * 4. Selects the server with the highest empirical throughput/bandwidth and lowest latency on the user's network.
     */
    suspend fun selectBestServer(
        context: Context,
        forceRescan: Boolean = false,
    ): SelectionResult? = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        scanMutex.withLock {
            val networkProfile = optimizeForClientNetwork(appContext)
            val scanNetwork = evidenceNetwork(appContext)
            val lastWinnerGuid = MmkvManager.decodeSettingsString(AppConfig.PREF_HOTARU_LAST_WINNER_GUID).orEmpty()

            // Stage 0: Warm Fast-Reconnect (<2s) — probe last winner + top 2 cached verified nodes
            // with a real 32 KiB payload (>=4000ms timeout) while refreshing cloud mirrors in the background.
            if (!forceRescan) {
                val existingGuids = loadCandidateGuids()
                if (existingGuids.isNotEmpty()) {
                    val warmGuids = (listOf(lastWinnerGuid) + getVerifiedServerGuids())
                        .distinct()
                        .filter { it.isNotEmpty() && it in existingGuids && it !in blacklistedGuids && MmkvManager.decodeServerConfig(it) != null }
                        .take(3)

                    if (warmGuids.isNotEmpty()) {
                        _connectStageFlow.value = ConnectStage.WARM_RECONNECT
                        feedScope.launch {
                            runCatching { refreshSubscriptionSilently(appContext, force = false) }
                        }
                        runCatching { CoreNativeManager.initCoreEnv(appContext) }
                        val warmBatch = buildCandidatePotentials(
                            appContext = appContext,
                            guids = warmGuids,
                            networkProfile = networkProfile,
                            scanNetwork = scanNetwork,
                            nowMs = System.currentTimeMillis(),
                            skipFragile = false,
                        )
                        val warmWinners = verifyCandidateBatch(
                            appContext = appContext,
                            batch = warmBatch,
                            scanNetwork = scanNetwork,
                            payloadUrl = STAGE3_PAYLOAD_URL,
                            timeoutMs = 4500L,
                            stageLabel = "WarmReconnect",
                        )
                        val fastWarm = warmWinners.filter {
                            HotaruHealthPolicy.isFast(PAYLOAD_PROBE_BYTES, it.delayMs)
                        }
                        if (fastWarm.isNotEmpty() && (scanNetwork.isEmpty() || evidenceNetwork(appContext) == scanNetwork)) {
                            _connectStageFlow.value = ConnectStage.LOCKING_TUNNEL
                            delay(220L)
                            return@withLock commitVerifiedSelection(fastWarm, networkProfile, "WarmReconnect")
                        }
                    }
                }
            }

            _connectStageFlow.value = ConnectStage.UPDATING_NODES
            val stage1StartMs = android.os.SystemClock.elapsedRealtime()
            // Always perform a bounded fast feed sync first on cold Connect so fresh cloud nodes enter immediately
            runCatching {
                withTimeoutOrNull(3500L) {
                    refreshSubscriptionSilently(appContext, force = true)
                }
            }
            val stage1Remain = 850L - (android.os.SystemClock.elapsedRealtime() - stage1StartMs)
            if (stage1Remain > 0L) delay(stage1Remain)
            // Ensure dedicated Cloudflare WARP is initialized in pool
            runCatching { HotaruWarpManager.ensureDedicatedWarpProfile() }
            var guids = loadCandidateGuids()
            if (guids.isEmpty()) {
                SettingsManager.initApp(appContext)
                guids = loadCandidateGuids()
            }
            if (guids.isEmpty()) {
                runCatching { refreshSubscriptionSilently(appContext, force = true) }
                guids = loadCandidateGuids()
            }
            if (guids.isEmpty()) {
                Log.w(LOG_TAG, "selectBestServer aborted: no servers available")
                _connectStageFlow.value = ConnectStage.IDLE
                return@withLock null
            }

            runCatching { CoreNativeManager.initCoreEnv(appContext) }
                .onFailure { LogUtil.w(AppConfig.TAG, "CoreNativeManager.initCoreEnv failed during smart scan", it) }

            val minKeep = guids.size.coerceAtMost(3)
            val nonBlacklistedGuids = guids.filter { it !in blacklistedGuids }.let {
                if (it.size < minKeep) {
                    blacklistedGuids.clear()
                    guids
                } else it
            }

            val nowMs = System.currentTimeMillis()
            if (!forceRescan && scanNetwork.isNotEmpty()) {
                val lastScanMs = MmkvManager.decodeSettingsLong(KEY_LAST_SCAN_MS, 0L)
                if (nowMs - lastScanMs in 0L..90_000L) {
                    val cachedVerified = getVerifiedServerGuids().filter { it in nonBlacklistedGuids }
                    val currentSelected = MmkvManager.getSelectServer()?.takeIf { it in cachedVerified } ?: cachedVerified.firstOrNull()
                    if (currentSelected != null) {
                        val cachedProfile = MmkvManager.decodeServerConfig(currentSelected)
                        val cachedEv = evidence(appContext, currentSelected)
                        val cachedQuality = quality(appContext, currentSelected, nowMs)
                        if (cachedProfile != null &&
                            cachedEv != null &&
                            cachedEv.network == scanNetwork &&
                            cachedQuality.latestFast &&
                            (MmkvManager.decodeServerAffiliationInfo(currentSelected)?.testDelayMillis ?: 0L) > 0L
                        ) {
                            _connectStageFlow.value = ConnectStage.TCP_SWEEP
                            val host = cachedProfile.server?.trim().orEmpty()
                            val port = cachedProfile.serverPort?.toIntOrNull() ?: AppConfig.DEFAULT_PORT
                            val tcpReachable = isUdpOrComplexType(cachedProfile.configType) ||
                                SpeedtestManager.socketConnectTime(host, port, 900) >= 0L
                            if (tcpReachable) {
                                delay(400L)
                                val delayMs = cachedEv.latencyMs.takeIf { it > 0L } ?: cachedEv.elapsedMs.coerceAtLeast(1L)
                                Log.i(LOG_TAG, "selectBestServer: reusing fresh verified winner guid=$currentSelected delay=${delayMs}ms net=${networkProfile.kind.name}")
                                MmkvManager.setSelectServer(currentSelected)
                                MmkvManager.encodeSettings(AppConfig.PREF_HOTARU_LAST_WINNER_GUID, currentSelected)
                                _connectStageFlow.value = ConnectStage.LOCKING_TUNNEL
                                delay(350L)
                                return@withLock SelectionResult(
                                    guid = currentSelected,
                                    remarks = cachedProfile.remarks.orEmpty(),
                                    delayMs = delayMs,
                                    verifiedGuids = cachedVerified,
                                    networkProfile = networkProfile,
                                )
                            }
                        }
                    }
                }
            }

            // Stage 1: Score all candidates by user's active network profile + run concurrent TCP sweep across protocols
            _connectStageFlow.value = ConnectStage.TCP_SWEEP
            val stage2StartMs = android.os.SystemClock.elapsedRealtime()
            val rankedQueue = rankTcpReachableCandidates(
                appContext = appContext,
                guids = nonBlacklistedGuids,
                networkProfile = networkProfile,
                scanNetwork = scanNetwork,
                nowMs = nowMs,
            )
            val stage2Remain = 850L - (android.os.SystemClock.elapsedRealtime() - stage2StartMs)
            if (stage2Remain > 0L) delay(stage2Remain)

            Log.i(
                LOG_TAG,
                "selectBestServer: totalGuids=${guids.size} pool=${nonBlacklistedGuids.size} ranked=${rankedQueue.size} net=${networkProfile.kind.name} downKbps=${networkProfile.downstreamKbps}"
            )

            val attemptedGuids = HashSet<String>(32)
            val batchStages = listOf(
                Triple("Batch 1", STAGE3_PAYLOAD_URL, ConnectStage.SPEED_TEST_WAVE_1),
                Triple("Batch 2", STAGE3_FALLBACK_PAYLOAD_URL, ConnectStage.SPEED_TEST_WAVE_2),
                Triple("Batch 3", STAGE3_PAYLOAD_URL, ConnectStage.SPEED_TEST_WAVE_3),
            )
            for ((stageName, payloadUrl, stageEnum) in batchStages) {
                val batch = buildStratifiedCandidateBatch(
                    rankedQueue.filter { it.guid !in attemptedGuids },
                    targetSize = STAGE3_CANDIDATES,
                )
                if (batch.isEmpty()) break
                batch.forEach { attemptedGuids.add(it.guid) }

                _connectStageFlow.value = stageEnum
                val verified = verifyCandidateBatch(
                    appContext = appContext,
                    batch = batch,
                    scanNetwork = scanNetwork,
                    payloadUrl = payloadUrl,
                    timeoutMs = STAGE3_TIMEOUT_MS,
                    stageLabel = stageName,
                )
                if (scanNetwork.isNotEmpty() && evidenceNetwork(appContext) != scanNetwork) {
                    _connectStageFlow.value = ConnectStage.IDLE
                    return@withLock null
                }
                if (verified.isNotEmpty()) {
                    _connectStageFlow.value = ConnectStage.LOCKING_TUNNEL
                    delay(950L)
                    return@withLock commitVerifiedSelection(verified, networkProfile, stageName)
                }
                Log.w(LOG_TAG, "$stageName failed; advancing to next stratified verification wave")
            }

            // Cloud Recovery: Refresh subscription silently and run TCP + stratified protocol verification on new pool
            Log.w(LOG_TAG, "Top waves failed; refreshing subscription silently from cloud feed")
            _connectStageFlow.value = ConnectStage.CLOUD_RECOVERY
            runCatching { refreshSubscriptionSilently(appContext, force = true) }
            val freshGuids = loadCandidateGuids().filter { candGuid ->
                candGuid !in blacklistedGuids && candGuid !in attemptedGuids
            }
            if (freshGuids.isNotEmpty()) {
                val freshRanked = rankTcpReachableCandidates(
                    appContext = appContext,
                    guids = freshGuids,
                    networkProfile = networkProfile,
                    scanNetwork = scanNetwork,
                    nowMs = System.currentTimeMillis(),
                )
                val freshBatch = buildStratifiedCandidateBatch(freshRanked, targetSize = STAGE3_CANDIDATES)
                val freshWinners = verifyCandidateBatch(
                    appContext = appContext,
                    batch = freshBatch,
                    scanNetwork = scanNetwork,
                    payloadUrl = STAGE3_PAYLOAD_URL,
                    timeoutMs = STAGE3_TIMEOUT_MS,
                    stageLabel = "CloudRecovery",
                )
                if (scanNetwork.isNotEmpty() && evidenceNetwork(appContext) != scanNetwork) {
                    _connectStageFlow.value = ConnectStage.IDLE
                    return@withLock null
                }
                if (freshWinners.isNotEmpty()) {
                    _connectStageFlow.value = ConnectStage.LOCKING_TUNNEL
                    delay(950L)
                    return@withLock commitVerifiedSelection(freshWinners, networkProfile, "CloudRecovery")
                }
            }

            // 4. Multi-tier Fallback: Dedicated Clean WARP
            Log.w(LOG_TAG, "VPS candidates failed; verifying Dedicated Clean WARP fallback")

            val warpGuid = HotaruWarpManager.ensureDedicatedWarpProfile()
            if (warpGuid != null) {
                val bestEndpoint = HotaruWarpManager.scanAndSelectBestAnycastEndpoint(appContext, warpGuid, testTimeoutMs = 5000L)
                val (delay, bytes) = quickVerifyPayloadGuid(appContext, warpGuid, timeoutMs = 6500L, payloadUrl = STAGE3_PAYLOAD_URL)
                if (delay > 0L && bytes >= HotaruHealthPolicy.MIN_PAYLOAD_BYTES) {
                    val profile = MmkvManager.decodeServerConfig(warpGuid)
                    persistVerifiedGuids(listOf(warpGuid), System.currentTimeMillis())
                    MmkvManager.setSelectServer(warpGuid)
                    MmkvManager.encodeSettings(AppConfig.PREF_HOTARU_LAST_WINNER_GUID, warpGuid)
                    MmkvManager.encodeServerTestDelayMillis(warpGuid, delay)
                    Log.i(LOG_TAG, "selectBestServer: Clean WARP fallback WON on ${bestEndpoint.first}:${bestEndpoint.second}: delay=${delay}ms bytes=$bytes")
                    _connectStageFlow.value = ConnectStage.LOCKING_TUNNEL
                    delay(950L)
                    return@withLock SelectionResult(
                        guid = warpGuid,
                        remarks = profile?.remarks ?: "HotaruNet #000 | Cloudflare WARP",
                        delayMs = delay,
                        verifiedGuids = listOf(warpGuid),
                        networkProfile = networkProfile,
                    )
                }
            }

            // Never fall back to unverified or dead servers!
            Log.w(LOG_TAG, "selectBestServer: all candidates failed live verification")
            _connectStageFlow.value = ConnectStage.IDLE
            return@withLock null
        }
    }

    private fun buildCandidatePotentials(
        appContext: Context,
        guids: List<String>,
        networkProfile: ClientNetworkProfile,
        scanNetwork: String,
        nowMs: Long,
        skipFragile: Boolean = true,
    ): List<CandidatePotential> = guids.mapNotNull { guid ->
        val profile = MmkvManager.decodeServerConfig(guid) ?: return@mapNotNull null
        val remarks = profile.remarks.orEmpty().trim()
        val isFragile = isBogusOrFragileConfig(profile)
        if (skipFragile && isFragile && guids.size > 20) return@mapNotNull null
        val prebenchKbps = if (profile.configType == EConfigType.SHADOWSOCKS) 0L else extractPrebenchmarkedKilobytesPerSecond(remarks)
        val tier = classifyTransportTier(profile)
        val port = profile.serverPort?.toIntOrNull() ?: AppConfig.DEFAULT_PORT
        val hostKey = normalizeHostOnly(profile)
        val ev = evidence(appContext, guid)
        val recentNetBps = HotaruHealthPolicy.throughputQuality(ev?.let { sampleHistory(it, nowMs) }, nowMs).conservativeBytesPerSecond
        val recentNetLatencyMs = ev?.latencyMs?.takeIf { HotaruHealthPolicy.isFresh(ev.latencyMeasuredAt, nowMs) } ?: 0L
        val adaptiveTierBonus = computeAdaptiveTierBonus(tierStat(scanNetwork, tier), nowMs)
        val potentialScore = computeCandidatePotentialScore(
            profile = profile,
            prebenchKbps = prebenchKbps,
            isEuro = false,
            tier = tier,
            port = port,
            isFragile = isFragile,
            networkKind = networkProfile.kind,
            downstreamKbps = networkProfile.downstreamKbps,
            recentNetBps = recentNetBps,
            recentNetLatencyMs = recentNetLatencyMs,
            adaptiveTierBonus = adaptiveTierBonus,
        )
        CandidatePotential(guid, profile, prebenchKbps, false, tier, port, hostKey, potentialScore)
    }

    private suspend fun rankTcpReachableCandidates(
        appContext: Context,
        guids: List<String>,
        networkProfile: ClientNetworkProfile,
        scanNetwork: String,
        nowMs: Long,
    ): List<CandidatePotential> {
        val candidatePool = buildCandidatePotentials(appContext, guids, networkProfile, scanNetwork, nowMs)
        val preRankedQueue = candidatePool.sortedByDescending { it.potentialScore }
        val stage1Targets = buildStratifiedCandidateBatch(preRankedQueue, targetSize = STAGE1_TOP_CANDIDATES)
        val stage1Sem = Semaphore(STAGE1_CONCURRENCY)
        val reachableTop = coroutineScope {
            stage1Targets.map { cand ->
                async(Dispatchers.IO) {
                    if (isUdpOrComplexType(cand.profile.configType) || cand.profile.alpn?.startsWith("h3") == true) {
                        cand
                    } else {
                        stage1Sem.withPermit {
                            val host = cand.profile.server?.trim().orEmpty()
                            val tcpMs = if (host.isNotEmpty()) {
                                SpeedtestManager.socketConnectTime(host, cand.port, STAGE1_TIMEOUT_MS)
                            } else -1L
                            if (tcpMs >= 0L) {
                                // Only apply a gentle latency tie-breaker so fast distant servers (US/Asia) keep top priority
                                cand.copy(potentialScore = cand.potentialScore - (tcpMs / 10L).coerceAtMost(150L))
                            } else {
                                null
                            }
                        }
                    }
                }
            }.awaitAll().filterNotNull().sortedByDescending { it.potentialScore }
        }
        val stage1Guids = stage1Targets.map { it.guid }.toSet()
        return if (reachableTop.isNotEmpty()) {
            reachableTop + preRankedQueue.filter { it.guid !in stage1Guids }
        } else {
            preRankedQueue
        }
    }

    private suspend fun verifyCandidateBatch(
        appContext: Context,
        batch: List<CandidatePotential>,
        scanNetwork: String,
        payloadUrl: String,
        timeoutMs: Long,
        stageLabel: String,
    ): List<VerifiedCandidate> {
        if (batch.isEmpty()) return emptyList()
        val verified = coroutineScope {
            batch.map { cand ->
                async(Dispatchers.IO) {
                    val (delay, bytes) = quickVerifyPayloadGuid(
                        context = appContext,
                        guid = cand.guid,
                        timeoutMs = timeoutMs,
                        payloadUrl = payloadUrl,
                    )
                    if (delay > 0L) {
                        recordLatency(appContext, cand.guid, delay, scanNetwork)
                        if (bytes > 0) {
                            recordThroughput(appContext, cand.guid, bytes, delay)
                            recordTierOutcome(scanNetwork, cand.tier, HotaruHealthPolicy.bytesPerSecond(bytes, delay), true)
                        }
                        scoreVerifiedCandidate(cand, delay, bytes)
                    } else {
                        Log.w(LOG_TAG, "$stageLabel candidate failed live verification: guid=${cand.guid} remarks='${cand.profile.remarks}'")
                        if (hasPhysicalInternet(appContext)) {
                            recordTierOutcome(scanNetwork, cand.tier, 0L, false)
                            blacklistServer(cand.guid)
                        }
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }
        if (verified.isEmpty()) return emptyList()
        val qualityOrder = routeQualityComparator(appContext, verified.map { it.guid })
        return verified.sortedWith { a, b ->
            qualityOrder.compare(a.guid, b.guid).takeIf { it != 0 }
                ?: b.finalScore.compareTo(a.finalScore).takeIf { it != 0 }
                ?: a.delayMs.compareTo(b.delayMs)
        }
    }

    private fun commitVerifiedSelection(
        rankedWinners: List<VerifiedCandidate>,
        networkProfile: ClientNetworkProfile,
        stageLabel: String,
    ): SelectionResult {
        val winner = rankedWinners.first()
        val verifiedGuids = rankedWinners.map { it.guid }
        persistVerifiedGuids(verifiedGuids, System.currentTimeMillis())
        MmkvManager.setSelectServer(winner.guid)
        MmkvManager.encodeSettings(AppConfig.PREF_HOTARU_LAST_WINNER_GUID, winner.guid)
        MmkvManager.encodeServerTestDelayMillis(winner.guid, winner.delayMs)
        Log.i(
            LOG_TAG,
            "selectBestServer $stageLabel WINNER selected: guid=${winner.guid} delay=${winner.delayMs}ms score=${winner.finalScore} remarks='${winner.profile.remarks}' verifiedCount=${verifiedGuids.size}"
        )
        return SelectionResult(
            guid = winner.guid,
            remarks = winner.profile.remarks.orEmpty(),
            delayMs = winner.delayMs,
            verifiedGuids = verifiedGuids,
            networkProfile = networkProfile,
        )
    }

    /**
     * Switches the selected server to the next verified healthy node in multi-process MMKV.
     * Reuses TCP Stage 1 + stratified protocol probing so daemon failover handles blocked ports cleanly.
     */
    fun switchToNextVerifiedServer(context: Context, failedGuid: String?): SelectionResult? {
        val appContext = context.applicationContext
        if (!hasPhysicalInternet(appContext)) {
            Log.w(LOG_TAG, "switchToNextVerifiedServer paused: no physical internet connectivity")
            return null
        }
        val networkProfile = optimizeForClientNetwork(appContext)
        blacklistServer(failedGuid)

        val scanNet = evidenceNetwork(appContext)
        val nowMs = System.currentTimeMillis()
        val allGuids = loadCandidateGuids()
        val rawVerified = getVerifiedServerGuids().filter { guid ->
            guid in allGuids && MmkvManager.decodeServerConfig(guid) != null
        }
        val storedVerified = rawVerified.filter { it != failedGuid && it !in blacklistedGuids }

        var verifiedNextGuid: String? = null
        var verifiedDelay = -1L

        val storedBatch = buildCandidatePotentials(
            appContext = appContext,
            guids = storedVerified.take(6),
            networkProfile = networkProfile,
            scanNetwork = scanNet,
            nowMs = nowMs,
            skipFragile = false,
        )
        if (storedBatch.isNotEmpty()) {
            val storedWinners = runCatching {
                runBlocking(Dispatchers.IO) {
                    verifyCandidateBatch(
                        appContext = appContext,
                        batch = storedBatch,
                        scanNetwork = scanNet,
                        payloadUrl = STAGE3_PAYLOAD_URL,
                        timeoutMs = STAGE3_TIMEOUT_MS,
                        stageLabel = "FailoverStored",
                    )
                }
            }.getOrDefault(emptyList())
            storedWinners.firstOrNull()?.let {
                verifiedNextGuid = it.guid
                verifiedDelay = it.delayMs
            }
        }

        if (verifiedNextGuid == null) {
            fun probeStratifiedCandidatePool(candidateGuids: List<String>, stageLabel: String): VerifiedCandidate? {
                if (candidateGuids.isEmpty()) return null
                return runCatching {
                    runBlocking(Dispatchers.IO) {
                        val ranked = rankTcpReachableCandidates(
                            appContext = appContext,
                            guids = candidateGuids,
                            networkProfile = networkProfile,
                            scanNetwork = scanNet,
                            nowMs = nowMs,
                        )
                        val attempted = HashSet<String>(STAGE3_CANDIDATES * 2)
                        val waves = listOf(
                            stageLabel to STAGE3_PAYLOAD_URL,
                            "${stageLabel}Wave2" to STAGE3_FALLBACK_PAYLOAD_URL,
                        )
                        for ((waveLabel, waveUrl) in waves) {
                            val batch = buildStratifiedCandidateBatch(
                                ranked.filter { it.guid !in attempted },
                                targetSize = STAGE3_CANDIDATES,
                            )
                            if (batch.isEmpty()) break
                            batch.forEach { attempted.add(it.guid) }
                            val winners = verifyCandidateBatch(
                                appContext = appContext,
                                batch = batch,
                                scanNetwork = scanNet,
                                payloadUrl = waveUrl,
                                timeoutMs = STAGE3_TIMEOUT_MS,
                                stageLabel = waveLabel,
                            )
                            winners.firstOrNull()?.let { return@runBlocking it }
                        }
                        null
                    }
                }.getOrNull()
            }

            val testedStored = storedVerified.take(6).toSet()
            val nonBlacklisted = allGuids.filter { guid ->
                guid != failedGuid && guid !in blacklistedGuids && guid !in testedStored
            }
            var bestFallback = probeStratifiedCandidatePool(nonBlacklisted, "FailoverStratified")

            if (bestFallback == null) {
                // Pool may have rotated during long active sessions; refresh encrypted feed (respecting harvest cooldown)
                val imported = runCatching {
                    runBlocking(Dispatchers.IO) {
                        refreshSubscriptionSilently(appContext, force = false)
                    }
                }.getOrDefault(0)
                if (imported > 0) {
                    val refreshedGuids = loadCandidateGuids().filter { guid ->
                        guid != failedGuid && guid !in blacklistedGuids
                    }
                    bestFallback = probeStratifiedCandidatePool(refreshedGuids, "FailoverRefreshed")
                }
            }

            if (bestFallback == null) {
                val refreshedAll = loadCandidateGuids()
                val remainingNonBlacklisted = refreshedAll.count { it != failedGuid && it !in blacklistedGuids }
                if (remainingNonBlacklisted == 0 && refreshedAll.size > 1) {
                    // Long session exhausted every node once; allow previously blacklisted nodes a fresh chance except failedGuid
                    blacklistedGuids.clear()
                    blacklistServer(failedGuid)
                    bestFallback = probeStratifiedCandidatePool(
                        refreshedAll.filter { it != failedGuid },
                        "FailoverBlacklistReset",
                    )
                }
            }

            if (bestFallback != null) {
                verifiedNextGuid = bestFallback.guid
                verifiedDelay = bestFallback.delayMs
            } else {
                // Multi-tier auto-failover: Try Dedicated Clean WARP with real payload throughput verification
                val warpGuid = HotaruWarpManager.ensureDedicatedWarpProfileSync()
                if (warpGuid != null && warpGuid != failedGuid) {
                    val (ping, bytes) = runCatching {
                        runBlocking(Dispatchers.IO) {
                            quickVerifyPayloadGuid(appContext, warpGuid, timeoutMs = 6500L, payloadUrl = STAGE3_PAYLOAD_URL)
                        }
                    }.getOrDefault(-1L to 0)
                    if (ping > 0L && bytes >= HotaruHealthPolicy.MIN_PAYLOAD_BYTES) {
                        verifiedNextGuid = warpGuid
                        verifiedDelay = ping
                    }
                }
            }
        }

        if (verifiedNextGuid == null) {
            Log.w(LOG_TAG, "switchToNextVerifiedServer failed: no alternative servers")
            return null
        }

        val remainingQueue = buildRemainingVerifiedQueue(
            rawVerified = rawVerified,
            failedGuid = failedGuid,
            nextGuid = verifiedNextGuid,
            blacklisted = blacklistedGuids,
        )
        persistVerifiedGuids(remainingQueue, System.currentTimeMillis())
        MmkvManager.setSelectServer(verifiedNextGuid)
        MmkvManager.encodeSettings(AppConfig.PREF_HOTARU_LAST_WINNER_GUID, verifiedNextGuid)
        if (verifiedDelay > 0L) {
            MmkvManager.encodeServerTestDelayMillis(verifiedNextGuid, verifiedDelay)
        }

        val profile = MmkvManager.decodeServerConfig(verifiedNextGuid)
        Log.i(
            LOG_TAG,
            "Auto-failover switched failedGuid=$failedGuid -> nextGuid=$verifiedNextGuid remarks='${profile?.remarks}' delay=${verifiedDelay}ms remainingVerified=${remainingQueue.size}"
        )
        return SelectionResult(
            guid = verifiedNextGuid,
            remarks = profile?.remarks ?: "HotaruNet Node",
            delayMs = verifiedDelay,
            verifiedGuids = remainingQueue,
            networkProfile = networkProfile,
        )
    }

    internal fun buildRemainingVerifiedQueue(
        rawVerified: List<String>,
        failedGuid: String?,
        nextGuid: String,
        blacklisted: Set<String>,
    ): List<String> = (rawVerified.filter { it != failedGuid && it != nextGuid && it !in blacklisted } + nextGuid).distinct()

    /**
     * Proves real download throughput (~32 KiB CDN JS asset over Go connection reuse) and returns (delayMs, bytes).
     * Single bounded 4.2s attempt per candidate so dead nodes never stall parallel batches for multiple timeouts.
     */
    private suspend fun quickVerifyPayloadGuid(
        context: Context,
        guid: String,
        timeoutMs: Long = 4500L,
        payloadUrl: String = STAGE3_PAYLOAD_URL,
    ): Pair<Long, Int> = withContext(Dispatchers.IO) {
        val effectiveTimeout = timeoutMs.coerceAtLeast(4000L)
        val cdnDelay = quickVerifyGuid(context, guid, payloadUrl, timeoutMs = effectiveTimeout)
        if (cdnDelay > 0L) return@withContext cdnDelay to PAYLOAD_PROBE_BYTES

        -1L to 0
    }

    /**
     * Bounded live test of a candidate server directly on the user's connection.
     * Verifies that the server can establish a tunnel and return 200/204 from target URL.
     */
    suspend fun quickVerifyGuid(
        context: Context,
        guid: String,
        testUrl: String = STAGE3_PRIMARY_URL,
        timeoutMs: Long = 4500L,
    ): Long = withContext(Dispatchers.IO) {
        runCatching { CoreNativeManager.initCoreEnv(context) }
        val configResult = CoreConfigManager.getV2rayConfig4Speedtest(context, guid)
        if (!configResult.status) return@withContext -1L
        val profile = MmkvManager.decodeServerConfig(guid) ?: return@withContext -1L
        val effectiveTimeout = timeoutMs.coerceAtLeast(4000L)
        val primaryDelay = RealPingExecutionLimiter.run(profile.configType) {
            withTimeoutOrNull(effectiveTimeout) {
                suspendCancellableCoroutine { cont ->
                    val future = CompletableFuture.supplyAsync({
                        CoreNativeManager.measureOutboundDelay(configResult.content, testUrl)
                    }, xrayExecutor)
                    future.whenComplete { result, err ->
                        if (cont.isActive) {
                            if (err != null) {
                                Log.w(LOG_TAG, "quickVerifyGuid error for $guid url=$testUrl: ${err.message}")
                                cont.resume(-1L)
                            } else {
                                cont.resume(result ?: -1L)
                            }
                        }
                    }
                    cont.invokeOnCancellation { future.cancel(true) }
                }
            }
        } ?: -1L

        if (primaryDelay > 0L) return@withContext primaryDelay

        // Robust fallback: if primary URL timed out or failed, try fallback 204
        if (testUrl == STAGE3_PRIMARY_URL && STAGE3_FALLBACK_URL != STAGE3_PRIMARY_URL) {
            return@withContext RealPingExecutionLimiter.run(profile.configType) {
                withTimeoutOrNull(effectiveTimeout.coerceAtMost(6000L)) {
                    suspendCancellableCoroutine { cont ->
                        val future = CompletableFuture.supplyAsync({
                            CoreNativeManager.measureOutboundDelay(configResult.content, STAGE3_FALLBACK_URL)
                        }, xrayExecutor)
                        future.whenComplete { result, err ->
                            if (cont.isActive) {
                                if (err != null) {
                                    Log.w(LOG_TAG, "quickVerifyGuid fallback error for $guid url=$STAGE3_FALLBACK_URL: ${err.message}")
                                    cont.resume(-1L)
                                } else {
                                    cont.resume(result ?: -1L)
                                }
                            }
                        }
                        cont.invokeOnCancellation { future.cancel(true) }
                    }
                }
            } ?: -1L
        }

        return@withContext -1L
    }

    fun quickVerifyGuidSync(
        context: Context,
        guid: String,
        testUrl: String = STAGE3_PRIMARY_URL,
        timeoutMs: Long = 5000L,
    ): Long {
        return runCatching {
            runBlocking(Dispatchers.IO) {
                quickVerifyGuid(context, guid, testUrl, timeoutMs)
            }
        }.getOrDefault(-1L)
    }

    fun getVerifiedServerGuids(): List<String> {
        val raw = MmkvManager.decodeSettingsString(KEY_VERIFIED_GUIDS)
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            JsonUtil.fromJsonSafe(raw, Array<String>::class.java)?.toList().orEmpty()
        }.getOrDefault(emptyList())
    }

    fun getLastNetworkProfileSummary(isPersian: Boolean): String {
        val cached = MmkvManager.decodeSettingsString(if (isPersian) KEY_NET_SUMMARY_FA else KEY_NET_SUMMARY_EN)
        if (!cached.isNullOrBlank()) return cached
        val kindName = MmkvManager.decodeSettingsString(KEY_ACTIVE_NET_KIND)
        val kind = NetworkKind.entries.firstOrNull { it.name == kindName } ?: NetworkKind.OTHER
        val mtu = MmkvManager.decodeSettingsString(AppConfig.PREF_VPN_MTU)?.toIntOrNull() ?: kind.mtu
        return if (isPersian) {
            "${kind.labelFa} • MTU $mtu"
        } else {
            "${kind.labelEn} • MTU $mtu"
        }
    }

    private fun loadCandidateGuids(): List<String> {
        val hotaruList = MmkvManager.decodeServerList(AppConfig.HOTARU_SUB_ID)
        return if (hotaruList.isNotEmpty()) hotaruList else MmkvManager.decodeAllServerList()
    }

    private fun persistVerifiedGuids(guids: List<String>, scanTimeMs: Long) {
        MmkvManager.encodeSettings(KEY_VERIFIED_GUIDS, JsonUtil.toJson(guids))
        if (scanTimeMs > 0L) {
            MmkvManager.encodeSettings(KEY_LAST_SCAN_MS, scanTimeMs)
        }
    }

    private suspend fun runStage1Sweep(
        items: List<Pair<String, ProfileItem>>,
        timeoutMs: Int,
    ): List<Stage1Candidate> = coroutineScope {
        val semaphore = Semaphore(STAGE1_CONCURRENCY)
        items.map { (guid, profile) ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val host = profile.server?.trim().orEmpty()
                    val port = profile.serverPort?.toIntOrNull() ?: AppConfig.DEFAULT_PORT
                    val tcpMs = SpeedtestManager.socketConnectTime(host, port, timeoutMs)
                    if (tcpMs >= 0L) {
                        buildStage1Candidate(guid, profile, tcpMs)
                    } else {
                        MmkvManager.encodeServerTestDelayMillis(guid, -1L)
                        null
                    }
                }
            }
        }.awaitAll()
            .filterNotNull()
            .sortedBy { it.stage1Score }
    }

    private suspend fun runStage2Verify(
        context: Context,
        candidates: List<Stage1Candidate>,
        testUrl: String,
        timeoutMs: Long = STAGE2_TIMEOUT_MS,
        persistDelay: Boolean = true,
    ): List<Stage2Candidate> = coroutineScope {
        val semaphore = Semaphore(STAGE2_CONCURRENCY)
        candidates.map { candidate ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val configResult = CoreConfigManager.getV2rayConfig4Speedtest(context, candidate.guid)
                    if (!configResult.status) {
                        Log.w(LOG_TAG, "Stage 2 getV2rayConfig4Speedtest failed for ${candidate.guid}: ${configResult.errorMessage}")
                        if (persistDelay) MmkvManager.encodeServerTestDelayMillis(candidate.guid, -1L)
                        return@withPermit null
                    }
                    val delay = RealPingExecutionLimiter.run(candidate.configType) {
                        withTimeoutOrNull(timeoutMs.coerceAtLeast(4000L)) {
                            suspendCancellableCoroutine { cont ->
                                val future = CompletableFuture.supplyAsync({
                                    CoreNativeManager.measureOutboundDelay(configResult.content, testUrl)
                                }, xrayExecutor)
                                future.whenComplete { result, err ->
                                    if (err != null && err !is java.util.concurrent.CancellationException) {
                                        Log.w(LOG_TAG, "Stage 2 measureOutboundDelay threw for ${candidate.guid}", err)
                                    }
                                    if (cont.isActive) {
                                        cont.resume(result ?: -1L)
                                    }
                                }
                                cont.invokeOnCancellation { future.cancel(true) }
                            }
                        }
                    } ?: -1L

                    if (delay > 0L) {
                        if (persistDelay) MmkvManager.encodeServerTestDelayMillis(candidate.guid, delay)
                        val fragilePenalty = if (candidate.isFragile) FRAGILE_PENALTY_MS else 0L
                        val score = (delay + (candidate.tcpMs / 5L) + candidate.scoreAdjustment + fragilePenalty)
                            .coerceAtLeast(1L)
                        Stage2Candidate(candidate = candidate, realDelayMs = delay, score = score)
                    } else {
                        if (persistDelay) MmkvManager.encodeServerTestDelayMillis(candidate.guid, -1L)
                        null
                    }
                }
            }
        }.awaitAll()
            .filterNotNull()
            .sortedBy { it.score }
    }

    internal fun extractPrebenchmarkedKilobytesPerSecond(remarks: String?): Long {
        val match = PREBENCH_KBPS_REGEX.find(remarks.orEmpty()) ?: return 0L
        val value = match.groupValues[1].toDoubleOrNull()?.takeIf { it.isFinite() } ?: return 0L
        val unit = match.groupValues[2].uppercase()
        val scale = when (unit) {
            "MIB" -> 1024.0
            "MB" -> 1000.0
            "KIB" -> 1.024
            else -> 1.0
        }
        // Legacy remarks are only a capped scan-order hint, never current throughput evidence.
        return (value * scale).coerceIn(0.0, 10_000_000.0).toLong()
    }

    private fun buildStage1Candidate(guid: String, profile: ProfileItem, tcpMs: Long): Stage1Candidate {
        val port = profile.serverPort?.toIntOrNull() ?: AppConfig.DEFAULT_PORT
        val isFragile = isBogusOrFragileConfig(profile)
        val tier = classifyTransportTier(profile)
        val transportBonus = when (tier) {
            TransportTier.REALITY_VISION -> 400L
            TransportTier.WARP_DEDICATED -> 350L
            TransportTier.HYSTERIA2 -> 260L
            TransportTier.WS_TLS -> 150L
            TransportTier.XHTTP -> 110L
            TransportTier.GRPC -> 80L
            TransportTier.MULTI_PROTO -> -200L
            TransportTier.DIRECT_DPI_SHIELD -> -1000L
        }
        val remarks = profile.remarks.orEmpty().trim()
        val primaryHubBonus = 0L

        val isTurbo = remarks.contains("Turbo", ignoreCase = true)
        val turboBonus = if (isTurbo) 80L else 0L

        val portBonus = when (port) {
            443 -> 150L
            8443, 2053, 2083, 2087, 2093, 2096 -> -180L
            else -> -250L
        }
        val ssPenalty = if (profile.configType == EConfigType.SHADOWSOCKS) 250L else 0L

        val prebenchKbps = if (profile.configType == EConfigType.SHADOWSOCKS) 0L else extractPrebenchmarkedKilobytesPerSecond(remarks)
        val prebenchBonus = (prebenchKbps / 30L).coerceIn(0L, 50L)

        val stage1Score = tcpMs +
            (if (isFragile) FRAGILE_PENALTY_MS else 0L) +
            ssPenalty -
            portBonus -
            transportBonus - primaryHubBonus - turboBonus - prebenchBonus
        return Stage1Candidate(
            guid = guid,
            serverKey = normalizeServerKey(profile),
            hostKey = normalizeHostOnly(profile),
            tier = tier,
            configType = profile.configType,
            security = profile.security,
            port = port,
            remarks = profile.remarks,
            tcpMs = tcpMs,
            isFragile = isFragile,
            stage1Score = stage1Score,
            scoreAdjustment = computeProtocolAdjustment(profile.configType, profile.security, port) - transportBonus,
        )
    }

    internal fun classifyTransportTier(profile: ProfileItem): TransportTier {
        if (profile.configType == EConfigType.CUSTOM_OUTBOUND ||
            profile.remarks.orEmpty().contains("Direct DPI", ignoreCase = true) ||
            profile.remarks.orEmpty().contains("Zapret", ignoreCase = true)
        ) {
            return TransportTier.DIRECT_DPI_SHIELD
        }
        if (profile.configType == EConfigType.WIREGUARD || profile.remarks.orEmpty().contains("WARP", ignoreCase = true)) {
            return TransportTier.WARP_DEDICATED
        }
        if (profile.configType == EConfigType.HYSTERIA2 || profile.configType == EConfigType.HYSTERIA) {
            return TransportTier.HYSTERIA2
        }
        val net = profile.network?.trim()?.lowercase().orEmpty()
        val sec = profile.security?.trim()?.lowercase().orEmpty()
        val isReality = sec.contains("reality")
        return when {
            net == "xhttp" || net == "splithttp" -> TransportTier.XHTTP
            net == "grpc" -> TransportTier.GRPC
            isReality -> TransportTier.REALITY_VISION
            (net == "ws" || net == "httpupgrade") && (sec == AppConfig.TLS || sec == "tls") -> TransportTier.WS_TLS
            profile.configType == EConfigType.TROJAN && (sec == AppConfig.TLS || sec == "tls") -> TransportTier.WS_TLS
            profile.configType == EConfigType.SHADOWSOCKS -> TransportTier.MULTI_PROTO
            sec == AppConfig.TLS || sec == "tls" -> TransportTier.WS_TLS
            else -> TransportTier.MULTI_PROTO
        }
    }

    private fun deduplicateByServer(candidates: List<Stage1Candidate>): List<Stage1Candidate> {
        val hostCounts = HashMap<String, Int>()
        return candidates
            .groupBy { it.serverKey }
            .values
            .mapNotNull { group -> group.minByOrNull { it.stage1Score } }
            .sortedBy { it.stage1Score }
            .filter { cand ->
                val count = hostCounts[cand.hostKey] ?: 0
                if (count >= 2) {
                    false
                } else {
                    hostCounts[cand.hostKey] = count + 1
                    true
                }
            }
    }

    private fun selectStratifiedTopCandidates(candidates: List<Stage1Candidate>, limit: Int): List<Stage1Candidate> {
        if (candidates.size <= limit) return candidates
        val byTier = candidates.groupBy { it.tier }
        val picked = ArrayList<Stage1Candidate>(limit)
        val usedGuids = HashSet<String>(limit)

        for (tier in TransportTier.entries) {
            val tierList = byTier[tier].orEmpty()
            var taken = 0
            for (cand in tierList) {
                if (picked.size >= limit || taken >= tier.quota) break
                if (usedGuids.add(cand.guid)) {
                    picked.add(cand)
                    taken++
                }
            }
        }

        if (picked.size < limit) {
            for (cand in candidates) {
                if (picked.size >= limit) break
                if (usedGuids.add(cand.guid)) {
                    picked.add(cand)
                }
            }
        }
        return picked
    }

    private fun selectStratifiedStage2Targets(stage2Winners: List<Stage2Candidate>, limit: Int): List<Stage1Candidate> {
        if (stage2Winners.size <= limit) return stage2Winners.map { it.candidate }
        val picked = ArrayList<Stage1Candidate>(limit)
        val usedGuids = HashSet<String>(limit)

        // Pick top winner from each active tier first so Stage 3 benchmarks all working protocols
        for (tier in TransportTier.entries) {
            val firstInTier = stage2Winners.firstOrNull { it.candidate.tier == tier && it.candidate.guid !in usedGuids }
            if (firstInTier != null && usedGuids.add(firstInTier.candidate.guid)) {
                picked.add(firstInTier.candidate)
                if (picked.size >= limit) return picked
            }
        }

        for (winner in stage2Winners) {
            if (picked.size >= limit) break
            if (usedGuids.add(winner.candidate.guid)) {
                picked.add(winner.candidate)
            }
        }
        return picked
    }

    private fun computeStage3CompositeScore(
        winner: Stage2Candidate,
        googleDelayMs: Long?,
        ytDelayMs: Long?,
    ): Long {
        val gMs = googleDelayMs ?: (winner.realDelayMs + 900L)
        val yMs = ytDelayMs ?: (gMs + 450L)
        val baseLatency = ((winner.realDelayMs * 45L) + (gMs * 35L) + (winner.candidate.tcpMs * 20L)) / 100L
        // Penalize nodes that pass burst 204 fast on first probe but spike >2.2x on subsequent probe
        val burstPenalty = if (googleDelayMs != null && googleDelayMs > 900L && googleDelayMs > (winner.realDelayMs * 22L) / 10L) {
            500L
        } else {
            0L
        }
        val multiPlatformPenalty = if (ytDelayMs != null && ytDelayMs > 1800L) 250L else 0L
        val tierBonus = winner.candidate.tier.speedBonusMs
        val remarks = winner.candidate.remarks.orEmpty()
        val serverHost = winner.candidate.serverKey.lowercase()

        val isTurbo = remarks.contains("Turbo", ignoreCase = true) ||
            remarks.contains("zapkey", ignoreCase = true) ||
            winner.candidate.serverKey.contains("zapkey.ir", ignoreCase = true)
        val turboBonus = if (isTurbo) 80L else 0L
        val prebenchKbps = if (winner.candidate.configType == EConfigType.SHADOWSOCKS) 0L else extractPrebenchmarkedKilobytesPerSecond(remarks)
        val speedBonus = (prebenchKbps / 30L).coerceIn(0L, 50L)

        return (baseLatency + (yMs / 6L) + burstPenalty + multiPlatformPenalty - tierBonus - turboBonus - speedBonus).coerceAtLeast(1L)
    }

    internal fun feedAuthHeaders(): String {
        // The feed is a private repository; this read-only token is the only way in.
        // HttpUtil parses this as a JSON object of header names to values.
        return buildString {
            append("{\"Authorization\":\"Bearer ").append(AppConfig.HOTARU_FEED_TOKEN).append("\",")
            append("\"Accept\":\"application/vnd.github+json\",")
            append("\"X-GitHub-Api-Version\":\"").append(AppConfig.HOTARU_FEED_API_VERSION).append("\"}")
        }
    }

    /**
     * Extracts the encrypted blob from a GitHub Contents API response. The API
     * wraps file bytes in a JSON envelope with base64 `content`; a plain blob is
     * also accepted so the same path works if the endpoint ever returns raw text.
     */
    internal fun extractApiContent(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("{")) return trimmed
        val content = runCatching {
            JsonUtil.fromJsonSafe(trimmed, Map::class.java)?.get("content")?.toString()
        }.getOrNull()
        return content?.trim().orEmpty()
    }

    private suspend fun fetchPrimaryFeed(): List<String> {
        val mirrors = HotaruCrypto.getFeedMirrors()
        val requests = buildList {
            // 1. GitHub Contents API with auth token (real-time commit state; never suffers CDN purge lag)
            add(
                "api" to UrlContentRequest(
                    url = AppConfig.HOTARU_SUB_URL,
                    timeout = 2600,
                    requestHeaders = feedAuthHeaders(),
                )
            )
            // 2-4. Direct raw GitHub & jsDelivr CDN mirrors (resolved transiently from native vault)
            mirrors.forEachIndexed { idx, mirrorUrl ->
                add(
                    "mirror-$idx" to UrlContentRequest(
                        url = mirrorUrl,
                        timeout = if (idx == 0) 2600 else 2800,
                    )
                )
            }
            // 5. GitHub Contents API (public accept header fallback)
            add(
                "api-pub" to UrlContentRequest(
                    url = AppConfig.HOTARU_SUB_URL,
                    timeout = 2800,
                    requestHeaders = "{\"Accept\":\"application/vnd.github+json\"}",
                )
            )
        }

        val ch = Channel<Pair<String, List<String>>?>(capacity = requests.size)
        val won = AtomicBoolean(false)
        val jobs = requests.mapIndexed { idx, (label, request) ->
            val staggerMs = if (idx <= 1) 0L else 350L
            feedScope.launch {
                if (staggerMs > 0L) delay(staggerMs)
                if (won.get()) {
                    ch.trySend(null)
                    return@launch
                }
                val list = runCatching {
                    val raw = extractApiContent(HttpUtil.getUrlContentWithUserAgent(request))
                    if (raw.isEmpty()) return@runCatching emptyList<String>()
                    val decoded = HotaruCrypto.decryptSubscriptionPayload(raw)
                        ?: HotaruCrypto.decryptSubscriptionPayload(Utils.decode(raw))
                        ?: Utils.decode(raw)
                    extractConfigUris(decoded)
                }.getOrDefault(emptyList())
                if (list.isNotEmpty() && won.compareAndSet(false, true)) {
                    ch.trySend(label to list)
                } else {
                    ch.trySend(null)
                }
            }
        }

        try {
            repeat(requests.size) {
                val res = ch.receive()
                if (res != null) {
                    jobs.forEach { it.cancel() }
                    Log.i(LOG_TAG, "fetchPrimaryFeed: retrieved ${res.second.size} nodes via ${res.first}")
                    return res.second
                }
            }
        } finally {
            won.set(true)
            jobs.forEach { it.cancel() }
            ch.close()
        }
        return emptyList()
    }

    private fun fetchMirrorOrTelegramFeed(url: String, isTelegram: Boolean, limit: Int): List<String> {
        return runCatching {
            val raw = HttpUtil.getUrlContent(UrlContentRequest(url = url, timeout = 4500)) ?: return emptyList()
            val text = if (isTelegram) {
                raw.replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
            } else if (!raw.contains("://")) {
                Utils.decode(raw)
            } else {
                raw
            }
            extractConfigUris(text).take(limit)
        }.getOrDefault(emptyList())
    }

    private fun extractConfigUris(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        return URI_REGEX.findAll(text)
            .map { it.value.trimEnd('.', ',', ';', ')', ']', '}') }
            .distinct()
            .toList()
    }

    private const val EXCLUDED_SELF_HOST_SHA256 =
        "2a73476777f0872d13ab8f79474ce5a394045371058f18381b145f7589fd0f99"
    private const val EXCLUDED_SELF_NET24_SHA256 =
        "c58b3c1a9b6300df2123948e4a193dbba47d7a1de8ace6a9afa5826eed953564"

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun isExcludedSelfHostOrSubnet(rawHost: String?): Boolean {
        val host = rawHost?.trim()?.lowercase()?.removePrefix("[")?.removeSuffix("]").orEmpty()
        if (host.isEmpty()) return false
        if (sha256Hex("hotaru-self-host:$host") == EXCLUDED_SELF_HOST_SHA256) return true
        val octets = host.split('.')
        if (octets.size == 4 && octets.all { it.toIntOrNull() in 0..255 }) {
            val prefix24 = "${octets[0]}.${octets[1]}.${octets[2]}"
            if (sha256Hex("hotaru-self-net:$prefix24") == EXCLUDED_SELF_NET24_SHA256) return true
        }
        return false
    }

    internal fun isCleanHarvestUri(uri: String): Boolean {
        val lower = uri.lowercase()
        if (lower.contains("v2raynplus") || lower.contains("127.0.0.1") || lower.contains("@[")) {
            return false
        }
        val (parsedHost, _) = extractHostAndRemarksForHarvest(uri)
        if (isExcludedSelfHostOrSubnet(parsedHost)) {
            return false
        }
        if (lower.contains("security=reality") && lower.contains("allowinsecure=1")) {
            return false
        }
        return true
    }

    internal fun extractHostAndRemarksForHarvest(uri: String): Pair<String, String> {
        if (uri.startsWith("vmess://", ignoreCase = true)) {
            val rawB64 = uri.substring(8).substringBefore('#').trim()
            val decodedJson = Utils.decode(rawB64).ifEmpty {
                runCatching {
                    val padded = rawB64 + "=".repeat((4 - rawB64.length % 4) % 4)
                    String(java.util.Base64.getMimeDecoder().decode(padded), Charsets.UTF_8)
                }.getOrDefault("")
            }
            val map = runCatching { JsonUtil.fromJsonSafe(decodedJson, Map::class.java) }.getOrNull()
            val add = map?.get("add")?.toString()?.trim()?.lowercase().orEmpty()
            val ps = map?.get("ps")?.toString()?.trim().orEmpty()
            return add to ps
        }
        val host = extractHostForDedup(uri)
        val remarks = if (uri.contains('#')) {
            runCatching { java.net.URLDecoder.decode(uri.substringAfter('#'), "UTF-8") }.getOrDefault("")
        } else {
            ""
        }
        return host to remarks
    }

    private fun extractHostForDedup(uri: String): String {
        val atIdx = uri.indexOf('@')
        if (atIdx < 0) return ""
        val afterAt = uri.substring(atIdx + 1)
        if (afterAt.startsWith("[")) {
            val closeBracket = afterAt.indexOf(']')
            if (closeBracket > 0) {
                return afterAt.substring(1, closeBracket).trim().lowercase()
            }
        }
        val endIdx = afterAt.indexOfAny(charArrayOf(':', '?', '#', '/'))
        val host = if (endIdx >= 0) afterAt.substring(0, endIdx) else afterAt
        return host.trim().lowercase()
    }

    internal fun normalizeHostOnly(profile: ProfileItem): String {
        val server = profile.server?.trim()?.lowercase().orEmpty()
        val ip = server.removePrefix("[").removeSuffix("]")
        val parts = ip.split(".")
        if (parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) {
            return "${parts[0]}.${parts[1]}.${parts[2]}.0/24"
        }
        return server
    }

    private fun normalizeServerKey(profile: ProfileItem): String {
        val host = profile.server?.trim()?.lowercase().orEmpty()
        val port = profile.serverPort?.trim().orEmpty()
        val net = profile.network?.trim()?.lowercase().orEmpty()
        return "$host:$port:$net"
    }

    private fun isBogusOrFragileConfig(profile: ProfileItem): Boolean {
        if (profile.configType == EConfigType.CUSTOM_OUTBOUND) return false
        if (profile.configType == EConfigType.WIREGUARD) return false
        val srv = profile.server?.trim().orEmpty()
        if (isExcludedSelfHostOrSubnet(srv) || srv.contains(':')) return true
        if (profile.insecure == true) return true
        val remarks = profile.remarks.orEmpty().trim()
        if (remarks.contains("@") || remarks.contains("Channel", ignoreCase = true) || remarks.contains("join", ignoreCase = true)) {
            return true
        }
        val sni = profile.sni?.trim().orEmpty()
        val host = profile.host?.trim().orEmpty()
        if (sni.contains("v2raynplus", ignoreCase = true) ||
            host.contains("v2raynplus", ignoreCase = true)
        ) {
            return true
        }
        if (profile.security.equals(AppConfig.REALITY, ignoreCase = true) && !sni.contains('.')) {
            return true
        }
        return false
    }

    private fun isUdpOrComplexType(type: EConfigType): Boolean {
        return type == EConfigType.WIREGUARD ||
            type == EConfigType.HYSTERIA2 ||
            type == EConfigType.HYSTERIA ||
            type == EConfigType.MASQUE ||
            type.isComplexType()
    }

    private fun computeProtocolAdjustment(type: EConfigType, security: String?, port: Int): Long {
        var adjustment = 0L
        if (type == EConfigType.VLESS || type == EConfigType.TROJAN) {
            adjustment -= 10L
        }
        if (security.equals(AppConfig.REALITY, ignoreCase = true) || security.equals(AppConfig.TLS, ignoreCase = true)) {
            adjustment -= 10L
        }
        if (port == AppConfig.DEFAULT_PORT) {
            adjustment -= 5L
        }
        return adjustment
    }
}
