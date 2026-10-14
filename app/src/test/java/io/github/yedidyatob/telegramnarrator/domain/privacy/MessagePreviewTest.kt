package io.github.yedidyatob.telegramnarrator.domain.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagePreviewTest {
    @Test
    fun `short text is shown as is, on one line`() {
        assertEquals("Hello world", MessagePreview.of("Hello\n\n  world "))
    }

    @Test
    fun `long text is cut at a word boundary with an ellipsis`() {
        val long = "word ".repeat(60)
        val preview = MessagePreview.of(long)
        assertTrue(preview.length <= MessagePreview.MAX_CHARS + 1)
        assertTrue(preview.endsWith("word…"))
    }

    @Test
    fun `never shows the full text of a long message`() {
        val long = "א".repeat(500)
        assertEquals(MessagePreview.MAX_CHARS + 1, MessagePreview.of(long).length)
    }

    @Test
    fun `an emoji is never split`() {
        val text = "a".repeat(99) + "😀" + "b".repeat(10)
        val preview = MessagePreview.of(text)
        assertEquals("a".repeat(99) + "…", preview)
    }
}
