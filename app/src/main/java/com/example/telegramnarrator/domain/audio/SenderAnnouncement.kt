package com.example.telegramnarrator.domain.audio

/**
 * Whether to prepend "Message from X:" before a spoken message. Pure logic so the first-message /
 * lastSender interaction is unit-tested (the old bug compared lastSender *after* updating it).
 *
 * Rules:
 * - First spoken message of a chat: do **not** announce the sender (the "New chat: ..." intro already
 *   named the chat). Still record the sender so consecutive messages from the same person stay quiet.
 * - Later messages: announce only when the sender key changes.
 */
object SenderAnnouncement {

    data class Decision(
        /** True = speak "Message from X: …". */
        val announceSender: Boolean,
        /** Value to store as lastSender after this message starts. */
        val nextLastSender: String,
        /** Value of isFirstMessageInChat after this message starts (always false). */
        val nextIsFirstMessageInChat: Boolean,
        /** Snapshot of lastSender / isFirst before this decision (restored if the item is replayed after pause). */
        val previousLastSender: String?,
        val previousIsFirstMessageInChat: Boolean
    )

    /**
     * @param senderKey stable identity of the sender (empty string when unknown), compared for equality
     * @param lastSender last announced sender key, or null at the start of a chat
     * @param isFirstMessageInChat true until the first non-skipped message of the current chat is spoken
     */
    fun decide(
        senderKey: String,
        lastSender: String?,
        isFirstMessageInChat: Boolean
    ): Decision {
        val announce = !isFirstMessageInChat && senderKey != lastSender
        return Decision(
            announceSender = announce,
            nextLastSender = senderKey,
            nextIsFirstMessageInChat = false,
            previousLastSender = lastSender,
            previousIsFirstMessageInChat = isFirstMessageInChat
        )
    }
}
