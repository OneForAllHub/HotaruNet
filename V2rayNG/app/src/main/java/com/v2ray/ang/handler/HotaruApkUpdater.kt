package com.v2ray.ang.handler

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.v2ray.ang.AppConfig
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * In-app APK downloader and PackageInstaller launcher for HotaruNet updates.
 * Prefers routing through the active local SOCKS5 tunnel when VPN is running (so GitHub CDN
 * blocks in censored networks never interrupt downloads), with automatic direct fallback.
 */
object HotaruApkUpdater {

    private val ALLOWED_APK_HOSTS = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "codeload.github.com",
    )

    internal fun isPermittedDownloadUrl(rawUrl: String): Boolean {
        val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase() ?: return false
        return host in ALLOWED_APK_HOSTS || host.endsWith(".githubusercontent.com")
    }

    internal fun isValidApkArchive(file: File): Boolean {
        if (!file.exists() || file.length() < 512 * 1024L) return false
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(4)
                if (raf.read(magic) != 4) return@use false
                // PK\x03\x04 ZIP/APK header
                magic[0] == 0x50.toByte() &&
                    magic[1] == 0x4B.toByte() &&
                    magic[2] == 0x03.toByte() &&
                    magic[3] == 0x04.toByte()
            }
        }.getOrDefault(false)
    }

    private fun buildDirectDownloadClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

    suspend fun downloadUpdateApk(
        context: Context,
        downloadUrl: String,
        isVpnRunning: Boolean,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        if (!isPermittedDownloadUrl(downloadUrl) || !downloadUrl.lowercase().contains(".apk")) {
            return@withContext Result.failure(IllegalArgumentException("Direct APK asset URL unavailable"))
        }

        val partFile = File(context.cacheDir, "HotaruNet_update.apk.part")
        val finalFile = File(context.cacheDir, "HotaruNet_update.apk")

        val socksPort = if (isVpnRunning) runCatching { SettingsManager.getSocksPort() }.getOrDefault(0) else 0
        val clients = buildList {
            if (socksPort > 0) {
                add(HotaruCommunityCounter.createRemoteDnsSocksClient(socksPort, timeoutSec = 25L))
            }
            add(buildDirectDownloadClient())
        }

        var lastError: Throwable? = null
        for (client in clients) {
            coroutineContext.ensureActive()
            val attempt = runCatching {
                if (partFile.exists()) partFile.delete()
                val req = Request.Builder()
                    .url(downloadUrl)
                    .header("User-Agent", "HotaruNet/${com.v2ray.ang.BuildConfig.VERSION_NAME}")
                    .get()
                    .build()

                client.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) {
                        error("HTTP ${res.code}")
                    }
                    val body = res.body
                    val total = body.contentLength()
                    var downloaded = 0L
                    var lastReportedPct = -1
                    val buf = ByteArray(32 * 1024)

                    body.byteStream().use { input ->
                        FileOutputStream(partFile).use { out ->
                            while (true) {
                                coroutineContext.ensureActive()
                                val n = input.read(buf)
                                if (n < 0) break
                                if (n == 0) continue
                                out.write(buf, 0, n)
                                downloaded += n
                                val pct = if (total > 0L) {
                                    ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                                } else {
                                    -1
                                }
                                if (pct != lastReportedPct || downloaded == total) {
                                    lastReportedPct = pct
                                    onProgress(pct.coerceAtLeast(0), downloaded, total)
                                }
                            }
                            out.flush()
                        }
                    }
                }

                if (!isValidApkArchive(partFile)) {
                    partFile.delete()
                    error("Downloaded file is incomplete or not a valid APK")
                }
                if (finalFile.exists()) finalFile.delete()
                check(partFile.renameTo(finalFile)) { "Failed staging downloaded APK" }
                onProgress(100, finalFile.length(), finalFile.length())
                finalFile
            }
            if (attempt.isSuccess) {
                return@withContext attempt
            }
            lastError = attempt.exceptionOrNull()
            LogUtil.w(AppConfig.TAG, "APK download attempt failed: ${lastError?.message}")
        }

        Result.failure(lastError ?: IllegalStateException("APK download failed"))
    }

    /**
     * Launches Android's system PackageInstaller for [apkFile].
     * Returns `true` if the installer intent was launched, or `false` if the user was redirected
     * to grant `ACTION_MANAGE_UNKNOWN_APP_SOURCES` first.
     */
    fun launchApkInstaller(context: Context, apkFile: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val permIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(permIntent)
            return false
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.cache",
            apkFile,
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
        return true
    }
}
