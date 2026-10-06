package io.github.yedidyatob.telegramnarrator.domain.sponsored

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SponsoredLinkPolicyTest {
    @Test
    fun `Telegram hosts open without confirmation`() {
        listOf(
            "https://t.me/SecretAdTestChannel",
            "https://telegram.org/blog",
            "https://telegram.me/somebot?start=x",
            "https://telegram.dog/channel",
            "https://telegra.ph/Article-01-01",
            "https://te.legra.ph/Article",
            "https://graph.org/page",
            "https://fragment.com/username/x",
            "https://telesco.pe/channel/1",
            "https://core.telegram.org/api",
            "https://T.ME/UpperCase",
            "t.me/no_scheme",
            "tg://resolve?domain=durov"
        ).forEach { assertFalse(it, SponsoredLinkPolicy.requiresConfirmation(it)) }
    }

    @Test
    fun `other hosts need confirmation`() {
        listOf(
            "https://example.com",
            "https://t.me.evil.com/x",
            "https://nott.me/x",
            "https://telegram.org.example.com",
            "https://faketelegram.org",
            "https://fragment.com.attacker.net",
            "http://192.168.0.1/",
            "",
            "not a url at all"
        ).forEach { assertTrue(it, SponsoredLinkPolicy.requiresConfirmation(it)) }
    }

    @Test
    fun `t me and tg links open in the Telegram app`() {
        assertTrue(SponsoredLinkPolicy.opensInTelegramApp("https://t.me/channel"))
        assertTrue(SponsoredLinkPolicy.opensInTelegramApp("https://telegram.me/bot"))
        assertTrue(SponsoredLinkPolicy.opensInTelegramApp("tg://resolve?domain=x"))
        assertFalse(SponsoredLinkPolicy.opensInTelegramApp("https://telegram.org/blog"))
        assertFalse(SponsoredLinkPolicy.opensInTelegramApp("https://example.com"))
    }

    @Test
    fun `host parsing and normalization`() {
        assertEquals("t.me", SponsoredLinkPolicy.hostOf("https://t.me/x"))
        assertEquals("t.me", SponsoredLinkPolicy.hostOf("t.me/x"))
        assertNull(SponsoredLinkPolicy.hostOf(""))
        assertEquals("https://t.me/x", SponsoredLinkPolicy.normalized("t.me/x"))
        assertEquals("tg://resolve?domain=x", SponsoredLinkPolicy.normalized("tg://resolve?domain=x"))
        assertEquals("https://example.com", SponsoredLinkPolicy.normalized(" https://example.com "))
    }
}
