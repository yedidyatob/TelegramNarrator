package io.github.yedidyatob.telegramnarrator.domain.privacy

/**
 * Telegram API terms: showing a message's full text counts as reading it, so outside playback (where the
 * message being read aloud is marked as read) message text is only shown as a short chat-list-style preview.
 * See docs/mark-as-read.md.
 */
object MessagePreview {
    const val MAX_CHARS = 60
    /** Lines the preview may take on screen (the text is also ellipsized to [MAX_CHARS]). */
    const val MAX_LINES = 1
    private const val ELLIPSIS = "…"

    /**
     * [text] with line breaks / runs of whitespace collapsed to single spaces, cut to at most [maxChars]
     * characters (preferring a word boundary, never splitting a surrogate pair) plus an ellipsis.
     */
    fun of(text: String, maxChars: Int = MAX_CHARS): String {
        val flat = text.trim().replace(Regex("\\s+"), " ")
        if (flat.length <= maxChars) return flat
        var end = maxChars
        if (Character.isHighSurrogate(flat[end - 1])) end--
        val space = flat.lastIndexOf(' ', end)
        if (space >= maxChars / 2) end = space
        return flat.substring(0, end).trimEnd() + ELLIPSIS
    }
}
