package com.example.telegramnarrator.domain.security

/**
 * File format of the wrapped TDLib database key: `[version=1][ivLength][iv][AES-GCM ciphertext+tag]`.
 * Pure Kotlin so the format can be unit tested without the Android Keystore.
 */
object WrappedKeyCodec {
    private const val VERSION: Byte = 1

    class Wrapped(val iv: ByteArray, val ciphertext: ByteArray)

    fun encode(iv: ByteArray, ciphertext: ByteArray): ByteArray {
        require(iv.isNotEmpty() && iv.size <= 255) { "bad IV length" }
        require(ciphertext.isNotEmpty()) { "empty ciphertext" }
        return byteArrayOf(VERSION, iv.size.toByte()) + iv + ciphertext
    }

    /** @return null for anything that is not a version-1 blob (treated like a missing key). */
    fun decode(blob: ByteArray): Wrapped? {
        if (blob.size < 3 || blob[0] != VERSION) return null
        val ivLength = blob[1].toInt() and 0xFF
        if (ivLength == 0 || blob.size <= 2 + ivLength) return null
        return Wrapped(
            iv = blob.copyOfRange(2, 2 + ivLength),
            ciphertext = blob.copyOfRange(2 + ivLength, blob.size)
        )
    }
}
