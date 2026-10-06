package io.github.yedidyatob.telegramnarrator.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

private val Base = Typography()

// No extra tracking anywhere: letter-spacing breaks the joined look of Hebrew/Arabic script and spreads Hebrew
// letters apart (Material's defaults add 0.1-0.5 sp to body and label styles)
private fun TextStyle.untracked() = copy(letterSpacing = 0.sp)

/** All 15 Material 3 type styles with the default sizes and weights, Hebrew-friendly (no letter-spacing). */
val Typography = Typography(
    displayLarge = Base.displayLarge.untracked(),
    displayMedium = Base.displayMedium.untracked(),
    displaySmall = Base.displaySmall.untracked(),
    headlineLarge = Base.headlineLarge.untracked(),
    headlineMedium = Base.headlineMedium.untracked(),
    headlineSmall = Base.headlineSmall.untracked(),
    titleLarge = Base.titleLarge.untracked(),
    titleMedium = Base.titleMedium.untracked(),
    titleSmall = Base.titleSmall.untracked(),
    bodyLarge = Base.bodyLarge.untracked(),
    bodyMedium = Base.bodyMedium.untracked(),
    bodySmall = Base.bodySmall.untracked(),
    labelLarge = Base.labelLarge.untracked(),
    labelMedium = Base.labelMedium.untracked(),
    labelSmall = Base.labelSmall.untracked()
)
