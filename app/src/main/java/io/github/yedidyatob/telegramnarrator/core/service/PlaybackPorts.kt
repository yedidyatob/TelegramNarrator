package io.github.yedidyatob.telegramnarrator.core.service

import io.github.yedidyatob.telegramnarrator.core.audio.TtsManager
import io.github.yedidyatob.telegramnarrator.core.playback.narration.SpeechPort
import io.github.yedidyatob.telegramnarrator.core.playback.narration.SponsoredPort
import io.github.yedidyatob.telegramnarrator.data.edge.EdgeSpeechSynthesizer
import io.github.yedidyatob.telegramnarrator.data.gemini.GeminiSpeechSynthesizer
import io.github.yedidyatob.telegramnarrator.data.tts.TtsPreferences
import io.github.yedidyatob.telegramnarrator.domain.language.ChatLanguagePolicy
import io.github.yedidyatob.telegramnarrator.domain.language.ChatLanguageResolver
import java.util.Locale
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredAd
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredMessagesRepository
import io.github.yedidyatob.telegramnarrator.domain.tts.SpeechProvider
import io.github.yedidyatob.telegramnarrator.domain.tts.SpeechSynthesisOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/** [SpeechPort] over the system [TtsManager] and the Gemini / Edge synthesizers. */
class EngineSpeechPort(
    private val tts: TtsManager,
    private val preferences: TtsPreferences,
    private val gemini: GeminiSpeechSynthesizer,
    private val edge: EdgeSpeechSynthesizer,
    private val languages: ChatLanguageResolver
) : SpeechPort {
    /** The voice language for [text] of [chatId]: the chat's language unless the text is clearly another script (#89). */
    private fun languageFor(chatId: Long?, text: String): String = ChatLanguagePolicy.utteranceLanguage(
        chatId?.let { languages.languageOf(it)?.language },
        text,
        Locale.getDefault().language
    )

    override fun chatLanguage(chatId: Long): String = languages.languageCodeOf(chatId)

    override val provider: SpeechProvider get() = preferences.settings.value.provider

    override val speechRate: Float get() = preferences.settings.value.speechRate

    override fun settingsKey(): Any = preferences.settings.value

    override suspend fun awaitReady() {
        tts.isInitialized.first { it }
    }

    override fun speak(
        text: String,
        languageSource: String,
        chatId: Long?,
        onStart: () -> Unit,
        onRange: (start: Int) -> Unit,
        onDone: (completed: Boolean) -> Unit
    ) = tts.speak(
        text,
        language = Locale.forLanguageTag(languageFor(chatId, languageSource)),
        onStart = onStart,
        onRange = onRange,
        onDone = onDone
    )

    override fun stop() = tts.stop()

    override fun synthesize(provider: SpeechProvider, text: String, chatId: Long?): SpeechSynthesisOutcome = when (provider) {
        SpeechProvider.SYSTEM -> SpeechSynthesisOutcome.UseSystem
        SpeechProvider.GEMINI -> gemini.synthesize(text)
        SpeechProvider.EDGE -> edge.synthesize(text, languageFor(chatId, text))
    }

    override fun discard(file: File) {
        gemini.discard(file)
        edge.discard(file)
    }
}

/** [SpeechPort]'s sibling for sponsored messages; prefetch and view reports run in [scope]. */
class RepositorySponsoredPort(
    private val repository: SponsoredMessagesRepository,
    private val scope: CoroutineScope
) : SponsoredPort {
    override fun prefetch(chatId: Long) {
        scope.launch { repository.adsFor(chatId) }
    }

    override suspend fun adFor(chatId: Long): SponsoredAd? = repository.adFor(chatId)

    override fun reportViewed(ad: SponsoredAd) {
        scope.launch { repository.reportViewed(ad) }
    }
}
