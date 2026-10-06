package io.github.yedidyatob.telegramnarrator.domain.cleaning

import io.github.yedidyatob.telegramnarrator.domain.model.Message

/**
 * Applies the per-channel [CleaningPreset]s to the messages of a chat. Runs before the generic
 * [io.github.yedidyatob.telegramnarrator.domain.audio.MessageCleaner], which stays the fallback for everything
 * the rules don't touch.
 */
class ChannelRulesEngine(
    private val config: ChannelRulesConfig = ChannelRulesConfig.EMPTY,
    /** The word a URL is read as when a preset has readLinks=true and no linkLabel (localized by the app). */
    private val defaultLinkLabel: () -> String = { "Link" }
) {

    /** What to do with one message of a batch. */
    data class Decision(
        val message: Message,
        /** Not read aloud, but still marked as read when its turn comes. */
        val dropped: Boolean,
        /**
         * Neither read nor marked: it belongs to a "drop the next N" group that continues in the
         * next batch, so it is left for the next run. Never set for dropped=false messages.
         */
        val deferred: Boolean,
        /** The message text after the rules (still to be run through MessageCleaner). */
        val text: String
    )

    companion object {
        private val URL_REGEX = Regex("(https?://\\S+|www\\.\\S+)", RegexOption.IGNORE_CASE)
        private val TELEGRAM_LINK_REGEX =
            Regex("(?:https?://)?(?:www\\.)?(?:t\\.me|telegram\\.me|telegram\\.dog)/\\S*", RegexOption.IGNORE_CASE)
        private val NUMBERED_LIST_MARKER = Regex("^[ \\t]*(\\d{1,3})[.)][ \\t]+", RegexOption.MULTILINE)
    }

    fun presetFor(chatId: Long, title: String?): CleaningPreset =
        config.channels.firstOrNull { it.match.matches(chatId, title) }?.preset ?: config.default

    /**
     * Decides for every message of [messages] (one chat, oldest first, as played) whether it is
     * read, and with which text.
     *
     * "Drop the next N" markers count messages in batch order. If the batch ends in the middle of such
     * a group and [moreUnreadFollows] is true (the group continues in the next batch), the marker and
     * the messages after it are deferred so that the next run sees the whole group. If there is nothing
     * after the batch, they are simply dropped.
     */
    fun evaluate(chatId: Long, title: String?, messages: List<Message>, moreUnreadFollows: Boolean): List<Decision> {
        val preset = presetFor(chatId, title)
        val dropped = BooleanArray(messages.size)
        var remaining = 0 // messages still to drop because of an earlier marker
        var lastMarkerIndex = -1

        messages.forEachIndexed { index, message ->
            val text = message.text
            if (remaining > 0) {
                dropped[index] = true
                remaining--
            }
            if (preset.dropMessage.any { it.containsMatchIn(text) }) dropped[index] = true

            val marker = preset.dropNext.filter { it.pattern.containsMatchIn(text) }.maxOfOrNull { it.count }
            if (marker != null) {
                dropped[index] = true
                if (marker > 0) {
                    remaining = maxOf(remaining, marker)
                    lastMarkerIndex = index
                }
            }
        }

        val deferFrom = if (remaining > 0 && moreUnreadFollows && lastMarkerIndex >= 0) lastMarkerIndex else messages.size

        return messages.mapIndexed { index, message ->
            val deferred = index >= deferFrom
            val isDropped = dropped[index] || deferred
            Decision(
                message = message,
                dropped = isDropped && !deferred,
                deferred = deferred,
                text = if (isDropped) "" else applyTextRules(message.text, preset)
            )
        }
    }

    /**
     * The cut / replace / link / symbol options of [preset] applied to [text]. Line breaks are kept;
     * the generic MessageCleaner collapses them afterwards.
     */
    fun applyTextRules(text: String, preset: CleaningPreset): String {
        var result = text
        preset.cut.forEach { result = it.replace(result, "") }
        preset.replace.forEach { result = it.pattern.replace(result, it.replacement) }

        if (preset.removeTelegramLinks) result = TELEGRAM_LINK_REGEX.replace(result, "")

        if (!preset.readLinks) {
            result = URL_REGEX.replace(result, "")
        } else {
            val label = preset.linkLabel ?: defaultLinkLabel()
            result = URL_REGEX.replace(result) { label }
        }

        preset.stripSymbols.filter { it.isNotEmpty() }.forEach { result = result.replace(it, "") }

        result = when (preset.numberedLists) {
            NumberedListMode.KEEP -> result
            NumberedListMode.STRIP -> NUMBERED_LIST_MARKER.replace(result, "")
            NumberedListMode.NATURAL -> NUMBERED_LIST_MARKER.replace(result) { "${it.groupValues[1]}, " }
        }
        return result
    }
}
