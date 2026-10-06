package io.github.yedidyatob.telegramnarrator.domain.audio

/**
 * How a voice-note message is handled in the playback queue.
 *
 * Voice notes are **never** announced with a spoken "Voice note" / "הודעה קולית" label.
 * If a local file path is available, the queue plays that audio via MediaPlayer; otherwise the
 * item is skipped silently (still marked read with the next item, like a caption-less photo).
 */
object VoiceNotePlayback {
    sealed class Outcome {
        data class Play(val path: String) : Outcome()
        data object SkipSilently : Outcome()
    }

    fun afterDownload(path: String?): Outcome =
        if (path != null) Outcome.Play(path) else Outcome.SkipSilently
}
