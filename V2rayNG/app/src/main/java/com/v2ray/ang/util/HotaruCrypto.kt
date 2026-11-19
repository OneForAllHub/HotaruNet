package com.v2ray.ang.util

import android.content.pm.ApplicationInfo
import android.os.Debug
import android.util.Base64
import androidx.annotation.VisibleForTesting
import com.v2ray.ang.AngApplication
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Zero-Leak native-backed credential vault, AES-GCM decryption, and runtime anti-tamper engine.
 *
 * Credentials, private feed URLs, mirrors, and the AES-256 subscription key are stored exclusively
 * inside compiled native code (`libhotaruguard.so`), scrambled per build via `hotaru_vault_gen.h`
 * and revealed transiently over JNI `RegisterNatives` only after anti-debug and hook checks pass.
 */
object HotaruCrypto {

    private const val GCM_NONCE_LENGTH = 12
    private const val GCM_TAG_BITS = 128

    internal const val ID_AES_KEY = 0
    internal const val ID_SUB_URL = 1
    internal const val ID_FEED_TOKEN = 2
    internal const val ID_MIRROR_RAW = 3
    internal const val ID_MIRROR_CDN = 4
    internal const val ID_MIRROR_CDN_MAIN = 5
    internal const val ID_APP_REPO = 6
    internal const val ID_APP_API = 7
    internal const val ID_APP_ISSUES = 8
    internal const val ID_APP_PRIVACY = 9
    internal const val ID_TELEMETRY_URL = 10
    internal const val ID_TELEMETRY_SECRET = 11

    @Volatile
    private var nativeReady: Boolean = false

    @VisibleForTesting
    @Volatile
    internal var testSecretProvider: ((Int) -> ByteArray?)? = null

    init {
        if (isAndroidRuntime()) {
            try {
                System.loadLibrary("hotaruguard")
                nativeReady = true
            } catch (_: Throwable) {
                nativeReady = false
            }
        }
    }

    private fun isAndroidRuntime(): Boolean =
        System.getProperty("java.vm.name")?.contains("Dalvik", ignoreCase = true) == true

    @JvmStatic
    private external fun n0(id: Int): ByteArray?

    private fun resolveBytes(id: Int): ByteArray {
        if (!isAndroidRuntime()) {
            return testSecretProvider?.invoke(id) ?: ByteArray(0)
        }
        if (!nativeReady || isEnvironmentTampered()) {
            return ByteArray(0)
        }
        return try {
            n0(id) ?: ByteArray(0)
        } catch (_: Throwable) {
            ByteArray(0)
        }
    }

    private fun resolveString(id: Int): String {
        val raw = resolveBytes(id)
        if (raw.isEmpty()) return ""
        return try {
            String(raw, Charsets.UTF_8)
        } finally {
            raw.fill(0)
        }
    }

    fun getAppRepoUrl(): String = resolveString(ID_APP_REPO)
    fun getAppApiUrl(): String = resolveString(ID_APP_API)
    fun getAppIssuesUrl(): String = resolveString(ID_APP_ISSUES)
    fun getAppPrivacyUrl(): String = resolveString(ID_APP_PRIVACY)
    fun getFeedToken(): String = resolveString(ID_FEED_TOKEN)
    fun getTelemetryUrl(): String = resolveString(ID_TELEMETRY_URL)
    fun getTelemetrySecret(): String = resolveString(ID_TELEMETRY_SECRET)

    fun getFeedMirrors(): List<String> = listOf(
        resolveString(ID_MIRROR_RAW),
        resolveString(ID_MIRROR_CDN),
        resolveString(ID_MIRROR_CDN_MAIN),
    ).filter { it.isNotEmpty() }

    /**
     * Reconstructs the 32-byte AES key transiently from native memory.
     */
    fun getRawAesKey(): ByteArray = resolveBytes(ID_AES_KEY)

    /**
     * Dynamically reconstructs the private subscription feed URL from native memory.
     */
    fun getSubUrl(): String = resolveString(ID_SUB_URL)

    /**
     * Backward-compatible Base64 representation of the AES key.
     */
    fun getAesKeyB64(): String {
        val raw = getRawAesKey()
        if (raw.isEmpty()) return ""
        return try {
            if (isAndroidRuntime()) {
                Base64.encodeToString(raw, Base64.NO_WRAP)
            } else {
                java.util.Base64.getEncoder().encodeToString(raw)
            }
        } finally {
            raw.fill(0)
        }
    }

    @Volatile
    private var cachedTamperCheck: Boolean? = null
    @Volatile
    private var lastTamperCheckTime: Long = 0L

    /**
     * Anti-tamper and dynamic hook inspection.
     * Detects live debuggers, debuggable-repackaged release builds, or Frida/Xposed hooks
     * injected into the process address space. Cached with throttling for 0ms hot-path cost.
     */
    fun isEnvironmentTampered(): Boolean {
        if (!isAndroidRuntime()) {
            return false
        }
        if (Debug.isDebuggerConnected()) {
            return true
        }
        if (!BuildConfig.DEBUG) {
            val isDebuggableApk = runCatching {
                (AngApplication.application.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            }.getOrDefault(false)
            if (isDebuggableApk) {
                return true
            }
        }
        val now = System.currentTimeMillis()
        val cached = cachedTamperCheck
        if (cached != null && (now - lastTamperCheckTime) < 10000L) {
            return cached
        }

        val result = try {
            val mapsFile = File("/proc/self/maps")
            if (mapsFile.exists() && mapsFile.canRead()) {
                mapsFile.bufferedReader().useLines { lines ->
                    lines.any { line ->
                        line.contains("frida", ignoreCase = true) ||
                            line.contains("xposed", ignoreCase = true) ||
                            line.contains("lsposed", ignoreCase = true) ||
                            line.contains("edxposed", ignoreCase = true) ||
                            line.contains("substrate", ignoreCase = true)
                    }
                }
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
        cachedTamperCheck = result
        lastTamperCheckTime = now
        return result
    }

    private fun decodeBase64(input: String): ByteArray =
        if (isAndroidRuntime()) {
            Base64.decode(input, Base64.DEFAULT)
        } else {
            java.util.Base64.getMimeDecoder().decode(input)
        }

    /**
     * Decrypts a Base64-encoded AES-GCM subscription payload (12-byte nonce || ciphertext+tag).
     * Returns plaintext immediately if [rawText] already looks like a URI subscription list.
     */
    fun decryptSubscriptionPayload(rawText: String?): String? {
        val trimmed = rawText?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        if (trimmed.contains("://")) {
            return trimmed
        }

        if (isEnvironmentTampered()) {
            if (isAndroidRuntime()) {
                LogUtil.w(AppConfig.TAG, "HotaruCrypto: security anomaly detected; operation aborted")
            }
            return null
        }

        var keyBytes: ByteArray? = null
        return try {
            val encryptedBytes = decodeBase64(trimmed)
            if (encryptedBytes.size <= GCM_NONCE_LENGTH + 16) {
                return null
            }
            keyBytes = getRawAesKey()
            if (keyBytes.size != 32) {
                return null
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_BITS, encryptedBytes, 0, GCM_NONCE_LENGTH)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), spec)
            val plainBytes = cipher.doFinal(
                encryptedBytes,
                GCM_NONCE_LENGTH,
                encryptedBytes.size - GCM_NONCE_LENGTH
            )
            String(plainBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            if (isAndroidRuntime()) {
                LogUtil.w(AppConfig.TAG, "HotaruCrypto: payload is not AES-GCM or failed to decrypt: ${e.message}")
            }
            null
        } finally {
            keyBytes?.fill(0)
        }
    }
}
