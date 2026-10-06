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

    /** The user picked a network engine but it failed: tell the user [reason] (once per episode) and use system TTS. */
    data class Fallback(val reason: FallbackReason) : SpeechSynthesisOutcome()
}

/**
 * Why a network engine fell back to system TTS (#63). Each value has its own message (string resource); the
 * values are specific enough for the user to know what to do (fix the key, add credit, wait, check the network).
 */
enum class FallbackReason {
    OPENAI_NO_KEY,
    /** HTTP 401: the key is wrong or was revoked. */
    OPENAI_INVALID_KEY,
    /** HTTP 429 `insufficient_quota`: the OpenAI account is out of credit. */
    OPENAI_NO_CREDIT,
    /** HTTP 429 rate limit, still limited after the retries. */
    OPENAI_RATE_LIMITED,
    /** Over the input limit even after splitting, or HTTP 400 about the length. */
    OPENAI_TOO_LONG,
    /** Other HTTP 400: OpenAI refused this message. */
    OPENAI_BAD_REQUEST,
    /** HTTP 5xx. */
    OPENAI_SERVER_ERROR,
    OPENAI_TIMEOUT,
    OPENAI_FAILED,
    /** Edge WebSocket handshake HTTP 429. */
    EDGE_THROTTLED,
    /** Edge handshake refused (403 after the clock-skew retry, other HTTP codes, 5xx). */
    EDGE_UNAVAILABLE,
    EDGE_TIMEOUT,
    EDGE_FAILED,
    /** No connection at all (DNS / connect failed), any engine. */
    NO_NETWORK,
    /** The synthesized file could not be played. */
    UNPLAYABLE;

    /** Failures caused by the network or the service being down (not by this message or the account). */
    val isConnectivity: Boolean
        get() = this == NO_NETWORK || this == OPENAI_TIMEOUT || this == EDGE_TIMEOUT ||
            this == OPENAI_SERVER_ERROR || this == EDGE_UNAVAILABLE
}
