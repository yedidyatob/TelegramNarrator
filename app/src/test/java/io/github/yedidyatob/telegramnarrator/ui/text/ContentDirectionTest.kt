package io.github.yedidyatob.telegramnarrator.ui.text

import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContentDirectionTest {

    @Test
    fun `Hebrew text is RTL and English text is LTR`() {
        assertEquals(LayoutDirection.Rtl, ContentDirection.of("תחזית מדויקת לכל עיר בישראל."))
        assertEquals(LayoutDirection.Ltr, ContentDirection.of("The 5 most important tech stories of the day."))
    }

    @Test
    fun `Arabic text is RTL`() {
        assertEquals(LayoutDirection.Rtl, ContentDirection.of("أخبار اليوم"))
    }

    @Test
    fun `Hebrew with a Latin brand name stays RTL even though it starts with Latin letters`() {
        assertEquals(LayoutDirection.Rtl, ContentDirection.of("iPhone 17 במבצע ענק עד סוף החודש"))
    }

    @Test
    fun `English with a Hebrew word stays LTR`() {
        assertEquals(LayoutDirection.Ltr, ContentDirection.of("Join the שלום community today"))
    }

    @Test
    fun `title and text are counted together`() {
        // Short English title, longer Hebrew body: the ad reads as Hebrew
        assertEquals(LayoutDirection.Rtl, ContentDirection.of("Tech", "החדשות החשובות של היום"))
        assertEquals(LayoutDirection.Ltr, ContentDirection.of("חדשות", "The most important stories of the day"))
    }

    @Test
    fun `no letters or a tie inherits the UI direction`() {
        assertNull(ContentDirection.of("123 456 !?"))
        assertNull(ContentDirection.of("\uD83D\uDE00\uD83D\uDD25"))
        assertNull(ContentDirection.of("", null))
        assertNull(ContentDirection.of())
        assertNull(ContentDirection.of("ab אב"))
    }
}
