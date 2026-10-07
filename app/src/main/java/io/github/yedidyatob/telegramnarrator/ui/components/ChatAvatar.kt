package io.github.yedidyatob.telegramnarrator.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Round chat photo, or Telegram-style initials on a colored gradient while the photo downloads / when the chat
 * has none. Decorative: the chat title next to it carries the meaning for TalkBack.
 */
@Composable
fun ChatAvatar(
    chatId: Long,
    title: String,
    photoFileId: Int?,
    loadFile: suspend (Int) -> String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val photo = rememberTdImage(photoFileId, loadFile)
    Box(modifier.size(size).clip(CircleShape), contentAlignment = Alignment.Center) {
        Crossfade(targetState = photo, label = "avatar") { image ->
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(size)
                )
            } else {
                InitialsAvatar(chatId, title, size)
            }
        }
    }
}

@Composable
private fun InitialsAvatar(chatId: Long, title: String, size: Dp) {
    val (top, bottom) = AvatarColors.forChat(chatId)
    Box(
        Modifier
            .size(size)
            .background(Brush.verticalGradient(listOf(top, bottom))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = AvatarInitials.of(title),
            color = Color.White,
            style = TextStyle(fontSize = (size.value * 0.38f).sp, fontWeight = FontWeight.SemiBold)
        )
    }
}

/** Telegram's seven avatar gradients (red, orange, violet, green, cyan, blue, pink), picked by chat id. */
internal object AvatarColors {
    private val gradients = listOf(
        Color(0xFFFF885E) to Color(0xFFFF516A),
        Color(0xFFFFCD6A) to Color(0xFFFFA85C),
        Color(0xFFE0A2F3) to Color(0xFFD669ED),
        Color(0xFFA0DE7E) to Color(0xFF54CB68),
        Color(0xFF53EDD6) to Color(0xFF28C9B7),
        Color(0xFF72D5FD) to Color(0xFF2A9EF1),
        Color(0xFFFFA8A8) to Color(0xFFFF719A)
    )

    fun forChat(chatId: Long): Pair<Color, Color> = gradients[Math.floorMod(chatId, gradients.size.toLong()).toInt()]
}

/** Up to two initials of a chat title: first letters of the first two words ("Daily Tech" → "DT"). */
object AvatarInitials {
    fun of(title: String): String {
        val words = title.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val letters = words.mapNotNull { firstLetterOrDigit(it) }
        if (letters.isEmpty()) {
            // Emoji / symbol-only titles: show the first character (an emoji is fine on a colored circle)
            return title.trim().takeIf { it.isNotEmpty() }?.let { firstGrapheme(it) } ?: "?"
        }
        return letters.take(2).joinToString("").uppercase()
    }

    private fun firstLetterOrDigit(word: String): String? {
        var i = 0
        while (i < word.length) {
            val cp = word.codePointAt(i)
            if (Character.isLetterOrDigit(cp)) return String(Character.toChars(cp))
            i += Character.charCount(cp)
        }
        return null
    }

    private fun firstGrapheme(text: String): String {
        val it = java.text.BreakIterator.getCharacterInstance()
        it.setText(text)
        val end = it.next()
        return if (end == java.text.BreakIterator.DONE) text else text.substring(0, end)
    }
}
