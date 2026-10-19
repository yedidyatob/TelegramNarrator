package io.github.yedidyatob.telegramnarrator.core.diagnostics

import java.io.File

/**
 * Append-only text log capped at [maxBytes]: when an append would pass the cap, the oldest half is dropped
 * (at a line boundary). Plain java.io so it is unit-tested; callers serialize access.
 */
class RollingLogFile(val file: File, private val maxBytes: Int = MAX_BYTES) {
    fun append(line: String) {
        file.parentFile?.mkdirs()
        val bytes = (line.trimEnd('\n') + "\n").toByteArray(Charsets.UTF_8)
        if (file.exists() && file.length() + bytes.size > maxBytes) trim()
        file.appendBytes(bytes)
    }

    private fun trim() {
        val content = file.readBytes()
        var start = content.size - maxBytes / 2
        if (start < 0) start = 0
        // Continue after the next newline so no line is cut in half
        while (start < content.size && content[start] != '\n'.code.toByte()) start++
        val kept = if (start < content.size) content.copyOfRange(start + 1, content.size) else ByteArray(0)
        file.writeBytes(kept)
    }

    companion object {
        const val MAX_BYTES = 200 * 1024
    }
}
