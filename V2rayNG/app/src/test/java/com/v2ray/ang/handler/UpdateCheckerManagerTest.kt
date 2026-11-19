package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpdateCheckerManagerTest {
    @Test
    fun `compareVersions handles standard and pre-release tags safely`() {
        assertTrue(UpdateCheckerManager.compareVersions("v2.3.43", "2.3.42") > 0)
        assertTrue(UpdateCheckerManager.compareVersions("2.4.0", "2.3.42") > 0)
        assertTrue(UpdateCheckerManager.compareVersions("v2.3.43-beta", "2.3.42") > 0)
        assertEquals(0, UpdateCheckerManager.compareVersions("v2.3.42", "2.3.42"))
        assertTrue(UpdateCheckerManager.compareVersions("2.3.41", "2.3.42") < 0)
    }

    @Test
    fun `parseUpdateResponse detects newer release even with universal-only asset or no assets`() {
        val noAssetJson = """
            {
              "tag_name": "v2.3.43",
              "name": "HotaruNet v2.3.43",
              "body": "Faster REALITY handshake",
              "html_url": "https://github.com/OneForAllHub/HotaruNet/releases/tag/v2.3.43",
              "prerelease": false,
              "assets": []
            }
        """.trimIndent()
        val res = UpdateCheckerManager.parseUpdateResponse(noAssetJson, currentVersion = "2.3.42")
        assertTrue(res.checkedOk)
        assertTrue(res.hasUpdate)
        assertEquals("2.3.43", res.latestVersion)
        assertEquals(
            "https://github.com/OneForAllHub/HotaruNet/releases/tag/v2.3.43",
            res.releasePageUrl,
        )
        assertEquals(
            "https://github.com/OneForAllHub/HotaruNet/releases/tag/v2.3.43",
            res.downloadUrl,
        )

        val universalOnlyJson = """
            {
              "tag_name": "v2.3.44",
              "html_url": "https://github.com/OneForAllHub/HotaruNet/releases/tag/v2.3.44",
              "body": "Notes",
              "assets": [
                {
                  "name": "HotaruNet_2.3.44_universal.apk",
                  "browser_download_url": "https://github.com/OneForAllHub/HotaruNet/releases/download/v2.3.44/HotaruNet_2.3.44_universal.apk"
                }
              ]
            }
        """.trimIndent()
        val univRes = UpdateCheckerManager.parseUpdateResponse(universalOnlyJson, currentVersion = "2.3.42")
        assertTrue(univRes.hasUpdate)
        assertTrue(univRes.downloadUrl!!.endsWith("HotaruNet_2.3.44_universal.apk"))
        assertEquals("https://github.com/OneForAllHub/HotaruNet/releases/tag/v2.3.44", univRes.releasePageUrl)
    }

    @Test
    fun `parseUpdateResponse returns hasUpdate false for same or older version`() {
        val sameJson = """{"tag_name":"v2.3.42","html_url":"https://github.com/OneForAllHub/HotaruNet/releases/tag/v2.3.42","assets":[]}"""
        val res = UpdateCheckerManager.parseUpdateResponse(sameJson, currentVersion = "2.3.42")
        assertTrue(res.checkedOk)
        assertFalse(res.hasUpdate)
    }
}
