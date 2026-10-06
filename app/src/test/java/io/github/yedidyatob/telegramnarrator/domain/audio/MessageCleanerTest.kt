package io.github.yedidyatob.telegramnarrator.domain.audio

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
    fun `intra word underscores without hash are kept`() {
        assertEquals("snake_case_name", MessageCleaner.clean("snake_case_name"))
        assertEquals("שלום_עולם", MessageCleaner.clean("שלום_עולם"))
        assertEquals("file_1 and file_2", MessageCleaner.clean("file_1 and file_2"))
    }

    @Test
    fun `english hashtags keep hash and speak underscores as spaces`() {
        assertEquals("# foo bar baz", MessageCleaner.clean("#foo_bar_baz"))
        assertEquals("# my tag", MessageCleaner.clean("#my_tag"))
        assertEquals("# tag", MessageCleaner.clean("#tag"))
        assertEquals("see # foo bar now", MessageCleaner.clean("see #foo_bar now"))
        assertEquals("# two tags # here", MessageCleaner.clean("#two_tags #here"))
    }

    @Test
    fun `hebrew hashtags keep hash and speak underscores as spaces`() {
        assertEquals("# שלום עולם", MessageCleaner.clean("#שלום_עולם"))
        assertEquals("# תג אחת", MessageCleaner.clean("#תג_אחת"))
        assertEquals("ראה # תג בוקר", MessageCleaner.clean("ראה #תג_בוקר"))
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
