package io.github.yedidyatob.telegramnarrator.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.text.BidiFormatter

/**
 * A UI string that starts with a number keeps its own reading order: untranslated English text on a Hebrew
 * device ("5 of 6 selected") would otherwise take the layout's RTL direction and read "of 6 selected 5".
 * Text whose first strong character already matches the layout is returned as is.
 */
@Composable
fun bidiSafe(text: String): String {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return remember(text, rtl) { BidiFormatter.getInstance(rtl).unicodeWrap(text) }
}
