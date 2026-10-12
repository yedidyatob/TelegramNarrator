package io.github.yedidyatob.telegramnarrator.core.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.core.content.ContextCompat

/** Headphones unplugged / Bluetooth disconnected: [onNoisy] pauses instead of playing from the speaker. */
class BecomingNoisyReceiver(private val context: Context, private val onNoisy: () -> Unit) {
    private var registered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) onNoisy()
        }
    }

    fun register() {
        if (registered) return
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        registered = true
    }

    fun unregister() {
        if (!registered) return
        registered = false
        context.unregisterReceiver(receiver)
    }
}
