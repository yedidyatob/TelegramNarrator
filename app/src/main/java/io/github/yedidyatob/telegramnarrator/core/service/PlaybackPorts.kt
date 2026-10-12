package io.github.yedidyatob.telegramnarrator.core.service

import io.github.yedidyatob.telegramnarrator.core.audio.TtsManager
import io.github.yedidyatob.telegramnarrator.core.playback.narration.SpeechPort
import io.github.yedidyatob.telegramnarrator.core.playback.narration.SponsoredPort
import io.github.yedidyatob.telegramnarrator.data.edge.EdgeSpeechSynthesizer
import io.github.yedidyatob.telegramnarrator.data.openai.OpenAiSpeechSynthesizer
import io.github.yedidyatob.telegramnarrator.data.tts.TtsPreferences
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredAd
import io.github.yedidyatob.telegramnarrator.domain.sponsored.SponsoredMessagesRepository
import io.github.yedidyatob.telegramnarrator.domain.tts.SpeechProvider
import io.github.yedidyatob.telegramnarrator.domain.tts.SpeechSynthesisOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/** [SpeechPort] over the system [TtsManager] and the OpenAI / Edge synthesizers. */
class EngineSpeechPort(
    private val tts: TtsManager,
    private val preferences: TtsPreferences,
    private val openAi: OpenAiSpeechSynthesizer,
    private val edge: EdgeSpeechSynthesizer
) : SpeechPort {
    override val provider: SpeechProvider get() = preferences.settings.value.provider

    override suspend fun awaitReady() {
        tts.isInitialized.first { it }
    }

    override fun speak(text: String, onStart: () -> Unit, onDone: (completed: Boolean) -> Unit) =
        tts.speak(text, onStart = onStart, onDone = onDone)

    override fun stop() = tts.stop()

    override fun synthesize(provider: SpeechProvider, text: String): SpeechSynthesisOutcome = when (provider) {
        SpeechProvider.SYSTEM -> SpeechSynthesisOutcome.UseSystem
        SpeechProvider.OPENAI -> openAi.synthesize(text)
        SpeechProvider.EDGE -> edge.synthesize(text)
    }

    override fun discard(file: File) {
        openAi.discard(file)
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
