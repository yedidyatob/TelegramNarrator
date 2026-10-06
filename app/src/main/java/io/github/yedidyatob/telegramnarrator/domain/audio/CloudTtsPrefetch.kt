package io.github.yedidyatob.telegramnarrator.domain.audio

/**
 * Prefetch helpers for cloud TTS engines (OpenAI / Edge). While the current synthesized message plays,
 * the next [COUNT] speakable queue items are synthesized into the existing disk cache so playback
 * does not wait on a full network round-trip per item.
 *
 * System TTS is never prefetched (it has no disk cache path). Prefetch must be cancelled on
 * skip / stop / pause / engine change — callers own that lifecycle.
 */
object CloudTtsPrefetch {
    /** How many upcoming speakable messages to warm into the disk cache while the current one plays. */
    const val COUNT = 2

    /**
     * Walks [items] in order and returns up to [limit] speech texts that cloud TTS would synthesize
     * (same cleaning / skip rules as [io.github.yedidyatob.telegramnarrator.core.service.PlaybackService]).
     * Includes spoken chat titles; skips intro dings, silence, outro, dropped rows, voice notes, and symbol-/media-only bodies.
     */
    fun upcomingSpeechTexts(items: Iterable<PlaybackItem>, limit: Int = COUNT): List<String> {
        if (limit <= 0) return emptyList()
        val result = ArrayList<String>(limit)
        for (item in items) {
            if (result.size >= limit) break
            val text = speechTextFor(item) ?: continue
            result.add(text)
        }
        return result
    }

    /** Speech body for a queue item, or null when it would not be sent to cloud TTS. */
    fun speechTextFor(item: PlaybackItem): String? {
        // Chat titles are spoken through the same engine, so warm them too
        if (item is PlaybackItem.ChatTitle) return item.text.takeIf { it.isNotBlank() }
        if (item !is PlaybackItem.MessageItem) return null
        if (item.dropped || item.voiceNoteFileId != null) return null
        val cleaned = MessageCleaner.clean(item.text)
        if (MessageSpeechBody.shouldSkipSilently(cleaned, item.contentType, item.voiceNoteFileId)) return null
        return MessageSpeechBody.resolve(cleaned, item.contentType)?.takeIf { it.isNotBlank() }
    }
}
