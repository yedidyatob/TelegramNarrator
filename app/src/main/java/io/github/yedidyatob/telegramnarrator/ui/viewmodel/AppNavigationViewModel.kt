package io.github.yedidyatob.telegramnarrator.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.yedidyatob.telegramnarrator.data.onboarding.OnboardingPreferences
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** App-level navigation state that isn't about the Telegram login: whether the onboarding was done. */
@HiltViewModel
class AppNavigationViewModel @Inject constructor(
    private val onboardingPreferences: OnboardingPreferences
) : ViewModel() {
    val onboardingCompleted: StateFlow<Boolean> = onboardingPreferences.completed

    /** Already signed in (e.g. updated from a version without onboarding): never show it. */
    fun onSignedIn() = onboardingPreferences.markCompleted()
}
