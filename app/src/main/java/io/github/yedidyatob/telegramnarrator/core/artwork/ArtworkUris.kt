package io.github.yedidyatob.telegramnarrator.core.artwork

/** content:// URIs of chat artwork served by [ChatArtworkProvider] (pure string logic, unit-tested). */
object ArtworkUris {
    const val AUTHORITY_SUFFIX = ".artwork"
    private const val CHAT_SEGMENT = "chat"

    fun authority(packageName: String) = packageName + AUTHORITY_SUFFIX

    fun forChat(packageName: String, chatId: Long): String = "content://${authority(packageName)}/$CHAT_SEGMENT/$chatId"

    /** The chat id of a provider path such as "/chat/-1001234", or null for anything else. */
    fun chatIdOf(pathSegments: List<String>): Long? =
        pathSegments.takeIf { it.size == 2 && it[0] == CHAT_SEGMENT }?.get(1)?.toLongOrNull()
}
