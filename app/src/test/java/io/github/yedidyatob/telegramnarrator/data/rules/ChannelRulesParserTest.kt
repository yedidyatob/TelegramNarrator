package io.github.yedidyatob.telegramnarrator.data.rules

import io.github.yedidyatob.telegramnarrator.domain.cleaning.ChannelRulesConfig
import io.github.yedidyatob.telegramnarrator.domain.cleaning.NumberedListMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ChannelRulesParserTest {

    @Test
    fun `the shipped asset parses and has the abu ali preset`() {
        val config = ChannelRulesParser.parse(TestResources.shippedRulesJson())
        assertEquals(1, config.channels.size)
        val channel = config.channels[0]
        assertTrue(channel.match.matches(1L, "אבו עלי אקספרס"))
        assertTrue(channel.match.matches(1L, "  אבו עלי אקספרס "))
        assertFalse(channel.match.matches(1L, "ערוץ אחר"))
        assertEquals(NumberedListMode.NATURAL, channel.preset.numberedLists)
        assertEquals(listOf("°"), channel.preset.stripSymbols)
        assertEquals(3, channel.preset.dropMessage.size)
        assertEquals(1, channel.preset.dropNext.size)
        assertEquals(1, channel.preset.dropNext[0].count)
        assertEquals(2, channel.preset.cut.size)
        // everything else is the default
        assertTrue(config.default.dropMessage.isEmpty())
        assertFalse(config.default.readLinks) // links are never read by default
        assertTrue(config.default.removeTelegramLinks)
    }

    @Test
    fun `parses every option and rule type`() {
        val config = ChannelRulesParser.parse(
            """
            {
              "default": { "options": { "readLinks": false, "linkLabel": "קישור" } },
              "channels": [{
                "name": "x",
                "match": { "chatIds": [5, 7], "titleContains": "News" },
                "rules": {
                  "dropMessage": ["ad"],
                  "dropNext": [{ "pattern": "marker", "count": 2 }, { "pattern": "single" }],
                  "cut": ["tail"],
                  "replace": [{ "pattern": "(a)(b)", "replacement": "${'$'}2${'$'}1" }]
                },
                "options": { "readLinks": true, "removeTelegramLinks": true, "numberedLists": "STRIP", "stripSymbols": ["#"] }
              }]
            }
            """.trimIndent()
        )
        val p = config.channels[0].preset
        assertEquals(setOf(5L, 7L), config.channels[0].match.chatIds)
        assertEquals("News", config.channels[0].match.titleContains)
        assertEquals(listOf(2, 1), p.dropNext.map { it.count })
        assertEquals("${'$'}2${'$'}1", p.replace[0].replacement)
        assertTrue(p.readLinks) // channel overrides the default
        assertEquals("קישור", p.linkLabel) // inherited from the default
        assertTrue(p.removeTelegramLinks)
        assertEquals(NumberedListMode.STRIP, p.numberedLists)
        assertFalse(config.default.readLinks)
    }

    @Test
    fun `inheritDefault false ignores the default preset`() {
        val config = ChannelRulesParser.parse(
            """
            {
              "default": { "rules": { "cut": ["x"] }, "options": { "stripSymbols": ["@"], "readLinks": true } },
              "channels": [
                { "name": "inherits", "match": { "titleEquals": "A" } },
                { "name": "alone", "match": { "titleEquals": "B" }, "inheritDefault": false }
              ]
            }
            """.trimIndent()
        )
        assertEquals(1, config.channels[0].preset.cut.size)
        assertEquals(listOf("@"), config.channels[0].preset.stripSymbols)
        assertTrue(config.channels[0].preset.readLinks) // inherited
        assertEquals(0, config.channels[1].preset.cut.size)
        assertTrue(config.channels[1].preset.stripSymbols.isEmpty())
        assertFalse(config.channels[1].preset.readLinks) // not inherited: built-in default
    }

    @Test
    fun `empty object is a valid config`() {
        val config = ChannelRulesParser.parse("{}")
        assertTrue(config.channels.isEmpty())
        assertEquals(ChannelRulesConfig.EMPTY.default, config.default)
        assertFalse(config.default.readLinks) // without any options, links are removed
        assertTrue(config.default.removeTelegramLinks)
        assertNull(config.default.linkLabel)
    }

    private fun assertRejected(json: String, messagePart: String) {
        try {
            ChannelRulesParser.parse(json)
            fail("expected a parse error for $json")
        } catch (e: ChannelRulesParseException) {
            assertTrue("'${e.message}' should contain '$messagePart'", e.message!!.contains(messagePart))
        }
    }

    @Test
    fun `bad input is rejected with a helpful message`() {
        assertRejected("not json", "Invalid channel rules JSON")
        assertRejected("""{"channels":[{"name":"c","match":{"titleEquals":"a"},"rules":{"cut":["("]}}]}""", "channel 'c': cut: invalid regex")
        assertRejected("""{"channels":[{"name":"c"}]}""", "missing 'match'")
        assertRejected("""{"channels":[{"name":"c","match":{}}]}""", "needs chatIds")
        assertRejected("""{"channels":[{"name":"c","match":{"titleEquals":"a"},"options":{"numberedLists":"bogus"}}]}""", "numberedLists")
        assertRejected("""{"channels":[{"name":"c","match":{"titleEquals":"a"},"rules":{"dropNext":[{"count":1}]}}]}""", "missing 'pattern'")
    }

    @Test
    fun `first matching channel wins and ids match regardless of title`() {
        val config = ChannelRulesParser.parse(
            """
            {"channels":[
              {"name":"one","match":{"chatIds":[42]}, "options":{"stripSymbols":["1"]}},
              {"name":"two","match":{"titleContains":"news"}, "options":{"stripSymbols":["2"]}}
            ]}
            """.trimIndent()
        )
        val engine = io.github.yedidyatob.telegramnarrator.domain.cleaning.ChannelRulesEngine(config)
        assertEquals(listOf("1"), engine.presetFor(42, "Daily News").stripSymbols)
        assertEquals(listOf("2"), engine.presetFor(1, "Daily News").stripSymbols)
        assertSame(config.default, engine.presetFor(1, "Other"))
        assertSame(config.default, engine.presetFor(1, null))
    }
}
