package io.github.yedidyatob.telegramnarrator.domain.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WrappedKeyCodecTest {
    private val iv = ByteArray(12) { it.toByte() }
    private val ct = ByteArray(48) { (100 + it).toByte() }

    @Test fun `round trip`() {
        val decoded = WrappedKeyCodec.decode(WrappedKeyCodec.encode(iv, ct))!!
        assertArrayEquals(iv, decoded.iv)
        assertArrayEquals(ct, decoded.ciphertext)
    }

    @Test fun `rejects unknown version`() {
        val blob = WrappedKeyCodec.encode(iv, ct).also { it[0] = 2 }
        assertNull(WrappedKeyCodec.decode(blob))
    }

    @Test fun `rejects truncated blobs`() {
        assertNull(WrappedKeyCodec.decode(ByteArray(0)))
        assertNull(WrappedKeyCodec.decode(byteArrayOf(1, 12)))
        assertNull(WrappedKeyCodec.decode(WrappedKeyCodec.encode(iv, ct).copyOf(2 + 12)))
    }

    @Test fun `rejects zero length iv`() = assertNull(WrappedKeyCodec.decode(byteArrayOf(1, 0, 5, 6)))
}
