package io.github.yedidyatob.telegramnarrator.data.tdlib

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Lossless fan-out of TDLib updates (#13). [publish] never blocks or drops (it is called on TDLib's own thread):
 * each collector of [flow] has its own unbounded queue, so a slow collector (e.g. the chat list during the
 * initial UpdateNewChat burst) only delays itself and never makes others, or itself, lose updates.
 * A collector receives the items published after it started collecting.
 */
class UpdateBus<T> {
    private val subscribers = CopyOnWriteArrayList<Channel<T>>()

    val subscriberCount: Int get() = subscribers.size

    fun publish(item: T) {
        for (subscriber in subscribers) subscriber.trySend(item)
    }

    val flow: Flow<T> = flow {
        val channel = Channel<T>(Channel.UNLIMITED)
        subscribers += channel
        try {
            for (item in channel) emit(item)
        } finally {
            subscribers -= channel
            channel.close()
        }
    }
}
