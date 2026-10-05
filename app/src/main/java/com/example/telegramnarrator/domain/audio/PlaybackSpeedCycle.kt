package com.example.telegramnarrator.domain.audio

/**
 * Discrete playback-speed steps shown in the Home top bar. Values stay within
 * [com.example.telegramnarrator.domain.tts.TtsVoiceLogic] rate bounds. The voice-settings slider
 * remains the full-range editor; this cycle only flips between a few presets, writing the same
 * speech-rate preference.
 */
object PlaybackSpeedCycle {
    val SPEEDS: List<Float> = listOf(1.0f, 1.25f, 1.5f, 2.0f)

    /** Next speed in the cycle; unknown/custom rates jump back to 1.0. */
    fun next(current: Float): Float {
        val index = SPEEDS.indexOfFirst { approxEqual(it, current) }
        return if (index < 0) SPEEDS.first() else SPEEDS[(index + 1) % SPEEDS.size]
    }

    private fun approxEqual(a: Float, b: Float): Boolean = kotlin.math.abs(a - b) < 0.001f
}
