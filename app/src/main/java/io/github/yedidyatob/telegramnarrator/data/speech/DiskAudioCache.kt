package io.github.yedidyatob.telegramnarrator.data.speech

import java.io.File

/**
 * Small on-disk cache of synthesized speech (MP3 files named `<key>.mp3`), shared by the network TTS
 * engines (OpenAI, Edge). Writes are atomic (temp file + rename). When [maxBytes] is finite, the least
 * recently used files are deleted after each write until the cache fits again.
 *
 * Plain java.io only, so it is unit tested on the JVM.
 */
class DiskAudioCache(
    private val dir: File,
    private val maxBytes: Long = UNLIMITED
) {
    fun fileFor(key: String): File = File(dir, "$key.mp3")

    /** The cached file for [key], or null. A hit refreshes the file's LRU timestamp. */
    fun get(key: String): File? {
        val file = fileFor(key)
        if (!file.isFile || file.length() <= 0L) return null
        file.setLastModified(System.currentTimeMillis())
        return file
    }

    @Synchronized
    fun put(key: String, bytes: ByteArray): File {
        dir.mkdirs()
        val file = fileFor(key)
        val tmp = File(dir, "${file.name}.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(file)) {
            file.writeBytes(bytes)
            tmp.delete()
        }
        file.setLastModified(System.currentTimeMillis())
        trim(keep = file)
        return file
    }

    /** Drops a cached file (e.g. one the MediaPlayer could not play). */
    fun remove(file: File) {
        if (file.parentFile == dir) file.delete()
    }

    fun sizeBytes(): Long = cachedFiles().sumOf { it.length() }

    private fun trim(keep: File) {
        if (maxBytes == UNLIMITED) return
        val files = cachedFiles().sortedBy { it.lastModified() }
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= maxBytes) break
            if (file == keep) continue
            val length = file.length()
            if (file.delete()) total -= length
        }
    }

    private fun cachedFiles(): List<File> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(".mp3") }?.toList().orEmpty()

    companion object {
        const val UNLIMITED = Long.MAX_VALUE
    }
}
