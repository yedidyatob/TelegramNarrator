package io.github.yedidyatob.telegramnarrator.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.yedidyatob.telegramnarrator.data.appearance.AppearancePreferences
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** "Use system colors" (Material You) in Home's ⋮ menu; the theme in MainActivity follows the same preference. */
@HiltViewModel
class AppearanceViewModel @Inject constructor(private val preferences: AppearancePreferences) : ViewModel() {
    val dynamicColorAvailable: Boolean get() = preferences.dynamicColorAvailable
    val dynamicColor: StateFlow<Boolean> = preferences.dynamicColor

    fun setDynamicColor(enabled: Boolean) = preferences.setDynamicColor(enabled)
}
