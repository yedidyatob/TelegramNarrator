package io.github.yedidyatob.telegramnarrator.domain.onboarding

import io.github.yedidyatob.telegramnarrator.domain.tts.TtsVoiceLogic
import io.github.yedidyatob.telegramnarrator.domain.tts.VoiceOption

/** Steps of the first-run onboarding, in order. */
enum class OnboardingStep { WELCOME, PRIVACY, VOICE, NOTIFICATIONS }

object OnboardingFlow {
    /** Android 13 (TIRAMISU): notifications became a runtime permission. */
    const val NOTIFICATION_PERMISSION_SDK = 33

    /**
     * The steps to show. The notification step only appears on Android 13+ when the permission isn't granted
     * yet (older versions grant it at install).
     */
    fun steps(sdkInt: Int, notificationsGranted: Boolean): List<OnboardingStep> =
        OnboardingStep.values().filter { step ->
            step != OnboardingStep.NOTIFICATIONS || (sdkInt >= NOTIFICATION_PERMISSION_SDK && !notificationsGranted)
        }

    /**
     * Whether onboarding should be shown at all: once, to people who aren't signed in yet. Someone already
     * signed in (an update from a version without onboarding) goes straight to Home.
     */
    fun shouldShow(completed: Boolean, signedIn: Boolean): Boolean = !completed && !signedIn

    /** True when the system engine has a Hebrew voice that works offline (the app's main language). */
    fun hasHebrewVoice(voices: List<VoiceOption>): Boolean =
        TtsVoiceLogic.usableVoices(voices).any { it.language == TtsVoiceLogic.HEBREW }
}
