package io.github.yedidyatob.telegramnarrator.domain.onboarding

import io.github.yedidyatob.telegramnarrator.domain.tts.VoiceOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingFlowTest {

    @Test
    fun `android 13 without the permission asks for notifications last`() {
        assertEquals(
            listOf(OnboardingStep.WELCOME, OnboardingStep.PRIVACY, OnboardingStep.VOICE, OnboardingStep.NOTIFICATIONS),
            OnboardingFlow.steps(sdkInt = 33, notificationsGranted = false)
        )
    }

    @Test
    fun `no notification step when already granted or before android 13`() {
        val withoutPermission = listOf(OnboardingStep.WELCOME, OnboardingStep.PRIVACY, OnboardingStep.VOICE)
        assertEquals(withoutPermission, OnboardingFlow.steps(sdkInt = 34, notificationsGranted = true))
        assertEquals(withoutPermission, OnboardingFlow.steps(sdkInt = 32, notificationsGranted = false))
    }

    @Test
    fun `shown once and never to someone already signed in`() {
        assertTrue(OnboardingFlow.shouldShow(completed = false, signedIn = false))
        assertFalse(OnboardingFlow.shouldShow(completed = true, signedIn = false))
        assertFalse(OnboardingFlow.shouldShow(completed = false, signedIn = true))
    }

    private fun voice(locale: String, installed: Boolean = true, network: Boolean = false) =
        VoiceOption(name = locale, localeTag = locale, language = locale.substringBefore('-'), quality = 400, requiresNetwork = network, installed = installed)

    @Test
    fun `hebrew voice must be installed and offline`() {
        assertTrue(OnboardingFlow.hasHebrewVoice(listOf(voice("en-US"), voice("he-IL"))))
        assertFalse(OnboardingFlow.hasHebrewVoice(listOf(voice("en-US"))))
        assertFalse(OnboardingFlow.hasHebrewVoice(listOf(voice("he-IL", installed = false))))
        assertFalse(OnboardingFlow.hasHebrewVoice(listOf(voice("he-IL", network = true))))
        assertFalse(OnboardingFlow.hasHebrewVoice(emptyList()))
    }
}
