package io.github.yedidyatob.telegramnarrator.ui.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.yedidyatob.telegramnarrator.core.audio.TtsManager
import io.github.yedidyatob.telegramnarrator.data.onboarding.OnboardingPreferences
import io.github.yedidyatob.telegramnarrator.data.openai.OpenAiKeyStore
import io.github.yedidyatob.telegramnarrator.data.tts.TtsPreferences
import io.github.yedidyatob.telegramnarrator.domain.onboarding.OnboardingFlow
import io.github.yedidyatob.telegramnarrator.domain.onboarding.OnboardingStep
import io.github.yedidyatob.telegramnarrator.domain.tts.SpeechProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** First-run onboarding: which step is shown, the voice choice, and finishing (shown once). */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val onboardingPreferences: OnboardingPreferences,
    private val ttsPreferences: TtsPreferences,
    private val openAiKeyStore: OpenAiKeyStore,
    private val ttsManager: TtsManager,
    private val savedState: SavedStateHandle
) : ViewModel() {

    private companion object {
        const val KEY_STEP = "step"
    }

    /** Fixed for the session, so granting notifications on the last step doesn't reshuffle the steps. */
    val steps: List<OnboardingStep> = OnboardingFlow.steps(
        sdkInt = Build.VERSION.SDK_INT,
        notificationsGranted = Build.VERSION.SDK_INT < OnboardingFlow.NOTIFICATION_PERMISSION_SDK ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    )

    val stepIndex: StateFlow<Int> = savedState.getStateFlow(KEY_STEP, 0)

    val provider: StateFlow<SpeechProvider> = ttsPreferences.settings
        .map { it.provider }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ttsPreferences.settings.value.provider)

    private val _hasOpenAiKey = MutableStateFlow(openAiKeyStore.hasApiKey())
    val hasOpenAiKey: StateFlow<Boolean> = _hasOpenAiKey.asStateFlow()

    private val _hebrewVoice = MutableStateFlow<Boolean?>(null)

    /** Whether the system engine has an offline Hebrew voice; null while the engine starts. */
    val hebrewVoice: StateFlow<Boolean?> = _hebrewVoice.asStateFlow()

    init {
        viewModelScope.launch {
            ttsManager.isInitialized.collect { ready ->
                _hebrewVoice.value = if (ready) OnboardingFlow.hasHebrewVoice(ttsManager.availableVoices()) else null
            }
        }
    }

    /** Back from the system TTS settings: a Hebrew voice may have been installed meanwhile. */
    fun recheckHebrewVoice() {
        if (!ttsManager.isInitialized.value) return
        ttsManager.reloadVoices()
        _hebrewVoice.update { OnboardingFlow.hasHebrewVoice(ttsManager.availableVoices()) }
    }

    val isLastStep: Boolean get() = stepIndex.value >= steps.lastIndex

    fun next() {
        if (!isLastStep) savedState[KEY_STEP] = stepIndex.value + 1
    }

    /** False when already on the first step (the system back then leaves the app as usual). */
    fun back(): Boolean {
        if (stepIndex.value == 0) return false
        savedState[KEY_STEP] = stepIndex.value - 1
        return true
    }

    fun selectProvider(provider: SpeechProvider) {
        ttsPreferences.update { it.copy(provider = provider) }
    }

    fun saveOpenAiKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return
        openAiKeyStore.setApiKey(trimmed)
        _hasOpenAiKey.value = openAiKeyStore.hasApiKey()
    }

    /** Finished or skipped: never shown again. */
    fun finish() = onboardingPreferences.markCompleted()
}
