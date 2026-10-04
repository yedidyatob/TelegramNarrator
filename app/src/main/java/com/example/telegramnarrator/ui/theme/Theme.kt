package com.example.telegramnarrator.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = TelegramBlueNight,
    secondary = TelegramSecondaryNight,
    background = TelegramBackgroundNight,
    surface = TelegramSurfaceNight,
    onPrimary = TelegramSurfaceDay,
    onSecondary = TelegramSurfaceDay,
    onBackground = TelegramSurfaceDay,
    onSurface = TelegramSurfaceDay,
    surfaceVariant = TelegramDarkNight,
    onSurfaceVariant = TelegramSecondaryNight
)

private val LightColorScheme = lightColorScheme(
    primary = TelegramBlueDay,
    secondary = TelegramSecondaryDay,
    background = TelegramSurfaceDay,
    surface = TelegramSurfaceDay,
    onPrimary = TelegramSurfaceDay,
    onSecondary = TelegramDarkDay,
    onBackground = TelegramDarkDay,
    onSurface = TelegramDarkDay,
    surfaceVariant = TelegramSurfaceDay,
    onSurfaceVariant = TelegramSecondaryDay
)

@Composable
fun TelegramNarratorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+ but we might want our custom Telegram branding 
    // to overrule dynamic colours so it always feels like Telegram!
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
