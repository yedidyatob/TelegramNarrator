package com.example.telegramnarrator.data.edge

import com.example.telegramnarrator.domain.edge.EdgeTts
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal blocking client for the (unofficial) Edge "Read aloud" TTS WebSocket, ported from the
 * `edge-tts` Python library. One WebSocket per text chunk; MP3 chunks are concatenated.
 * Never logs the message text. Call from a background thread.
 */
@Singleton
class EdgeTtsClient @Inject constructor() {

    /** Handshake rejected with an HTTP status (403 usually means our clock is off: Sec-MS-GEC mismatch). */
    class HandshakeException(val code: Int, val serverDate: String?) : IOException("Edge TTS handshake HTTP $code")

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        .writeTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        .build()

    // Server-minus-device clock difference, learned from a 403's Date header (like edge-tts' DRM class)
    @Volatile private var clockSkewSeconds = 0L

    /**
     * @return MP3 bytes for [text] spoken by [voice]
     * @throws IOException on network / protocol errors or when no audio was received
     */
    fun synthesize(text: String, voice: String): ByteArray {
        val chunks = EdgeTts.prepareTextChunks(text)
        if (chunks.isEmpty()) throw IOException("Edge TTS: nothing to synthesize")
        val out = ByteArrayOutputStream()
        for (chunk in chunks) out.write(synthesizeChunkWithSkewRetry(chunk, voice))
        return out.toByteArray()
    }

    private fun synthesizeChunkWithSkewRetry(escapedChunk: String, voice: String): ByteArray {
        return try {
            synthesizeChunk(escapedChunk, voice)
        } catch (e: HandshakeException) {
            val serverNow = EdgeTts.parseHttpDate(e.serverDate)
            if (e.code != 403 || serverNow == null) throw e
            clockSkewSeconds = serverNow - System.currentTimeMillis() / 1000
            synthesizeChunk(escapedChunk, voice)
        }
    }

    private fun synthesizeChunk(escapedChunk: String, voice: String): ByteArray {
        val nowSeconds = System.currentTimeMillis() / 1000 + clockSkewSeconds
        val url = EdgeTts.buildWssUrl(EdgeTts.newConnectionId(), EdgeTts.secMsGec(nowSeconds))
        val request = Request.Builder()
            .url(url)
            .header("Pragma", "no-cache")
            .header("Cache-Control", "no-cache")
            .header("Origin", EdgeTts.ORIGIN)
            .header("User-Agent", EdgeTts.USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cookie", "muid=${EdgeTts.newMuid()};")
            .build()

        val audio = ByteArrayOutputStream()
        val done = CountDownLatch(1)
        val error = AtomicReference<IOException?>(null)
        val finished = AtomicBoolean(false)
        // The service streams audio roughly 4-5x faster than real time, so long chunks legitimately take a
        // while: time out on inactivity rather than on the total duration
        val lastActivity = AtomicLong(System.nanoTime())

        fun fail(e: IOException) {
            if (finished.compareAndSet(false, true)) {
                error.set(e)
                done.countDown()
            }
        }

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val timestamp = EdgeTts.dateToString(System.currentTimeMillis())
                webSocket.send(EdgeTts.speechConfigMessage(timestamp))
                val ssml = EdgeTts.buildSsml(voice, escapedChunk)
                webSocket.send(EdgeTts.ssmlMessage(EdgeTts.newConnectionId(), timestamp, ssml))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                lastActivity.set(System.nanoTime())
                when (EdgeTts.parseTextFrameHeaders(text)["Path"]) {
                    "turn.end" -> {
                        if (finished.compareAndSet(false, true)) done.countDown()
                        webSocket.close(NORMAL_CLOSURE, null)
                    }
                    "turn.start", "response", "audio.metadata" -> Unit
                    else -> {
                        fail(IOException("Edge TTS: unexpected text frame"))
                        webSocket.cancel()
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                lastActivity.set(System.nanoTime())
                val frame = EdgeTts.parseBinaryFrame(bytes.toByteArray())
                if (frame == null || frame.path != "audio") {
                    fail(IOException("Edge TTS: malformed binary frame"))
                    webSocket.cancel()
                    return
                }
                // The stream ends with an empty frame without Content-Type; ignore it
                if (frame.audio.isEmpty()) return
                if (frame.contentType != null && frame.contentType != "audio/mpeg") {
                    fail(IOException("Edge TTS: unexpected content type"))
                    webSocket.cancel()
                    return
                }
                synchronized(audio) { audio.write(frame.audio) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(NORMAL_CLOSURE, null)
                fail(IOException("Edge TTS: connection closed before the end of the audio ($code)"))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                fail(IOException("Edge TTS: connection closed before the end of the audio ($code)"))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (response != null) {
                    fail(HandshakeException(response.code, response.header("Date")))
                } else {
                    fail(t as? IOException ?: IOException("Edge TTS: ${t.javaClass.simpleName}", t))
                }
            }
        }

        val socket = http.newWebSocket(request, listener)
        val startedAt = System.nanoTime()
        while (!done.await(POLL_MS, TimeUnit.MILLISECONDS)) {
            val now = System.nanoTime()
            val idle = TimeUnit.NANOSECONDS.toSeconds(now - lastActivity.get())
            val total = TimeUnit.NANOSECONDS.toSeconds(now - startedAt)
            if (idle >= IDLE_TIMEOUT_S || total >= MAX_TOTAL_S) {
                socket.cancel()
                throw IOException("Edge TTS: timed out")
            }
        }
        error.get()?.let { throw it }
        val bytes = synchronized(audio) { audio.toByteArray() }
        if (bytes.isEmpty()) throw IOException("Edge TTS: no audio received")
        return bytes
    }

    private companion object {
        const val CONNECT_TIMEOUT_S = 10L
        const val READ_TIMEOUT_S = 30L
        /** No frame for this long (including the connect / handshake) = give up. */
        const val IDLE_TIMEOUT_S = 15L
        /** Hard cap per chunk (a full 4096-byte chunk is a few minutes of speech). */
        const val MAX_TOTAL_S = 180L
        const val POLL_MS = 250L
        const val NORMAL_CLOSURE = 1000
    }
}
