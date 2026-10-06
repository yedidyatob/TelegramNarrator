package io.github.yedidyatob.telegramnarrator.domain.audio

/**
 * The chat / channel title spoken right after the chat-boundary ding, so the listener knows which
 * channel is being read. Only the title itself is spoken (no "New chat" / "Message from" words); the
 * TTS engine picks the voice from the title's own script via [LanguageDetector], like a message body.
 */
object ChatTitleSpeech {
    /**
     * Speakable form of [rawTitle], or null when nothing is left to say.
     *
     * Runs the generic [MessageCleaner] (emoji, URLs, markdown markers) and then drops whitespace-separated
     * parts that have no letter or digit (decorative separators such as `|`, `•`, `-`, `»`). A title that is
     * only emoji / symbols, blank or null yields null, so the ding is followed directly by the messages.
     */
    fun speakableTitle(rawTitle: String?): String? {
        if (rawTitle.isNullOrBlank()) return null
        val cleaned = MessageCleaner.clean(rawTitle)
        val kept = cleaned.split(' ')
            .filter { it.isNotEmpty() && !MessageSpeechBody.isSymbolsOnly(it) }
            .joinToString(" ")
            .trim()
        return kept.ifEmpty { null }
    }

    /**
     * Queue items that open one chat: the boundary ding ([PlaybackItem.Intro]) and, when the chat is not
     * silent and its title has something speakable, the spoken [PlaybackItem.ChatTitle]. A silent chat
     * (every message dropped by the channel rules) gets neither a ding nor a title.
     */
    fun chatOpening(chatId: Long, rawTitle: String?, displayTitle: String, silent: Boolean): List<PlaybackItem> {
        val intro = PlaybackItem.Intro(displayTitle, chatId, silent = silent)
        if (silent) return listOf(intro)
        val spoken = speakableTitle(rawTitle) ?: return listOf(intro)
        return listOf(intro, PlaybackItem.ChatTitle(spoken, chatId, displayTitle))
    }
}
