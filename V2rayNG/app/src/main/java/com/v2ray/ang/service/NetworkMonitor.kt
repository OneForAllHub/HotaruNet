package com.v2ray.ang.service

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.v2ray.ang.AppConfig
import com.v2ray.ang.extension.delay
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Watches the network that carries the tunnel and reports topology changes.
 *
 * Handles both make-before-break handovers (Cellular <-> Wi-Fi) and offline -> online
 * restorations where Android may reassign the same [Network] handle after minutes or hours.
 *
 * Only used from Android P and above, see CoreServiceManager.startNetworkMonitor().
 * [onHandover] is invoked on a background thread after the debounce window and may block.
 */
class NetworkMonitor(
    private val connectivity: ConnectivityManager,
    private val onUnderlyingNetworksChanged: (Array<Network>?) -> Unit,
    private val onHandover: () -> Unit,
    private val onNetworkLost: () -> Unit = {},
) {
    internal companion object {
        const val HANDOVER_DEBOUNCE_MS = 1200L

        internal fun shouldTriggerRecovery(
            hadNetworkLoss: Boolean,
            lastStableUpstreamId: String?,
            currentNetworkId: String,
        ): Boolean = hadNetworkLoss || (lastStableUpstreamId != null && lastStableUpstreamId != currentNetworkId)
    }

    @Volatile
    private var upstream: Network? = null
    @Volatile
    private var lastStableUpstreamId: String? = null
    @Volatile
    private var hadNetworkLoss: Boolean = false
    private var handoverJob: Job? = null
    private var registered = false

    /**
     * Unfortunately registerDefaultNetworkCallback is going to return our VPN interface:
     * https://android.googlesource.com/platform/frameworks/base/+/dda156ab0c5d66ad82bdcf76cda07cbc0a9c8a2e
     *
     * This makes doing a requestNetwork with REQUEST necessary so that we don't get ALL possible networks that
     * satisfies default network capabilities but only THE default network. Unfortunately we need to have
     * android.permission.CHANGE_NETWORK_STATE to be able to call requestNetwork.
     *
     * Source: https://android.googlesource.com/platform/frameworks/base/+/2df4c7d/services/core/java/com/android/server/ConnectivityService.java#887
     */
    private val request by lazy {
        NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build()
    }

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val networkId = network.toString()
            val trigger = shouldTriggerRecovery(hadNetworkLoss, lastStableUpstreamId, networkId)
            upstream = network
            lastStableUpstreamId = networkId
            hadNetworkLoss = false
            onUnderlyingNetworksChanged(arrayOf(network))
            if (trigger) {
                scheduleHandover(network)
            }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val capsReady = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            if (capsReady && (hadNetworkLoss || upstream == null)) {
                val networkId = network.toString()
                val trigger = shouldTriggerRecovery(hadNetworkLoss, lastStableUpstreamId, networkId)
                upstream = network
                lastStableUpstreamId = networkId
                hadNetworkLoss = false
                onUnderlyingNetworksChanged(arrayOf(network))
                if (trigger) {
                    scheduleHandover(network)
                    return
                }
            }
            if (upstream == null || upstream == network) {
                upstream = network
                onUnderlyingNetworksChanged(arrayOf(network))
            }
        }

        override fun onLost(network: Network) {
            if (upstream != null && upstream != network) {
                return
            }
            upstream = null
            hadNetworkLoss = true
            handoverJob?.cancel()
            handoverJob = null
            onUnderlyingNetworksChanged(null)
            onNetworkLost()
        }
    }

    /**
     * Starts watching. Safe to call more than once, only the first call registers.
     */
    fun register() {
        if (registered) return
        try {
            connectivity.requestNetwork(request, callback)
            registered = true
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "NetworkMonitor: Failed to request network", e)
        }
    }

    /**
     * Stops watching and drops the tracked state. Safe to call more than once.
     */
    fun unregister() {
        handoverJob?.cancel()
        handoverJob = null
        upstream = null
        lastStableUpstreamId = null
        hadNetworkLoss = false
        if (!registered) return
        registered = false
        try {
            connectivity.unregisterNetworkCallback(callback)
        } catch (e: Exception) {
            LogUtil.w(AppConfig.TAG, "NetworkMonitor: Failed to unregister callback", e)
        }
    }

    private fun scheduleHandover(network: Network) {
        LogUtil.i(AppConfig.TAG, "NetworkMonitor: Upstream recovered/changed -> $network")
        handoverJob?.cancel()
        handoverJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                delay(HANDOVER_DEBOUNCE_MS)
                onHandover()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "NetworkMonitor: Failed to handle upstream change", e)
            }
        }
    }
}
