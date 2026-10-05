package com.example.telegramnarrator.data.openai

import com.example.telegramnarrator.data.tts.TtsPreferences
import com.example.telegramnarrator.domain.openai.OpenAiTts
import com.example.telegramnarrator.domain.tts.TtsVoiceLogic
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves OpenAI-synthesized MP3 for a message: cache hit or network call.
 * Returns null when OpenAI should not be used (disabled / no key / over limit) so callers fall back.
 */
@Singleton
class OpenAiSpeechSynthesizer @Inject constructor(
    private val preferences: TtsPreferences,
    private val keyStore: OpenAiKeyStore,
    private val cache: OpenAiSpeechCache,
    private val client: OpenAiSpeechClient
) {
    sealed class Outcome {
        data class Ready(val file: File, val fromCache: Boolean) : Outcome()
        /** OpenAI not configured / not selected — use system TTS silently. */
        object UseSystem : Outcome()
        /** User wanted OpenAI but synthesis failed — show toast and use system TTS. */
        data class Fallback(val reason: String) : Outcome()
    }

    fun isOpenAiEnabled(): Boolean = preferences.settings.value.openAi.enabled

    fun shouldPreferOpenAi(): Boolean {
        val opts = preferences.settings.value.openAi
        return opts.enabled && keyStore.hasApiKey()
    }

    fun synthesize(text: String): Outcome {
        val settings = preferences.settings.value
        val opts = settings.openAi
        if (!opts.enabled) return Outcome.UseSystem
        val apiKey = keyStore.getApiKey()
        if (apiKey == null) {
            return Outcome.Fallback("OpenAI TTS needs an API key. Using system voice.")
        }
        if (text.isBlank()) return Outcome.UseSystem
        if (!OpenAiTts.isWithinApiLimit(text)) {
            return Outcome.Fallback("Message too long for OpenAI TTS. Using system voice.")
        }

        val model = OpenAiTts.normalizeModel(opts.model)
        val voice = OpenAiTts.normalizeVoice(opts.voice)
        cache.getIfPresent(text, voice, model)?.let { return Outcome.Ready(it, fromCache = true) }

        return try {
            val speed = TtsVoiceLogic.clampRate(settings.speechRate)
            val bytes = client.synthesize(
                OpenAiSpeechClient.Request(
                    apiKey = apiKey,
                    model = model,
                    voice = voice,
                    text = text,
                    speed = speed
                )
            )
            val file = cache.put(text, voice, model, bytes)
            Outcome.Ready(file, fromCache = false)
        } catch (e: IOException) {
            android.util.Log.w("OpenAiSpeech", "TTS request failed: ${e.message}")
            Outcome.Fallback("OpenAI TTS failed. Using system voice.")
        } catch (e: Exception) {
            android.util.Log.w("OpenAiSpeech", "TTS unexpected error: ${e.javaClass.simpleName}")
            Outcome.Fallback("OpenAI TTS failed. Using system voice.")
        }
    }
}
