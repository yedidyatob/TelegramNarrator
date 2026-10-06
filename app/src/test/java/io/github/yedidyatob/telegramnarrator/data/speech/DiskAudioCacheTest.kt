package io.github.yedidyatob.telegramnarrator.data.speech

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiskAudioCacheTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `put then get returns the same bytes`() {
        val cache = DiskAudioCache(tmp.newFolder("c"))
        assertNull(cache.get("k"))
        cache.put("k", byteArrayOf(1, 2, 3))
        val file = cache.get("k")
        assertNotNull(file)
        assertArrayEquals(byteArrayOf(1, 2, 3), file!!.readBytes())
        assertEquals("k.mp3", file.name)
    }

    @Test
    fun `empty files are treated as misses`() {
        val dir = tmp.newFolder("c")
        val cache = DiskAudioCache(dir)
        cache.fileFor("k").writeBytes(ByteArray(0))
        assertNull(cache.get("k"))
    }

    @Test
    fun `least recently used files are trimmed past the size limit`() {
        val dir = tmp.newFolder("c")
        val cache = DiskAudioCache(dir, maxBytes = 25)
        cache.put("old", ByteArray(10)).setLastModified(1_000)
        cache.put("mid", ByteArray(10)).setLastModified(2_000)
        cache.put("new", ByteArray(10)) // 30 bytes > 25: "old" goes
        assertNull(cache.get("old"))
        assertNotNull(cache.get("mid"))
        assertNotNull(cache.get("new"))
        assertTrue(cache.sizeBytes() <= 25)
    }

    @Test
    fun `the file just written is never trimmed`() {
        val cache = DiskAudioCache(tmp.newFolder("c"), maxBytes = 5)
        cache.put("big", ByteArray(10))
        assertNotNull(cache.get("big"))
    }

    @Test
    fun `remove only deletes files of this cache`() {
        val cache = DiskAudioCache(tmp.newFolder("c"))
        val file = cache.put("k", byteArrayOf(1))
        val outside = tmp.newFile("other.mp3").apply { writeBytes(byteArrayOf(1)) }
        cache.remove(outside)
        assertTrue(outside.exists())
        cache.remove(file)
        assertFalse(file.exists())
    }
}
