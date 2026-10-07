package io.github.yedidyatob.telegramnarrator.domain.model

data class Chat(
    val id: Long,
    val title: String,
    val unreadCount: Int,
    val lastMessage: Message? = null,
    val order: Long = 0L,
    /** TDLib file id of the small chat photo (avatar), if the chat has one. */
    val photoFileId: Int? = null
)
