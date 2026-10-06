package io.github.yedidyatob.telegramnarrator.data.appearance

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Look-and-feel choices: "Use system colors" (Material You dynamic color, Android 12+). Stored on the device. */
@Singleton
class AppearancePreferences @Inject constructor(@ApplicationContext context: Context) {
    private companion object {
        const val FILE = "appearance"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
    }

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Dynamic color needs Android 12 (S); older devices never show the option. */
    val dynamicColorAvailable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private val _dynamicColor = MutableStateFlow(dynamicColorAvailable && prefs.getBoolean(KEY_DYNAMIC_COLOR, false))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    fun setDynamicColor(enabled: Boolean) {
        if (!dynamicColorAvailable) return
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        _dynamicColor.value = enabled
    }
}
