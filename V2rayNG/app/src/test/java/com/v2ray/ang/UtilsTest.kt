package com.v2ray.ang

import com.v2ray.ang.util.Utils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class UtilsTest {

    @Test
    fun test_parseInt() {
        assertEquals(Utils.parseInt("1234"), 1234)
    }

    @Test
    fun test_isIpAddress() {
        assertFalse(Utils.isIpAddress("114.113.112.266"))
        assertFalse(Utils.isIpAddress("666.666.666.666"))
        assertFalse(Utils.isIpAddress("256.0.0.0"))
        assertFalse(Utils.isIpAddress("::ffff:127.0.0.0.1"))
        assertFalse(Utils.isIpAddress("baidu.com"))
        assertFalse(Utils.isIpAddress(""))

        assertTrue(Utils.isIpAddress("127.0.0.1"))
        assertTrue(Utils.isIpAddress("127.0.0.1:80"))
        assertTrue(Utils.isIpAddress("0.0.0.0/0"))
        assertTrue(Utils.isIpAddress("::1"))
        assertTrue(Utils.isIpAddress("[::1]:80"))
        assertTrue(Utils.isIpAddress("2605:2700:0:3::4713:93e3"))
        assertTrue(Utils.isIpAddress("[2605:2700:0:3::4713:93e3]:80"))
        assertTrue(Utils.isIpAddress("::ffff:192.168.173.22"))
        assertTrue(Utils.isIpAddress("[::ffff:192.168.173.22]:80"))
        assertTrue(Utils.isIpAddress("1::"))
        assertTrue(Utils.isIpAddress("::"))
        assertTrue(Utils.isIpAddress("::/0"))
        assertTrue(Utils.isIpAddress("10.24.56.0/24"))
        assertTrue(Utils.isIpAddress("2001:4321::1"))
        assertTrue(Utils.isIpAddress("240e:1234:abcd:12::6666"))
        assertTrue(Utils.isIpAddress("240e:1234:abcd:12::/64"))
    }

    @Test
    fun test_IsIpInCidr() {
        assertTrue(Utils.isIpInCidr("192.168.1.1", "192.168.1.0/24"))
        assertTrue(Utils.isIpInCidr("192.168.1.254", "192.168.1.0/24"))
        assertFalse(Utils.isIpInCidr("192.168.2.1", "192.168.1.0/24"))

        assertTrue(Utils.isIpInCidr("10.0.0.0", "10.0.0.0/8"))
        assertTrue(Utils.isIpInCidr("10.255.255.255", "10.0.0.0/8"))
        assertFalse(Utils.isIpInCidr("11.0.0.0", "10.0.0.0/8"))

        assertFalse(Utils.isIpInCidr("invalid-ip", "192.168.1.0/24"))
        assertFalse(Utils.isIpInCidr("192.168.1.1", "invalid-cidr"))
    }

    @Test
    fun test_toTelegramDeepLink() {
        assertEquals(
            "tg://resolve?domain=OneForAllHubBot",
            Utils.toTelegramDeepLink("https://t.me/OneForAllHubBot"),
        )
        assertEquals(
            "tg://resolve?domain=OneForAllHub",
            Utils.toTelegramDeepLink("https://t.me/OneForAllHub/"),
        )
        assertEquals(
            "tg://resolve?domain=OneForAllHubBot&start=help",
            Utils.toTelegramDeepLink("https://t.me/OneForAllHubBot?start=help"),
        )
        assertEquals(
            "tg://resolve?domain=OneForAllHub&post=42",
            Utils.toTelegramDeepLink("https://telegram.me/OneForAllHub/42"),
        )
        assertEquals(
            "tg://resolve?domain=OneForAllHub",
            Utils.toTelegramDeepLink("https://t.me/s/OneForAllHub"),
        )
        assertEquals(
            "tg://join?invite=AbCdEf123",
            Utils.toTelegramDeepLink("https://t.me/+AbCdEf123"),
        )
        assertEquals(
            "tg://join?invite=AbCdEf123",
            Utils.toTelegramDeepLink("https://t.me/joinchat/AbCdEf123"),
        )
        assertEquals(
            "tg://resolve?domain=OneForAllHubBot",
            Utils.toTelegramDeepLink("tg://resolve?domain=OneForAllHubBot"),
        )
        assertNull(Utils.toTelegramDeepLink("https://github.com/OneForAllHub/HotaruNet"))
        assertNull(Utils.toTelegramDeepLink("https://example.com/t.me/OneForAllHubBot"))
        assertNull(Utils.toTelegramDeepLink("https://t.me/"))
        assertNull(Utils.toTelegramDeepLink("https://t.me/share/url?url=https://example.com"))
    }

    @Test
    fun test_resolveUriLaunchPlan_telegramInstalledVsFallback() {
        // 1. Official Telegram or fork handles tg:// via queryIntentActivities
        val singleClientPlan = Utils.resolveUriLaunchPlan(
            uriString = "https://t.me/OneForAllHubBot",
            querySchemeHandlers = { listOf("org.telegram.messenger") },
            isPackageInstalled = { false },
        )
        assertEquals(
            listOf(
                Utils.UriLaunchTarget("tg://resolve?domain=OneForAllHubBot", "org.telegram.messenger"),
                Utils.UriLaunchTarget("https://t.me/OneForAllHubBot", null),
            ),
            singleClientPlan,
        )

        // 2. Multiple Telegram clients installed (lets Android respect user default / chooser, never browser)
        val multiClientPlan = Utils.resolveUriLaunchPlan(
            uriString = "https://t.me/OneForAllHubBot",
            querySchemeHandlers = { listOf("org.telegram.messenger", "ir.ilmili.telegraph") },
            isPackageInstalled = { false },
        )
        assertEquals(
            listOf(
                Utils.UriLaunchTarget("tg://resolve?domain=OneForAllHubBot", null),
                Utils.UriLaunchTarget("https://t.me/OneForAllHubBot", null),
            ),
            multiClientPlan,
        )

        // 3. Query returns empty, fallback detects all installed known Telegram fork packages in priority order
        val packageProbePlan = Utils.resolveUriLaunchPlan(
            uriString = "https://t.me/OneForAllHubBot",
            querySchemeHandlers = { emptyList() },
            isPackageInstalled = { it == "org.thunderdog.challegram" || it == "ir.ilmili.telegraph" },
        )
        assertEquals(
            listOf(
                Utils.UriLaunchTarget("tg://resolve?domain=OneForAllHubBot", "org.thunderdog.challegram"),
                Utils.UriLaunchTarget("tg://resolve?domain=OneForAllHubBot", "ir.ilmili.telegraph"),
                Utils.UriLaunchTarget("https://t.me/OneForAllHubBot", null),
            ),
            packageProbePlan,
        )

        // 4. Channel link (@OneForAllHub) with www.t.me or telegram.dog resolves natively too
        assertEquals(
            "tg://resolve?domain=OneForAllHub",
            Utils.toTelegramDeepLink("https://www.t.me/OneForAllHub"),
        )
        assertEquals(
            "tg://resolve?domain=OneForAllHubBot",
            Utils.toTelegramDeepLink("https://telegram.dog/OneForAllHubBot"),
        )

        // 5. Private post tg:// URI falls back to https://t.me/c/<channel>/<post> when no Telegram installed
        assertEquals(
            listOf(Utils.UriLaunchTarget("https://t.me/c/123456/78", null)),
            Utils.resolveUriLaunchPlan(
                uriString = "tg://privatepost?channel=123456&post=78",
                querySchemeHandlers = { emptyList() },
                isPackageInstalled = { false },
            ),
        )

        // 4. No Telegram app installed -> falls back directly to https://t.me/... in browser
        val browserFallbackPlan = Utils.resolveUriLaunchPlan(
            uriString = "https://t.me/OneForAllHubBot",
            querySchemeHandlers = { emptyList() },
            isPackageInstalled = { false },
        )
        assertEquals(
            listOf(Utils.UriLaunchTarget("https://t.me/OneForAllHubBot", null)),
            browserFallbackPlan,
        )

        // 5. Direct tg:// URI when no Telegram client installed converts cleanly to https://t.me/... fallback
        val tgSchemeFallbackPlan = Utils.resolveUriLaunchPlan(
            uriString = "tg://resolve?domain=OneForAllHubBot",
            querySchemeHandlers = { emptyList() },
            isPackageInstalled = { false },
        )
        assertEquals(
            listOf(Utils.UriLaunchTarget("https://t.me/OneForAllHubBot", null)),
            tgSchemeFallbackPlan,
        )

        // 6. Non-Telegram URL stays untouched
        val githubPlan = Utils.resolveUriLaunchPlan(
            uriString = "https://github.com/OneForAllHub/HotaruNet",
            querySchemeHandlers = { error("should not query tg:// for github") },
            isPackageInstalled = { error("should not probe telegram pkgs for github") },
        )
        assertEquals(
            listOf(Utils.UriLaunchTarget("https://github.com/OneForAllHub/HotaruNet", null)),
            githubPlan,
        )
    }

}