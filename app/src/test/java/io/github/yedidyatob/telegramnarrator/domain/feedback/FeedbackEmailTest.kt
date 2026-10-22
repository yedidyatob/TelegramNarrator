package io.github.yedidyatob.telegramnarrator.domain.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackEmailTest {
    private val info = FeedbackEmail.Info("1.4.0", 42, "release", "123", "abc1234", "15", 35, "Google", "Pixel 8")

    @Test
    fun `body has a blank area then app and device facts only`() {
        val body = FeedbackEmail.body("Write here:", info)
        assertTrue(body.startsWith("Write here:\n\n"))
        assertTrue(body.contains("App: 1.4.0 (42) release · CI #123 abc1234"))
        assertTrue(body.contains("Android: 15 (API 35)"))
        assertTrue(body.contains("Device: Google Pixel 8"))
    }

    @Test
    fun `local build without CI info`() {
        val body = FeedbackEmail.body("x", info.copy(ciRun = "", gitSha = ""))
        assertTrue(body.contains("App: 1.4.0 (42) release\n"))
    }

    @Test
    fun `mailto goes to the feedback address`() {
        assertEquals("mailto:telegram.narrator.app@gmail.com?subject=S&body=B", FeedbackEmail.mailto("S", "B") { it })
    }
}
