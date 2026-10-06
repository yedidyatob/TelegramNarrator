package io.github.yedidyatob.telegramnarrator.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * Text direction of user content (messages, ads), independent of the app's UI language: a Hebrew ad on an
 * English device should be right-aligned, an English one on a Hebrew device left-aligned.
 */
object ContentDirection {
    /**
     * Direction of [texts] by majority of strong (letter) characters: RTL (Hebrew, Arabic, …) vs LTR. A Hebrew
     * text with a Latin brand name ("iPhone 17 במבצע") stays RTL, unlike a first-strong-character rule.
     * Null when there are no strong characters (digits, emoji, punctuation) or it's a tie: inherit the UI's.
     */
    fun of(vararg texts: String?): LayoutDirection? {
        var rtl = 0
        var ltr = 0
        for (text in texts) {
            if (text == null) continue
            var i = 0
            while (i < text.length) {
                val codePoint = text.codePointAt(i)
                i += Character.charCount(codePoint)
                when (Character.getDirectionality(codePoint)) {
                    Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                    Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> rtl++
                    Character.DIRECTIONALITY_LEFT_TO_RIGHT -> ltr++
                }
            }
        }
        return when {
            rtl > ltr -> LayoutDirection.Rtl
            ltr > rtl -> LayoutDirection.Ltr
            else -> null
        }
    }
}

/**
 * Lays out [content] in the direction of [texts] (see [ContentDirection.of]), so wrap-content texts, rows and
 * paddings start on the content's side. Falls back to the current (UI) direction when undecidable.
 */
@Composable
fun ProvideContentDirection(vararg texts: String?, content: @Composable () -> Unit) {
    val current = LocalLayoutDirection.current
    val direction = remember(current, *texts) { ContentDirection.of(*texts) ?: current }
    CompositionLocalProvider(LocalLayoutDirection provides direction, content = content)
}

/**
 * For multi-paragraph user text laid out with fillMaxWidth: each paragraph gets its own bidi direction from
 * its first strong character and is aligned to that paragraph's start (right for Hebrew, left for English).
 */
val ContentTextStyle: TextStyle = TextStyle(textDirection = TextDirection.Content, textAlign = TextAlign.Start)
