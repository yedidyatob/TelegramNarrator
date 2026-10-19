package io.github.yedidyatob.telegramnarrator.domain.avatar

/** Telegram-style avatar gradients (ARGB, top to bottom), chosen by chat id; shared by the app and Auto artwork. */
object AvatarPalette {
    val gradients: List<Pair<Long, Long>> = listOf(
        0xFFFF885E to 0xFFFF516A,
        0xFFFFCD6A to 0xFFFFA85C,
        0xFFE0A2F3 to 0xFFD669ED,
        0xFFA0DE7E to 0xFF54CB68,
        0xFF53EDD6 to 0xFF28C9B7,
        0xFF72D5FD to 0xFF2A9EF1,
        0xFFFFA8A8 to 0xFFFF719A
    )

    fun forChat(chatId: Long): Pair<Long, Long> = gradients[Math.floorMod(chatId, gradients.size.toLong()).toInt()]
}
