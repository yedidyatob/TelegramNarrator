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

    // ---- Symbol-only lines and repeated punctuation -------------------------------------------------

    private val partiesMessage = "המפלגות שלא שלחו עדיין את עמדתן :\n\nהליכוד\nישר!\nביחד\nהדמוקרטים\nרע\"מ\nהמשותפת\n" +
        "עוצמה יהודית\nהמילואימניקים\nכחול לבן\nש\"ס\nיהדות התורה\n##\nיש לכן מה להסתיר?\n" +
        "לא מגיע לבוחרים שלכן לדעת מה עמדותיכן?"

    private val houthisMessage = "כלי תקשורת המזוהים עם החות'ים:\n\nסעודיה תקפה לפני זמן קצר את מתקני חברת הנפט " +
        "בתחנת א-צבאחה☝️ בעיר הבירה צנעא.\n\n##\nנמתין לתגובה החות'ית המובטחת..."

    @Test
    fun `real message A - separator line dropped, party list read with pauses`() {
        assertEquals(
            "המפלגות שלא שלחו עדיין את עמדתן : הליכוד, ישר! ביחד, הדמוקרטים, רע\"מ, המשותפת, עוצמה יהודית, " +
                "המילואימניקים, כחול לבן, ש\"ס, יהדות התורה. יש לכן מה להסתיר? לא מגיע לבוחרים שלכן לדעת מה עמדותיכן?",
            MessageCleaner.clean(partiesMessage)
        )
    }

    @Test
    fun `real message B - separator line and emoji dropped, ellipsis becomes a stop`() {
        assertEquals(
            "כלי תקשורת המזוהים עם החות'ים: סעודיה תקפה לפני זמן קצר את מתקני חברת הנפט בתחנת א-צבאחה " +
                "בעיר הבירה צנעא. נמתין לתגובה החות'ית המובטחת.",
            MessageCleaner.clean(houthisMessage)
        )
    }

    @Test
    fun `symbol-only lines are dropped`() {
        for (separator in listOf("##", "###", "---", "***", "•••", ". . .", "☝️", "====", "~~~~", "___", "|", "  ##  ")) {
            assertEquals("separator '$separator'", "first. second", MessageCleaner.clean("first\n$separator\nsecond"))
        }
        assertEquals("", MessageCleaner.clean("##\n---\n☝️"))
    }

    @Test
    fun `hashtag lines are kept`() {
        assertEquals("# חדשות. טקסט", MessageCleaner.clean("#חדשות\n\nטקסט"))
        assertEquals("# breaking news, text", MessageCleaner.clean("#breaking_news\ntext"))
        // A doubled hash before a word is still one hashtag
        assertEquals("# חדשות", MessageCleaner.clean("##חדשות"))
    }

    @Test
    fun `lines join with a comma, after a blank line with a stop, never doubling punctuation`() {
        assertEquals("a, b", MessageCleaner.clean("a\nb"))
        assertEquals("a. b", MessageCleaner.clean("a\n\nb"))
        assertEquals("a! b", MessageCleaner.clean("a!\nb"))
        assertEquals("a: b", MessageCleaner.clean("a:\n\nb"))
        assertEquals("a, b", MessageCleaner.clean("a,\nb"))
        assertEquals("(a), b", MessageCleaner.clean("(a)\nb"))
    }

    @Test
    fun `repeated punctuation is collapsed`() {
        assertEquals("wow!", MessageCleaner.clean("wow!!!"))
        assertEquals("really?", MessageCleaner.clean("really???"))
        assertEquals("what?!", MessageCleaner.clean("what?!"))
        assertEquals("wait. then", MessageCleaner.clean("wait.... then"))
        assertEquals("wait. then", MessageCleaner.clean("wait… then"))
        assertEquals("wait. then", MessageCleaner.clean("wait. . . then"))
        assertEquals("a b", MessageCleaner.clean("a ---- b"))
        assertEquals("a b", MessageCleaner.clean("a ==== b"))
        assertEquals("a b", MessageCleaner.clean("a ~~~~ b"))
        assertEquals("a b", MessageCleaner.clean("a ## b"))
    }

    @Test
    fun `meaningful punctuation and symbols are kept`() {
        val text = "צה\"ל, ש\"ס ורע\"מ: 5% הנחה, 20 ₪ או \$3 (+1) ב-12:30 ב-1/2; ג'ירפה? כן! סוף."
        assertEquals(text, MessageCleaner.clean(text))
        assertEquals("1.5 - 2.5", MessageCleaner.clean("1.5 - 2.5"))
    }
}
