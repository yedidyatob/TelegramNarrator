package io.github.yedidyatob.telegramnarrator.ui.theme

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
    onPrimary = TelegramSurfaceDay,
    primaryContainer = TelegramBlueContainerNight,
    onPrimaryContainer = TelegramOnBlueContainerNight,
    secondary = TelegramSecondaryNight,
    onSecondary = TelegramSurfaceDay,
    secondaryContainer = TelegramSecondaryContainerNight,
    onSecondaryContainer = TelegramOnSecondaryContainerNight,
    tertiary = TelegramTertiaryNight,
    background = TelegramBackgroundNight,
    onBackground = TelegramSurfaceDay,
    surface = TelegramSurfaceNight,
    onSurface = TelegramSurfaceDay,
    surfaceVariant = TelegramDarkNight,
    onSurfaceVariant = TelegramOnSurfaceVariantNight,
    outline = TelegramOutlineNight,
    outlineVariant = TelegramOutlineVariantNight,
    surfaceContainerLowest = TelegramContainerLowestNight,
    surfaceContainerLow = TelegramContainerLowNight,
    surfaceContainer = TelegramContainerNight,
    surfaceContainerHigh = TelegramContainerHighNight,
    surfaceContainerHighest = TelegramContainerHighestNight
)

private val LightColorScheme = lightColorScheme(
    primary = TelegramBlueDay,
    onPrimary = TelegramSurfaceDay,
    primaryContainer = TelegramBlueContainerDay,
    onPrimaryContainer = TelegramOnBlueContainerDay,
    secondary = TelegramSecondaryDay,
    onSecondary = TelegramDarkDay,
    secondaryContainer = TelegramSecondaryContainerDay,
    onSecondaryContainer = TelegramOnSecondaryContainerDay,
    tertiary = TelegramTertiaryDay,
    background = TelegramSurfaceDay,
    onBackground = TelegramDarkDay,
    surface = TelegramSurfaceDay,
    onSurface = TelegramDarkDay,
    surfaceVariant = TelegramSurfaceVariantDay,
    onSurfaceVariant = TelegramOnSurfaceVariantDay,
    outline = TelegramOutlineDay,
    outlineVariant = TelegramOutlineVariantDay,
    surfaceContainerLowest = TelegramContainerLowestDay,
    surfaceContainerLow = TelegramContainerLowDay,
    surfaceContainer = TelegramContainerDay,
    surfaceContainerHigh = TelegramContainerHighDay,
    surfaceContainerHighest = TelegramContainerHighestDay
)

@Composable
fun TelegramNarratorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Telegram-blue branding by default; "Use system colors" (Home ⋮ menu, Android 12+) switches to Material You
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
