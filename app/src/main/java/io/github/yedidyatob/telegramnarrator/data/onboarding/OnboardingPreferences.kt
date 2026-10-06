package io.github.yedidyatob.telegramnarrator.data.onboarding

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the first-run onboarding was finished or skipped (shown once). Stored on this device only. */
@Singleton
class OnboardingPreferences @Inject constructor(@ApplicationContext context: Context) {
    private companion object {
        const val FILE = "onboarding"
        const val KEY_COMPLETED = "completed_v1"
    }

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _completed = MutableStateFlow(prefs.getBoolean(KEY_COMPLETED, false))
    val completed: StateFlow<Boolean> = _completed.asStateFlow()

    fun markCompleted() {
        if (_completed.value) return
        prefs.edit().putBoolean(KEY_COMPLETED, true).apply()
        _completed.value = true
    }
}
