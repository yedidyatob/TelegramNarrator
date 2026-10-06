package io.github.yedidyatob.telegramnarrator.domain.cleaning

import io.github.yedidyatob.telegramnarrator.data.rules.ChannelRulesParser
import io.github.yedidyatob.telegramnarrator.data.rules.TestResources
import io.github.yedidyatob.telegramnarrator.domain.audio.MessageCleaner
import io.github.yedidyatob.telegramnarrator.domain.model.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelRulesEngineTest {

    private val abuAliTitle = "אבו עלי אקספרס"
    private val chatId = 100L
    private val engine = ChannelRulesEngine(ChannelRulesParser.parse(TestResources.shippedRulesJson()))

    private fun msg(id: Long, text: String) = Message(
        id = id, chatId = chatId, senderName = "x", text = text, timestamp = 0L, isOutgoing = false
    )

    /** What the playback pipeline does for a message that is read: rules, then the generic cleaner. */
    private fun spoken(chatTitle: String, text: String): String {
        val preset = engine.presetFor(chatId, chatTitle)
        return MessageCleaner.clean(engine.applyTextRules(text, preset))
    }

    private fun evaluate(texts: List<String>, moreUnread: Boolean = false, title: String = abuAliTitle) =
        engine.evaluate(chatId, title, texts.mapIndexed { i, t -> msg(i + 1L, t) }, moreUnread)

    // --- real sample messages ---

    @Test
    fun `sample 1 headline list - t me links removed and numbers read naturally`() {
        assertEquals(
            TestResources.sample("sample1_headlines.expected.txt"),
            spoken(abuAliTitle, TestResources.sample("sample1_headlines.txt"))
        )
    }

    @Test
    fun `sample 2 comments outro is cut whatever the number`() {
        val text = TestResources.sample("sample2_comments_outro.txt")
        val expected = TestResources.sample("sample2_comments_outro.expected.txt")
        for (n in listOf(1, 3, 4, 7, 9, 12, 120)) {
            assertEquals("outro with $n", expected, spoken(abuAliTitle, text.replace("3 תגובות", "$n תגובות")))
        }
        // singular with digit
        assertEquals(expected, spoken(abuAliTitle, text.replace("3 תגובות", "1 תגובה")))
        // natural Hebrew singular ("one comment") — device-reported missing form
        assertEquals(expected, spoken(abuAliTitle, text.replace("3 תגובות", "תגובה אחת")))
    }

    @Test
    fun `Hebrew comment footers singular and plural are stripped from real-ish posts`() {
        val body = "כותרת החדשות על המבצע בלילה\n\nעוד משפט חשוב."
        val expected = MessageCleaner.clean(body)
        for (footer in listOf(
            "תגובה אחת",
            "1 תגובה",
            "2 תגובות",
            "12 תגובות",
            "  תגובה אחת  ",
            "\t7 תגובות\t",
        )) {
            val raw = "$body\n\n$footer"
            assertEquals("footer='$footer'", expected, spoken(abuAliTitle, raw))
        }
        // numbers inside the article must stay
        assertTrue(spoken(abuAliTitle, "דירוג 9 מתוך 10\n\nתגובה אחת").contains("9 מתוך 10"))
    }

    @Test
    fun `sample 2 numbers inside the sentence are untouched`() {
        assertTrue(spoken(abuAliTitle, TestResources.sample("sample2_comments_outro.txt")).contains("9 מתוך 10"))
    }

    @Test
    fun `sample 3 ad with the marker in the same message is dropped but not the next message`() {
        val ad = TestResources.sample("sample3_ad_inline.txt")
        val news = TestResources.sample("sample4_react_outro.txt")
        val result = evaluate(listOf(news, ad, news))
        assertEquals(listOf(false, true, false), result.map { it.dropped })
        assertTrue(result.none { it.deferred })
        assertEquals("", result[1].text)
    }

    @Test
    fun `sample 3 marker as its own message drops the marker and the ad after it`() {
        val marker = TestResources.sample("sample3_ad_marker.txt")
        val ad = TestResources.sample("sample3_ad_body.txt")
        val news = TestResources.sample("sample4_react_outro.txt")
        val result = evaluate(listOf(news, marker, ad, news))
        assertEquals(listOf(false, true, true, false), result.map { it.dropped })
        assertTrue(result.none { it.deferred })
        assertEquals(TestResources.sample("sample4_react_outro.expected.txt"), MessageCleaner.clean(result[3].text))
    }

    @Test
    fun `an ad with a givechak link is dropped even without the marker`() {
        val ad = TestResources.sample("sample3_ad_body.txt")
        val result = evaluate(listOf("חדשות רגילות", ad, "עוד חדשות"))
        assertEquals(listOf(false, true, false), result.map { it.dropped })
    }

    @Test
    fun `sample 4 reply outro is cut`() {
        assertEquals(
            TestResources.sample("sample4_react_outro.expected.txt"),
            spoken(abuAliTitle, TestResources.sample("sample4_react_outro.txt"))
        )
    }

    @Test
    fun `sample 5 several outros inside one message are all cut`() {
        assertEquals(
            TestResources.sample("sample5_multiple_outros.expected.txt"),
            spoken(abuAliTitle, TestResources.sample("sample5_multiple_outros.txt"))
        )
    }

    @Test
    fun `degree sign is stripped and other chats are left to the generic cleaner`() {
        assertEquals("שלום", spoken(abuAliTitle, "°שלום°"))
        val other = "°שלום\n\n3 תגובות\n1. פריט https://t.me/abualiexpress/1"
        assertEquals(MessageCleaner.clean(other), spoken("ערוץ אחר", other))
    }

    // --- drop-next semantics ---

    @Test
    fun `marker followed by the ad in the next batch is deferred, not dropped`() {
        val marker = TestResources.sample("sample3_ad_marker.txt")
        val result = evaluate(listOf("חדשות", "עוד חדשות", marker), moreUnread = true)
        assertEquals(listOf(false, false, false), result.map { it.dropped })
        assertEquals(listOf(false, false, true), result.map { it.deferred })
    }

    @Test
    fun `marker as the very last unread message is dropped and marked`() {
        val marker = TestResources.sample("sample3_ad_marker.txt")
        val result = evaluate(listOf("חדשות", marker), moreUnread = false)
        assertEquals(listOf(false, true), result.map { it.dropped })
        assertTrue(result.none { it.deferred })
    }

    @Test
    fun `a complete marker plus ad at the end of a batch is not deferred even if more unread follow`() {
        val marker = TestResources.sample("sample3_ad_marker.txt")
        val ad = TestResources.sample("sample3_ad_body.txt")
        val result = evaluate(listOf("חדשות", marker, ad), moreUnread = true)
        assertEquals(listOf(false, true, true), result.map { it.dropped })
        assertTrue(result.none { it.deferred })
    }

    @Test
    fun `count greater than one drops that many following messages and defers a partial group`() {
        val config = ChannelRulesParser.parse(
            """{"channels":[{"name":"c","match":{"titleEquals":"C"},
                "rules":{"dropNext":[{"pattern":"^AD${'$'}","count":2}]}}]}"""
        )
        val e = ChannelRulesEngine(config)
        fun run(texts: List<String>, more: Boolean) =
            e.evaluate(1, "C", texts.mapIndexed { i, t -> msg(i + 1L, t) }, more)

        assertEquals(listOf(false, true, true, true, false), run(listOf("a", "AD", "x", "y", "b"), false).map { it.dropped })
        // only one follower in the batch, group continues -> marker and its follower are deferred
        val partial = run(listOf("a", "AD", "x"), true)
        assertEquals(listOf(false, true, true), partial.map { it.deferred })
        assertEquals(listOf(false, false, false), partial.map { it.dropped })
        // nothing follows -> dropped
        assertEquals(listOf(false, true, true), run(listOf("a", "AD", "x"), false).map { it.dropped })
    }

    @Test
    fun `back to back markers extend the group`() {
        val config = ChannelRulesParser.parse(
            """{"channels":[{"name":"c","match":{"titleEquals":"C"},"rules":{"dropNext":[{"pattern":"^AD${'$'}"}]}}]}"""
        )
        val e = ChannelRulesEngine(config)
        val result = e.evaluate(1, "C", listOf("a", "AD", "AD", "ad body", "b").mapIndexed { i, t -> msg(i + 1L, t) }, false)
        assertEquals(listOf(false, true, true, true, false), result.map { it.dropped })
    }

    @Test
    fun `dropped messages keep their position so they can be marked read in order`() {
        val result = evaluate(listOf("a", "°תוכן שיווקי", "ad givechak.co.il", "b"))
        assertEquals(listOf(1L, 2L, 3L, 4L), result.map { it.message.id })
    }

    // --- options ---

    private fun preset(options: String, rules: String = "{}") = ChannelRulesParser.parse(
        """{"channels":[{"name":"c","match":{"titleEquals":"C"},"rules":$rules,"options":$options}]}"""
    ).channels[0].preset

    private val e = ChannelRulesEngine()
    private val linkReader = ChannelRulesEngine(defaultLinkLabel = { "קישור" })

    @Test
    fun `readLinks false removes links`() {
        val p = preset("""{"readLinks":false}""")
        assertEquals("ראו  ועוד", e.applyTextRules("ראו https://example.com/a?b=1 ועוד", p))
        assertEquals("ראו", MessageCleaner.clean(e.applyTextRules("ראו www.example.com/x", p)))
    }

    @Test
    fun `linkLabel replaces links`() {
        val p = preset("""{"readLinks":true,"linkLabel":"לינק"}""")
        assertEquals("ראו לינק ועוד לינק", MessageCleaner.clean(e.applyTextRules("ראו https://a.com/x ועוד www.b.com", p)))
    }

    @Test
    fun `links are removed by default - no preset options needed`() {
        val p = preset("""{}""")
        assertFalse(p.readLinks)
        assertEquals("ראו", MessageCleaner.clean(e.applyTextRules("ראו https://a.com/x", p)))
        assertEquals("ראו", MessageCleaner.clean(e.applyTextRules("ראו www.a.com/x?y=1", p)))
        // and with no rules at all
        assertEquals("ראו", MessageCleaner.clean(e.applyTextRules("ראו https://a.com/x", ChannelRulesConfig.EMPTY.default)))
    }

    @Test
    fun `readLinks true without a label reads the localized default word`() {
        val p = preset("""{"readLinks":true}""")
        assertEquals("ראו Link", MessageCleaner.clean(e.applyTextRules("ראו https://a.com/x", p)))
        assertEquals("ראו קישור", MessageCleaner.clean(linkReader.applyTextRules("ראו https://a.com/x", p)))
    }

    @Test
    fun `shipped default preset skips every link for every chat`() {
        val text = "חדשות https://example.com/a?b=1 וגם t.me/somechannel/12?single ועוד www.site.co.il/x וכן http://telegram.me/c/1 סוף"
        for (title in listOf("ערוץ אחר", abuAliTitle, "")) {
            assertEquals("חדשות וגם ועוד וכן סוף", spoken(title, text))
        }
        assertFalse(engine.presetFor(chatId, "ערוץ אחר").readLinks)
        assertTrue(engine.presetFor(chatId, "ערוץ אחר").removeTelegramLinks)
    }

    @Test
    fun `removeTelegramLinks removes t me links including suffixes but not other links`() {
        val p = preset("""{"removeTelegramLinks":true}""")
        assertEquals(
            "a b c",
            MessageCleaner.clean(
                e.applyTextRules(
                    "a https://t.me/chan/12?single b t.me/chan/3 c http://telegram.me/x/1?single=1 https://example.com/t.me", p
                )
            )
        )
    }

    @Test
    fun `numbered list modes`() {
        val text = "1. אחד\n2) שניים\n   10. עשר\nב. לא מספר\n3.14 לא רשימה"
        assertEquals(text, e.applyTextRules(text, preset("""{"numberedLists":"keep"}""")))
        assertEquals("אחד\nשניים\nעשר\nב. לא מספר\n3.14 לא רשימה", e.applyTextRules(text, preset("""{"numberedLists":"strip"}""")))
        assertEquals("1, אחד\n2, שניים\n10, עשר\nב. לא מספר\n3.14 לא רשימה", e.applyTextRules(text, preset("""{"numberedLists":"natural"}""")))
    }

    @Test
    fun `stripSymbols cut and replace are applied in that order of rules`() {
        val p = preset(
            """{"stripSymbols":["°","■"]}""",
            """{"cut":["\\(.*?\\)"],"replace":[{"pattern":"(\\d+)%","replacement":"${'$'}1 אחוז"}]}"""
        )
        assertEquals(" הריבית עלתה ב-5 אחוז ", e.applyTextRules("°■ הריבית עלתה ב-5% (מקור: ערוץ)", p))
    }

    @Test
    fun `text fully removed by the rules is left blank for the player to skip`() {
        val result = evaluate(listOf("12 תגובות", "https://t.me/abualiexpress/5"))
        assertEquals(listOf(false, false), result.map { it.dropped })
        assertEquals(listOf("", ""), result.map { MessageCleaner.clean(it.text) })
    }

    @Test
    fun `empty engine changes nothing`() {
        val text = "°תוכן שיווקי\n3 תגובות"
        assertEquals(text, e.applyTextRules(text, e.presetFor(1, "x")))
        assertFalse(e.evaluate(1, "x", listOf(msg(1, text)), true)[0].dropped)
    }
}
