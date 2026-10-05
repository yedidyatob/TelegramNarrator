package com.example.telegramnarrator.data.edge

import android.content.Context
import com.example.telegramnarrator.data.speech.DiskAudioCache
import com.example.telegramnarrator.domain.edge.EdgeTts
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** On-disk MP3 cache for Edge TTS keyed by [EdgeTts.cacheKey] (hash of text + voice), LRU-trimmed. */
@Singleton
class EdgeSpeechCache @Inject constructor(
    @ApplicationContext context: Context
) {
    private val cache = DiskAudioCache(File(context.cacheDir, "edge_tts"), MAX_BYTES)

    fun getIfPresent(text: String, voice: String): File? = cache.get(EdgeTts.cacheKey(text, voice))

    fun put(text: String, voice: String, bytes: ByteArray): File = cache.put(EdgeTts.cacheKey(text, voice), bytes)

    fun remove(file: File) = cache.remove(file)

    private companion object {
        // ~100 MB of 48 kbps MP3 is several hours of speech
        const val MAX_BYTES = 100L * 1024 * 1024
    }
}
