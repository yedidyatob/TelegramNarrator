package io.github.yedidyatob.telegramnarrator.domain.tts

import java.io.File

/** Result of asking a network TTS engine (Gemini / Edge) for a message's audio. */
sealed class SpeechSynthesisOutcome {
    /**
     * Audio is ready on disk. [playbackSpeed] is applied by the MediaPlayer (1.0 when the engine caches
     * at normal speed, like Gemini; Edge also caches at normal speed and applies rate at playback).
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
    // ---- Gemini ---------------------------------------------------------------------------------
    GEMINI_NO_KEY,
    /** HTTP 401 or 403: the key is wrong, was revoked, or lacks permission. */
    GEMINI_INVALID_KEY,
    /** HTTP 429 with "RESOURCE_EXHAUSTED" and quota hint: the account is out of credit. */
    GEMINI_NO_CREDIT,
    /** HTTP 429 rate limit, still limited after the retries. */
    GEMINI_RATE_LIMITED,
    /** HTTP 400: Gemini refused this request (bad voice, input too long after splitting, etc.). */
    GEMINI_BAD_REQUEST,
    /** HTTP 5xx. */
    GEMINI_SERVER_ERROR,
    GEMINI_TIMEOUT,
    /** Successful response but no audio data in the payload. */
    GEMINI_NO_AUDIO,
    GEMINI_FAILED,

    // ---- Edge -----------------------------------------------------------------------------------
    /** Edge WebSocket handshake HTTP 429. */
    EDGE_THROTTLED,
    /** Edge handshake refused (403 after the clock-skew retry, other HTTP codes, 5xx). */
    EDGE_UNAVAILABLE,
    EDGE_TIMEOUT,
    EDGE_FAILED,

    // ---- Shared ---------------------------------------------------------------------------------
    /** No connection at all (DNS / connect failed), any engine. */
    NO_NETWORK,
    /** The synthesized file could not be played. */
    UNPLAYABLE;

    /** Failures caused by the network or the service being down (not by this message or the account). */
    val isConnectivity: Boolean
        get() = this == NO_NETWORK || this == GEMINI_TIMEOUT || this == EDGE_TIMEOUT ||
            this == GEMINI_SERVER_ERROR || this == EDGE_UNAVAILABLE
}
