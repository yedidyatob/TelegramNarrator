package com.example.telegramnarrator.data.openai

import android.content.Context
import com.example.telegramnarrator.data.speech.DiskAudioCache
import com.example.telegramnarrator.domain.openai.OpenAiTts
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-disk MP3 cache keyed by [OpenAiTts.cacheKey] so identical messages are not re-billed.
 * Unlimited size: every entry was paid for (Android may still clear cacheDir under storage pressure).
 */
@Singleton
class OpenAiSpeechCache @Inject constructor(
    @ApplicationContext context: Context
) {
    private val cache = DiskAudioCache(File(context.cacheDir, "openai_tts"))

    fun fileFor(text: String, voice: String, model: String): File =
        cache.fileFor(OpenAiTts.cacheKey(text, voice, model))

    fun getIfPresent(text: String, voice: String, model: String): File? =
        cache.get(OpenAiTts.cacheKey(text, voice, model))

    fun put(text: String, voice: String, model: String, bytes: ByteArray): File =
        cache.put(OpenAiTts.cacheKey(text, voice, model), bytes)

    fun remove(file: File) = cache.remove(file)
}
