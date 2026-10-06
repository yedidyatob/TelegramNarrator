package io.github.yedidyatob.telegramnarrator.core.tts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.yedidyatob.telegramnarrator.R
import io.github.yedidyatob.telegramnarrator.domain.tts.FallbackReason
import io.github.yedidyatob.telegramnarrator.domain.tts.TtsFailureEpisodes
import io.github.yedidyatob.telegramnarrator.domain.tts.TtsFailures

/**
 * Tells the user why a network voice fell back to the system voice (#63): a toast once per failure episode
 * ([TtsFailureEpisodes]); for failures fixed on the OpenAI website (invalid key, no credit) also a notification
 * that opens the page. Prefetch failures only count when the network itself is down, so prefetching the next
 * messages never produces a message per item.
 */
class TtsFailureNotifier(private val context: Context) {
    private val episodes = TtsFailureEpisodes()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** A message's synthesis failed with [reason]. */
    fun onFailure(reason: FallbackReason) {
        if (!episodes.onFailure(reason)) return
        val message = context.getString(TtsFailureMessages.messageRes(reason))
        mainHandler.post { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
        TtsFailures.helpUrl(reason)?.let { url -> postHelpNotification(reason, message, url) }
    }

    /** A prefetch of an upcoming message failed: only connectivity problems are worth telling. */
    fun onPrefetchFailure(reason: FallbackReason) {
        if (reason.isConnectivity) onFailure(reason)
    }

    fun onNetworkSuccess() = episodes.onNetworkSuccess()

    private fun postHelpNotification(reason: FallbackReason, message: String, url: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel()
        val open = PendingIntent.getActivity(
            context,
            reason.ordinal,
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val action = TtsFailureMessages.helpActionRes(reason)?.let { context.getString(it) }
        val text = if (action != null) "$message $action" else message
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_narrator)
            .setContentTitle(context.getString(R.string.tts_alert_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call: the toast already told the user
        }
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_tts_alerts),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private companion object {
        const val CHANNEL_ID = "TtsAlerts"
        const val NOTIFICATION_ID = 2001
    }
}
