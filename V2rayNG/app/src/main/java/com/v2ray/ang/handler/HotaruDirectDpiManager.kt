package com.v2ray.ang.handler

import android.util.Log
import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import java.util.UUID

/**
 * On-Device Direct DPI Bypass / Zapret Shield Engine (Zero-Hop).
 * Routes direct outbound with calibrated TLS ClientHello fragmentation
 * (length: 100-200, delay: 10-20) and UDP fake noise mask.
 * Delivers unthrottled zero-hop streaming with zero VPS congestion.
 */
object HotaruDirectDpiManager {
    private const val TAG = "HotaruDirectDpi"
    const val MMKV_DIRECT_DPI_GUID = "hotaru_direct_dpi_profile_guid"

    const val DIRECT_DPI_REMARKS = "HotaruNet #000 | ⚡ Direct DPI Shield | Zapret Zero-Hop (Fragment)"

    fun ensureDirectDpiProfile(subId: String = AppConfig.HOTARU_SUB_ID): String? {
        try {
            val outboundJson = """
            {
              "tag": "${AppConfig.TAG_PROXY}",
              "protocol": "freedom",
              "streamSettings": {
                "sockopt": {
                  "domainStrategy": "AsIs"
                },
                "finalmask": {
                  "tcp": [
                    {
                      "type": "fragment",
                      "settings": {
                        "packets": "tlshello",
                        "length": "100-200",
                        "delay": "10-20",
                        "maxSplit": "10"
                      }
                    }
                  ],
                  "udp": [
                    {
                      "type": "noise",
                      "settings": {
                        "noise": [
                          {
                            "rand": "10-20",
                            "delay": "10-16"
                          }
                        ]
                      }
                    }
                  ]
                }
              }
            }
            """.trimIndent()

            var guid = MmkvManager.decodeSettingsString(MMKV_DIRECT_DPI_GUID)
            val existingRaw = if (!guid.isNullOrBlank()) MmkvManager.decodeServerRaw(guid) else null
            val existingProfile = if (!guid.isNullOrBlank()) MmkvManager.decodeServerConfig(guid) else null

            if (!guid.isNullOrBlank() && existingProfile != null && !existingRaw.isNullOrBlank()) {
                if (!existingRaw.contains("\"AsIs\"") || existingProfile.server == "127.0.0.1") {
                    existingProfile.server = "speed.cloudflare.com"
                    existingProfile.remarks = DIRECT_DPI_REMARKS
                    MmkvManager.encodeServerRaw(guid, outboundJson)
                    MmkvManager.encodeServerConfig(guid, existingProfile)
                    Log.i(TAG, "Direct DPI Shield profile upgraded to AsIs and speed.cloudflare.com: guid=$guid")
                }
                val currentList = MmkvManager.decodeServerList(subId)
                if (guid !in currentList) {
                    currentList.add(0, guid)
                    MmkvManager.encodeServerList(currentList, subId)
                }
                return guid
            }

            guid = UUID.randomUUID().toString().replace("-", "")

            val profile = ProfileItem.create(EConfigType.CUSTOM_OUTBOUND).apply {
                remarks = DIRECT_DPI_REMARKS
                server = "speed.cloudflare.com"
                serverPort = "443"
                subscriptionId = subId
            }

            MmkvManager.encodeServerRaw(guid, outboundJson)
            MmkvManager.encodeServerConfig(guid, profile)
            MmkvManager.encodeSettings(MMKV_DIRECT_DPI_GUID, guid)

            val currentList = MmkvManager.decodeServerList(subId)
            if (guid !in currentList) {
                currentList.add(0, guid)
                MmkvManager.encodeServerList(currentList, subId)
            }
            Log.i(TAG, "Direct DPI Shield profile initialized and injected: guid=$guid")
            return guid
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ensure Direct DPI Shield profile", e)
            return null
        }
    }
}
