package com.v2ray.ang.handler

import android.os.Build
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.dto.CheckUpdateResult
import com.v2ray.ang.dto.GitHubRelease
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.extension.concatUrl
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UpdateCheckerManager {
    private const val FALLBACK_REPO_URL = "https://github.com/OneForAllHub/HotaruNet"
    private const val FALLBACK_API_URL = "https://api.github.com/repos/OneForAllHub/HotaruNet/releases"

    suspend fun checkForUpdateSilently(includePreRelease: Boolean = false): CheckUpdateResult =
        withContext(Dispatchers.IO) {
            runCatching {
                checkForUpdate(includePreRelease)
            }.getOrElse {
                CheckUpdateResult(hasUpdate = false, checkedOk = false)
            }
        }

    suspend fun checkForUpdate(includePreRelease: Boolean = false): CheckUpdateResult = withContext(Dispatchers.IO) {
        val apiBase = AppConfig.APP_API_URL.ifBlank { FALLBACK_API_URL }
        val url = if (includePreRelease) {
            apiBase
        } else {
            apiBase.concatUrl("latest")
        }

        val proxyUsername = SettingsManager.getSocksUsername()
        val proxyPassword = SettingsManager.getSocksPassword()

        var response = HttpUtil.getUrlContent(
            UrlContentRequest(
                url = url,
                timeout = 5000
            )
        )
        if (response.isNullOrEmpty()) {
            val httpPort = SettingsManager.getHttpPort()
            response = HttpUtil.getUrlContent(
                UrlContentRequest(
                    url = url,
                    timeout = 5000,
                    httpPort = httpPort,
                    proxyUsername = proxyUsername,
                    proxyPassword = proxyPassword
                )
            )
                ?: throw IllegalStateException("Failed to get response")
        }

        parseUpdateResponse(response, includePreRelease)
    }

    internal fun parseUpdateResponse(
        response: String,
        includePreRelease: Boolean = false,
        currentVersion: String = BuildConfig.VERSION_NAME,
    ): CheckUpdateResult {
        val latestRelease = if (includePreRelease) {
            JsonUtil.fromJsonSafe(response, Array<GitHubRelease>::class.java)
                ?.firstOrNull()
                ?: return CheckUpdateResult(hasUpdate = false, checkedOk = true)
        } else {
            JsonUtil.fromJsonSafe(response, GitHubRelease::class.java)
        }
        if (latestRelease == null || latestRelease.tagName.isBlank()) {
            return CheckUpdateResult(hasUpdate = false, checkedOk = true)
        }

        val latestVersion = normalizeVersionTag(latestRelease.tagName)
        val repoUrl = AppConfig.APP_URL.ifBlank { FALLBACK_REPO_URL }
        val releasePageUrl = latestRelease.htmlUrl.ifBlank { repoUrl.concatUrl("releases/latest") }

        return if (compareVersions(latestVersion, currentVersion) > 0) {
            val abi = runCatching { Build.SUPPORTED_ABIS.firstOrNull() }.getOrNull().orEmpty().ifBlank { "universal" }
            val downloadUrl = getDownloadUrl(latestRelease, abi) ?: releasePageUrl
            LogUtil.i(AppConfig.TAG, "Found new version: $latestVersion (current: $currentVersion)")
            CheckUpdateResult(
                hasUpdate = true,
                latestVersion = latestVersion,
                releaseNotes = latestRelease.body,
                downloadUrl = downloadUrl,
                releasePageUrl = releasePageUrl,
                isPreRelease = latestRelease.prerelease,
                checkedOk = true,
            )
        } else {
            CheckUpdateResult(hasUpdate = false, checkedOk = true)
        }
    }

    internal fun normalizeVersionTag(raw: String): String {
        val stripped = raw.trim().removePrefix("v").removePrefix("V")
        val match = Regex("""\d+(?:\.\d+)*""").find(stripped)
        return match?.value ?: stripped
    }

    internal fun compareVersions(version1: String, version2: String): Int {
        val v1 = normalizeVersionTag(version1).split(".")
        val v2 = normalizeVersionTag(version2).split(".")

        for (i in 0 until maxOf(v1.size, v2.size)) {
            val num1 = if (i < v1.size) v1[i].takeWhile { it.isDigit() }.toIntOrNull() ?: 0 else 0
            val num2 = if (i < v2.size) v2[i].takeWhile { it.isDigit() }.toIntOrNull() ?: 0 else 0
            if (num1 != num2) return num1 - num2
        }
        return 0
    }

    private fun getDownloadUrl(release: GitHubRelease, abi: String): String? {
        val fDroid = "fdroid"
        val assets = release.assets
        if (assets.isEmpty()) return null

        val isFdroidBuild = BuildConfig.APPLICATION_ID.contains(fDroid, ignoreCase = true)
        val channelFiltered = if (isFdroidBuild) {
            assets.filter { it.name.contains(fDroid, ignoreCase = true) }
        } else {
            assets.filter { !it.name.contains(fDroid, ignoreCase = true) }
        }.ifEmpty { assets }

        val abiAsset = channelFiltered.firstOrNull { it.name.contains(abi, ignoreCase = true) }
            ?: channelFiltered.firstOrNull { it.name.contains("universal", ignoreCase = true) }
            ?: channelFiltered.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

        return abiAsset?.browserDownloadUrl
    }
}
