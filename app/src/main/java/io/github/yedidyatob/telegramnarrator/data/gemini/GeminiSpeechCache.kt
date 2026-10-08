package io.github.yedidyatob.telegramnarrator.data.gemini

import android.content.Context
import io.github.yedidyatob.telegramnarrator.domain.gemini.GeminiTts
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-disk WAV cache for Gemini TTS audio. Files are stored as `<sha256>.wav` under
 * `cacheDir/gemini_tts/`. The cache key includes the model id (via [GeminiTts.cacheKey]) so a future
 * model bump automatically produces new entries. Unlimited size: every entry was paid for (Android may
 * still clear cacheDir under storage pressure). Writes are atomic (temp + rename).
 */
@Singleton
class GeminiSpeechCache @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dir = File(context.cacheDir, "gemini_tts")

    private fun fileFor(key: String): File = File(dir, "$key.wav")

    /** Returns a cached WAV file or null. A cache hit touches the file's LRU timestamp. */
    fun getIfPresent(text: String, voice: String): File? {
        val file = fileFor(GeminiTts.cacheKey(text, voice))
        if (!file.isFile || file.length() <= 0L) return null
        file.setLastModified(System.currentTimeMillis())
        return file
    }

    /** Atomically writes [bytes] (a complete WAV) to the cache and returns the resulting [File]. */
    @Synchronized
    fun put(text: String, voice: String, bytes: ByteArray): File {
        dir.mkdirs()
        val file = fileFor(GeminiTts.cacheKey(text, voice))
        val tmp = File(dir, "${file.name}.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(file)) {
            file.writeBytes(bytes)
            tmp.delete()
        }
        file.setLastModified(System.currentTimeMillis())
        return file
    }

    /** Drops a cached file (e.g. one MediaPlayer could not play). */
    fun remove(file: File) {
        if (file.parentFile == dir) file.delete()
    }
}
