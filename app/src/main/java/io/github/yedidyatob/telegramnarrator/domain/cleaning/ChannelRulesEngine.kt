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
     * read, and with which text. Each message is decided on its own text (`dropMessage`).
     */
    fun evaluate(chatId: Long, title: String?, messages: List<Message>): List<Decision> {
        val preset = presetFor(chatId, title)
        return messages.map { message ->
            val dropped = preset.dropMessage.any { it.containsMatchIn(message.text) }
            Decision(
                message = message,
                dropped = dropped,
                text = if (dropped) "" else applyTextRules(message.text, preset)
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
