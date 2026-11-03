package io.github.yedidyatob.telegramnarrator.data.speech

import java.io.File

/** Deletes the online engines' synthesized-speech caches (#69): spoken message text must not outlive a logout. */
fun interface SpeechCacheWiper {
    fun wipe()
}

object SpeechCaches {
    /** Gemini's, and the former OpenAI engine's directory (may remain from older installs). */
    val DIRECTORIES = listOf("gemini_tts", "openai_tts")

    /** Deletes every speech cache under [cacheDir]; returns whether all of them are gone. */
    fun wipe(cacheDir: File): Boolean =
        DIRECTORIES.map { File(cacheDir, it) }.all { !it.exists() || it.deleteRecursively() }
}
