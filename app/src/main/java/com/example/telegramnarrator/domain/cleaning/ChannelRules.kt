package com.example.telegramnarrator.domain.cleaning

/** How "1. text" style list markers at the start of a line are handled. */
enum class NumberedListMode {
    /** Leave them alone */
    KEEP,
    /** Remove the marker: "1. text" -> "text" */
    STRIP,
    /** Make the number read naturally: "1. text" -> "1, text" */
    NATURAL
}

/** After a message matching [pattern] has been found, the next [count] messages are dropped too. */
data class DropNextRule(val pattern: Regex, val count: Int)

data class ReplaceRule(val pattern: Regex, val replacement: String)

/**
 * Everything that controls how the messages of one chat are cleaned before being read aloud.
 * See docs/channel-rules.md.
 */
data class CleaningPreset(
    /** Messages whose text matches any of these are not read (but marked as read). */
    val dropMessage: List<Regex> = emptyList(),
    /** Marker messages: the message itself and the next N messages are dropped. */
    val dropNext: List<DropNextRule> = emptyList(),
    /** Text matching these is removed from the messages that are read. */
    val cut: List<Regex> = emptyList(),
    /** Pattern -> replacement (Java regex replacement syntax, e.g. "$1"). */
    val replace: List<ReplaceRule> = emptyList(),
    /** false: URLs are removed instead of being read as "link". */
    val readLinks: Boolean = true,
    /** What a URL is read as when [readLinks] is true; null = the app's localized default. */
    val linkLabel: String? = null,
    /** Remove t.me / telegram.me links entirely (including any "?single" style suffix). */
    val removeTelegramLinks: Boolean = false,
    val numberedLists: NumberedListMode = NumberedListMode.KEEP,
    /** Literal strings removed from the text, e.g. "°". */
    val stripSymbols: List<String> = emptyList()
)

/** Which chats a [ChannelPreset] applies to; a chat matches if any of the criteria matches. */
data class ChannelMatch(
    val chatIds: Set<Long> = emptySet(),
    val titleEquals: String? = null,
    val titleContains: String? = null
) {
    fun matches(chatId: Long, title: String?): Boolean {
        if (chatId in chatIds) return true
        val t = title?.trim() ?: return false
        if (titleEquals != null && t.equals(titleEquals.trim(), ignoreCase = true)) return true
        if (titleContains != null && titleContains.isNotBlank() && t.contains(titleContains.trim(), ignoreCase = true)) return true
        return false
    }
}

data class ChannelPreset(
    val name: String,
    val match: ChannelMatch,
    val preset: CleaningPreset
)

data class ChannelRulesConfig(
    /** Used for every chat that no channel matches. */
    val default: CleaningPreset = CleaningPreset(),
    /** Checked in order, the first match wins. */
    val channels: List<ChannelPreset> = emptyList()
) {
    companion object {
        /** No rules at all: only the generic MessageCleaner applies. */
        val EMPTY = ChannelRulesConfig()
    }
}
