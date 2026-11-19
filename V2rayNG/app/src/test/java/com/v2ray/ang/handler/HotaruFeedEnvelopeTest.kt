package com.v2ray.ang.handler

import com.v2ray.ang.util.HotaruCrypto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class HotaruFeedEnvelopeTest {
    @Test fun `github contents envelope is unwrapped to the base64 content field`() {
        val content = "QWxhZGRpbjpvcGVuIHNlc2FtZQ=="
        val envelope = """{"name":"feed.dat","path":"a/feed.dat","sha":"abc123",""" +
            """"size":24,"encoding":"base64","content":"$content","type":"file"}"""
        assertEquals(content, HotaruSmartEngine.extractApiContent(envelope))
    }

    @Test fun `plain feed text passes through untouched`() {
        val blob = "vless://00000000-0000-0000-0000-000000000000@a.invalid:443?security=tls"
        assertEquals(blob, HotaruSmartEngine.extractApiContent(blob))
        assertEquals("", HotaruSmartEngine.extractApiContent(""))
    }

    @Test fun `envelope without a content field yields an empty string not a crash`() {
        assertEquals("", HotaruSmartEngine.extractApiContent("""{"message":"Not Found"}"""))
    }

    @Test fun `no hardcoded production credentials exist in kotlin layer on jvm`() {
        HotaruCrypto.testSecretProvider = null
        assertEquals("", HotaruCrypto.getFeedToken())
        assertEquals("", HotaruCrypto.getSubUrl())
        assertTrue(HotaruCrypto.getFeedMirrors().isEmpty())
        assertEquals(0, HotaruCrypto.getRawAesKey().size)
    }

    @Test fun `auth headers carry the vault token and api version`() {
        val syntheticToken = "test_vault_token_only"
        HotaruCrypto.testSecretProvider = { id ->
            if (id == HotaruCrypto.ID_FEED_TOKEN) syntheticToken.toByteArray(Charsets.UTF_8) else null
        }
        try {
            val headers = HotaruSmartEngine.feedAuthHeaders()
            val parsed = com.v2ray.ang.util.JsonUtil.parseHeadersToMap(headers)
            assertEquals("Bearer $syntheticToken", parsed["Authorization"])
            assertEquals("application/vnd.github+json", parsed["Accept"])
            assertEquals(com.v2ray.ang.AppConfig.HOTARU_FEED_API_VERSION, parsed["X-GitHub-Api-Version"])
        } finally {
            HotaruCrypto.testSecretProvider = null
        }
    }

    @Test fun `decryptSubscriptionPayload decrypts aes-256-gcm envelope using vault key`() {
        val syntheticKey = ByteArray(32) { i -> (i + 7).toByte() }
        val nonce = ByteArray(12) { i -> (i * 3 + 1).toByte() }
        val plaintext = "vless://00000000-0000-0000-0000-000000000000@example.invalid:443#Node"

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(syntheticKey, "AES"), GCMParameterSpec(128, nonce))
        val sealed = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val envelope = Base64.getEncoder().encodeToString(nonce + sealed)

        HotaruCrypto.testSecretProvider = { id ->
            if (id == HotaruCrypto.ID_AES_KEY) syntheticKey.copyOf() else null
        }
        try {
            assertEquals(plaintext, HotaruCrypto.decryptSubscriptionPayload(envelope))
        } finally {
            HotaruCrypto.testSecretProvider = null
        }
    }
}