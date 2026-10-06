package io.github.yedidyatob.telegramnarrator.domain.tts

import java.io.File

/** Result of asking a network TTS engine (OpenAI / Edge) for a message's audio. */
sealed class SpeechSynthesisOutcome {
    /**
     * Audio is ready on disk. [playbackSpeed] is applied by the MediaPlayer (1.0 when the engine already
     * baked the speech rate into the audio, like OpenAI's `speed`).
     */
    data class Ready(val file: File, val fromCache: Boolean, val playbackSpeed: Float = 1f) : SpeechSynthesisOutcome()

    /** The engine is not selected / nothing to synthesize: use system TTS silently. */
    object UseSystem : SpeechSynthesisOutcome()

    /** The user picked a network engine but it failed: show [reason] as a toast and use system TTS. */
    data class Fallback(val reason: String) : SpeechSynthesisOutcome()
}
