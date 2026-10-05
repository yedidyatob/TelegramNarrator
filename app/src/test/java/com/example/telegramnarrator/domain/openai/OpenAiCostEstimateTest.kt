package com.example.telegramnarrator.domain.openai

import com.example.telegramnarrator.data.rules.ChannelRulesParser
import com.example.telegramnarrator.data.rules.TestResources
import com.example.telegramnarrator.domain.audio.MessageCleaner
import com.example.telegramnarrator.domain.cleaning.ChannelRulesEngine
import com.example.telegramnarrator.domain.model.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Character counts after Abu Ali channel rules + MessageCleaner for ~200 representative posts.
 * Used for the OpenAI TTS cost estimate in the PR description.
 *
 * Pricing (verified 2026-10): tts-1 = $15 / 1M chars, tts-1-hd = $30 / 1M chars.
 */
class OpenAiCostEstimateTest {

    private val abuAliTitle = "אבו עלי אקספרס"
    private val chatId = 100L
    private val engine = ChannelRulesEngine(ChannelRulesParser.parse(TestResources.shippedRulesJson()))

    private fun spoken(text: String): String {
        val preset = engine.presetFor(chatId, abuAliTitle)
        val afterRules = engine.applyTextRules(text, preset)
        // Drop-style ads may leave empty after rules; evaluate drop for ads
        return MessageCleaner.clean(afterRules)
    }

    private fun loadMessages(): List<String> {
        val stream = javaClass.getResourceAsStream("/openai_cost/messages.txt")
            ?: error("Missing openai_cost/messages.txt")
        val raw = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return raw.split("\n---MSG---\n").map { it.trim() }.filter { it.isNotEmpty() }
    }

    @Test
    fun `200 abu-ali-style messages cleaned char counts for cost estimate`() {
        val messages = loadMessages()
        assertEquals(200, messages.size)

        // Apply drop rules the same way playback would for a batch
        val domainMessages = messages.mapIndexed { i, t ->
            Message(id = i + 1L, chatId = chatId, senderName = "x", text = t, timestamp = 0L, isOutgoing = false)
        }
        val decisions = engine.evaluate(chatId, abuAliTitle, domainMessages, moreUnreadFollows = false)
        val cleanedSpoken = decisions
            .filter { !it.dropped && !it.deferred }
            .map { MessageCleaner.clean(it.text) }
            .filter { it.isNotBlank() }

        val chars = cleanedSpoken.map { it.length }
        val total = chars.sum()
        val avg = total.toDouble() / cleanedSpoken.size

        // Sanity: cleaning should shrink vs raw corpus
        val rawTotal = messages.sumOf { it.length }
        assertTrue("cleaned total ($total) should be less than raw ($rawTotal)", total < rawTotal)
        assertTrue("should keep most non-ad messages", cleanedSpoken.size >= 150)

        // Persist a small report for humans / PR (test working dir is app/)
        val report = buildString {
            appendLine("messages_raw=${messages.size}")
            appendLine("messages_spoken_after_clean=${cleanedSpoken.size}")
            appendLine("avg_chars_per_spoken_msg=${"%.1f".format(avg)}")
            appendLine("total_chars_spoken=$total")
            appendLine("raw_total_chars=$rawTotal")
            val tts1 = total / 1_000_000.0 * 15.0
            val tts1hd = total / 1_000_000.0 * 30.0
            appendLine("cost_tts1_first_listen_usd=${"%.4f".format(tts1)}")
            appendLine("cost_tts1hd_first_listen_usd=${"%.4f".format(tts1hd)}")
            appendLine("cost_with_cache_replay_usd=0.0000")
            appendLine("pricing_source=OpenAI API pricing (tts-1 \$15/1M chars, tts-1-hd \$30/1M chars)")
        }
        File("build/openai_tts_cost_estimate.txt").apply {
            parentFile?.mkdirs()
            writeText(report)
        }
        // Also print for CI logs
        println(report)

        // Stable ballpark assertions so the estimate cannot silently collapse
        assertTrue("avg chars should be in a realistic news-post range", avg in 40.0..800.0)
        assertTrue("200 posts should produce thousands of spoken chars", total in 5_000..200_000)
    }
}
