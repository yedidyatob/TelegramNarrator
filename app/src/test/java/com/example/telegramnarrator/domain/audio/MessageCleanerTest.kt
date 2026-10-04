package com.example.telegramnarrator.domain.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageCleanerTest {

    @Test
    fun `plain text is unchanged`() {
        assertEquals("Hello world", MessageCleaner.clean("Hello world"))
        assertEquals("שלום עולם", MessageCleaner.clean("שלום עולם"))
    }

    @Test
    fun `urls are removed by default`() {
        assertEquals("see now", MessageCleaner.clean("see https://example.com/a_b*c now"))
        assertEquals("", MessageCleaner.clean("www.example.com"))
        assertEquals("a b", MessageCleaner.clean("a https://t.me/chan/1?single b"))
    }

    @Test
    fun `urls are replaced with the supplied localized label`() {
        assertEquals("ראה קישור עכשיו", MessageCleaner.clean("ראה https://example.com/x עכשיו", "קישור"))
        // The label is inserted literally (no regex group interpretation)
        assertEquals("a \$1 b", MessageCleaner.clean("a https://x.y b", "\$1"))
    }

    @Test
    fun `double markers are stripped`() {
        assertEquals("bold text", MessageCleaner.clean("**bold** text"))
        assertEquals("bold text", MessageCleaner.clean("__bold__ text"))
    }

    @Test
    fun `single markers are stripped`() {
        assertEquals("italic and italic", MessageCleaner.clean("*italic* and _italic_"))
        assertEquals("some code here", MessageCleaner.clean("some `code` here"))
        assertEquals("block", MessageCleaner.clean("```block```"))
        assertEquals("שלום עולם", MessageCleaner.clean("*שלום* _עולם_"))
    }

    @Test
    fun `markers spanning several words`() {
        assertEquals("one two three", MessageCleaner.clean("*one two* three"))
        assertEquals("one two three", MessageCleaner.clean("one **two three**"))
    }

    @Test
    fun `intra word underscores and hashtags are kept`() {
        assertEquals("snake_case_name", MessageCleaner.clean("snake_case_name"))
        assertEquals("#my_tag", MessageCleaner.clean("#my_tag"))
        assertEquals("שלום_עולם", MessageCleaner.clean("שלום_עולם"))
        assertEquals("file_1 and file_2", MessageCleaner.clean("file_1 and file_2"))
    }

    @Test
    fun `multiplication is kept but stray markers are removed`() {
        assertEquals("2*3", MessageCleaner.clean("2*3"))
        assertEquals("2 * 3 = 6", MessageCleaner.clean("2 * 3 = 6"))
        assertEquals("note", MessageCleaner.clean("**note"))
        assertEquals("note", MessageCleaner.clean("note *"))
        assertEquals("a b", MessageCleaner.clean("a ____ b"))
    }

    @Test
    fun `emoji are removed and whitespace collapsed`() {
        assertEquals("hi there", MessageCleaner.clean("hi \uD83D\uDE00   there  \n"))
        assertEquals("", MessageCleaner.clean("\uD83D\uDE00"))
    }
}
