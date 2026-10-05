package com.example.telegramnarrator.data.openai

import android.content.Context
import com.example.telegramnarrator.domain.openai.OpenAiTts
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** On-disk MP3 cache keyed by [OpenAiTts.cacheKey] so identical messages are not re-billed. */
@Singleton
class OpenAiSpeechCache @Inject constructor(
    @ApplicationContext context: Context
) {
    private val dir: File = File(context.cacheDir, "openai_tts").also { it.mkdirs() }

    fun fileFor(text: String, voice: String, model: String): File {
        val key = OpenAiTts.cacheKey(text, voice, model)
        return File(dir, "$key.mp3")
    }

    fun getIfPresent(text: String, voice: String, model: String): File? {
        val file = fileFor(text, voice, model)
        return if (file.isFile && file.length() > 0L) file else null
    }

    fun put(text: String, voice: String, model: String, bytes: ByteArray): File {
        val file = fileFor(text, voice, model)
        val tmp = File(dir, "${file.name}.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(file)) {
            file.writeBytes(bytes)
            tmp.delete()
        }
        return file
    }
}
