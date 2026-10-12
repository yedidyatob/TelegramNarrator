package io.github.yedidyatob.telegramnarrator.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import androidx.core.text.BidiFormatter
import io.github.yedidyatob.telegramnarrator.MainActivity
import io.github.yedidyatob.telegramnarrator.R
import io.github.yedidyatob.telegramnarrator.domain.audio.PlaybackItem
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredAd

/** The playback notification (MediaStyle with pause/resume, next message, next chat, stop) and its texts. */
class PlaybackNotifications(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "PlaybackChannel"
        const val NOTIFICATION_ID = 1
    }

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

    fun build(text: String, isPaused: Boolean, session: MediaSessionCompat): Notification {
        val toggleIcon = if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause
        val toggleTitle = context.getString(if (isPaused) R.string.playback_resume else R.string.playback_pause)
        val toggleAction = if (isPaused) PlaybackService.ACTION_RESUME else PlaybackService.ACTION_PAUSE
        // Tapping the notification opens the app
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context, 4, openIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_narrator)
            .setContentIntent(openPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(toggleIcon, toggleTitle, serviceIntent(toggleAction, 3))
            .addAction(android.R.drawable.ic_media_next, context.getString(R.string.playback_skip_msg), serviceIntent(PlaybackService.ACTION_SKIP_MSG, 1))
            .addAction(android.R.drawable.ic_media_next, context.getString(R.string.playback_skip_chat), serviceIntent(PlaybackService.ACTION_SKIP_CHAT, 2))
            .addAction(android.R.drawable.ic_delete, context.getString(R.string.home_btn_stop), serviceIntent(PlaybackService.ACTION_STOP, 0))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 3)
                    .setMediaSession(session.sessionToken)
            )
            .build()
    }

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        context, requestCode, Intent(context, PlaybackService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE
    )

    /**
     * Wraps a chat title / sender / ad title in bidi isolation marks, so a Hebrew name inside an English
     * template ("Chat: חדשות") or the other way round keeps the template's order and punctuation.
     */
    private fun isolate(name: String): String = BidiFormatter.getInstance().unicodeWrap(name)
}
