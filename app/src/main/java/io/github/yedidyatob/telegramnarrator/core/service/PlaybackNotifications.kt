package io.github.yedidyatob.telegramnarrator.core.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.text.BidiFormatter
import io.github.yedidyatob.telegramnarrator.R
import io.github.yedidyatob.telegramnarrator.domain.audio.PlaybackItem
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredAd

/**
 * The playback notification's texts. Media3's DefaultMediaNotificationProvider builds the notification
 * itself (MediaStyle, the session token, play/pause, next message, next chat, stop, the content intent).
 */
class PlaybackNotifications(private val context: Context) {

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_playback),
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Status line for [item], or null to keep the current one (a sponsored slot sets its own once the ad is known). */
    fun statusFor(item: PlaybackItem): String? = when (item) {
        is PlaybackItem.Intro -> context.getString(R.string.notification_chat, isolate(item.chatName))
        is PlaybackItem.ChatTitle -> context.getString(R.string.notification_chat, isolate(item.chatName))
        is PlaybackItem.MessageItem -> context.getString(
            R.string.notification_from,
            isolate(item.sender ?: context.getString(R.string.playback_unknown_sender))
        )
        is PlaybackItem.SponsoredSlot -> null
        else -> context.getString(R.string.notification_playing)
    }

    fun sponsoredStatus(ad: SponsoredAd, chatName: String): String = context.getString(
        if (ad.isRecommended) R.string.notification_recommended else R.string.notification_sponsored,
        isolate(ad.title.ifBlank { chatName })
    )

    /**
     * Wraps a chat title / sender / ad title in bidi isolation marks, so a Hebrew name inside an English
     * template ("Chat: חדשות") or the other way round keeps the template's order and punctuation.
     */
    private fun isolate(name: String): String = BidiFormatter.getInstance().unicodeWrap(name)

    companion object {
        const val CHANNEL_ID = "PlaybackChannel"
        const val NOTIFICATION_ID = 1
    }
}
