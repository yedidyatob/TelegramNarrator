package com.example.telegramnarrator.data.openai

import com.example.telegramnarrator.data.tts.TtsPreferences
import com.example.telegramnarrator.domain.openai.OpenAiTts
import com.example.telegramnarrator.domain.tts.SpeechProvider
import com.example.telegramnarrator.domain.tts.SpeechSynthesisOutcome
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
    fun isOpenAiEnabled(): Boolean = preferences.settings.value.provider == SpeechProvider.OPENAI

    fun shouldPreferOpenAi(): Boolean = isOpenAiEnabled() && keyStore.hasApiKey()

    /** A cached file that turned out to be unplayable is dropped so the next attempt re-fetches it. */
    fun discard(file: File) = cache.remove(file)

    fun synthesize(text: String): SpeechSynthesisOutcome {
        if (!isOpenAiEnabled()) return SpeechSynthesisOutcome.UseSystem
        return synthesizeWithSavedKey(text)
    }

    /** "Test voice" in settings: the selected model / voice with the saved key (the caller checks a key is saved). */
    fun synthesizeForTest(text: String): SpeechSynthesisOutcome = synthesizeWithSavedKey(text)

    private fun synthesizeWithSavedKey(text: String): SpeechSynthesisOutcome {
        val settings = preferences.settings.value
        val opts = settings.openAi
        val apiKey = keyStore.getApiKey()
        if (apiKey == null) {
            return SpeechSynthesisOutcome.Fallback("OpenAI TTS needs an API key. Using system voice.")
        }
        if (text.isBlank()) return SpeechSynthesisOutcome.UseSystem
        if (!OpenAiTts.isWithinApiLimit(text)) {
            return SpeechSynthesisOutcome.Fallback("Message too long for OpenAI TTS. Using system voice.")
        }

        val model = OpenAiTts.normalizeModel(opts.model)
        val voice = OpenAiTts.normalizeVoice(opts.voice)
        cache.getIfPresent(text, voice, model)?.let { return SpeechSynthesisOutcome.Ready(it, fromCache = true) }

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
            SpeechSynthesisOutcome.Ready(file, fromCache = false)
        } catch (e: IOException) {
            android.util.Log.w("OpenAiSpeech", "TTS request failed: ${e.message}")
            SpeechSynthesisOutcome.Fallback("OpenAI TTS failed. Using system voice.")
        } catch (e: Exception) {
            android.util.Log.w("OpenAiSpeech", "TTS unexpected error: ${e.javaClass.simpleName}")
            SpeechSynthesisOutcome.Fallback("OpenAI TTS failed. Using system voice.")
        }
    }
}
