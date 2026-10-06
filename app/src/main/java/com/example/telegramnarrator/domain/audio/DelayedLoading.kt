package com.example.telegramnarrator.domain.audio

/**
 * Timing rules for the "Preparing audio…" indicator (pure, unit tested; the caller supplies the clock).
 *
 * A load is started with [begin] and finished with [end]. It only becomes visible once it has been pending
 * for [delayMs], so cache hits and fast loads never flash the indicator. Starting a new load supersedes the
 * previous one: a late [end] of an older load is ignored. Not thread-safe; callers synchronize.
 */
class DelayedLoading(val delayMs: Long = DEFAULT_DELAY_MS) {
    private var currentToken = 0L
    private var startedAtMs: Long? = null

    /** Starts a load at [nowMs] and returns its token (pass it to [end]). */
    fun begin(nowMs: Long): Long {
        currentToken++
        startedAtMs = nowMs
        return currentToken
    }

    /** Finishes the load [token]; returns false (and changes nothing) when a newer load replaced it. */
    fun end(token: Long): Boolean {
        if (token != currentToken || startedAtMs == null) return false
        startedAtMs = null
        return true
    }

    /** Drops any pending load (skip / pause / stop / next queue item). */
    fun clear() {
        currentToken++
        startedAtMs = null
    }

    fun isPending(token: Long): Boolean = token == currentToken && startedAtMs != null

    /** Whether the indicator should be shown at [nowMs]. */
    fun isVisible(nowMs: Long): Boolean {
        val started = startedAtMs ?: return false
        return nowMs - started >= delayMs
    }

    /** When the current load becomes visible (null when nothing is pending). */
    fun visibleAtMs(): Long? = startedAtMs?.plus(delayMs)

    companion object {
        /** Loads shorter than this (typically disk-cache hits) never show the indicator. */
        const val DEFAULT_DELAY_MS = 300L
    }
}
