package io.github.yedidyatob.telegramnarrator.data.tdlib

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import io.github.yedidyatob.telegramnarrator.domain.security.DatabaseEncryptionPolicy
import io.github.yedidyatob.telegramnarrator.domain.security.WrappedKeyCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the TDLib `databaseEncryptionKey` (issue #15).
 *
 * A random 32-byte key is generated once and stored in `noBackupFilesDir`, wrapped (AES-256-GCM) by a
 * non-exportable Android Keystore key. Plain JCA + Keystore on purpose: no dependency on the deprecated
 * `androidx.security:security-crypto`. Neither the key nor the wrapped blob is ever logged.
 */
@Singleton
class TdLibDatabaseKeyStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private companion object {
        const val TAG = "TdLibDbKeyStore"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val WRAPPING_KEY_ALIAS = "tdlib_db_key_wrapping"
        const val WRAPPED_KEY_FILE = "tdlib_db_key.bin"
        const val PREFS = "tdlib_security"
        const val KEY_ENCRYPTED = "db_encrypted_with_app_key"
        const val DB_KEY_BYTES = 32
        const val GCM_TAG_BITS = 128
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }

    private val wrappedKeyFile = File(context.noBackupFilesDir, WRAPPED_KEY_FILE)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Whether the database was successfully opened with / re-keyed to the app key. */
    var markedEncrypted: Boolean
        get() = prefs.getBoolean(KEY_ENCRYPTED, false)
        set(value) {
            // commit(): the marker must be on disk before we rely on it at the next start
            prefs.edit().putBoolean(KEY_ENCRYPTED, value).commit()
        }

    /**
     * The database key, created on first use. Returns null if the Android Keystore is unusable on this
     * device; the caller then keeps TDLib's default (empty) key rather than failing to start.
     * If an existing blob cannot be unwrapped (Keystore key lost), a new key is created; the database
     * opened with the old key can then no longer be opened and is reset by the caller.
     */
    @Synchronized
    fun getOrCreateKey(): ByteArray? = try {
        loadKey() ?: createKey()
    } catch (e: Exception) {
        Log.w(TAG, "Android Keystore unavailable; TDLib database stays unencrypted (${e.javaClass.simpleName})")
        null
    }

    private fun loadKey(): ByteArray? {
        if (!wrappedKeyFile.exists()) return null
        val wrapped = WrappedKeyCodec.decode(wrappedKeyFile.readBytes())
        val wrappingKey = existingWrappingKey()
        if (wrapped == null || wrappingKey == null) {
            Log.w(TAG, "Stored TDLib database key is unreadable; creating a new one")
            return null
        }
        // Only a definitive mismatch counts as "key lost"; other Keystore errors propagate so that a transient
        // failure never replaces a valid key (getOrCreateKey then reports the Keystore as unavailable).
        return try {
            Cipher.getInstance(TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, wrappingKey, GCMParameterSpec(GCM_TAG_BITS, wrapped.iv))
                doFinal(wrapped.ciphertext)
            }.takeIf { it.size == DB_KEY_BYTES }
        } catch (e: AEADBadTagException) {
            Log.w(TAG, "Stored TDLib database key does not match the Keystore key; creating a new one")
            null
        }
    }

    private fun createKey(): ByteArray {
        val key = ByteArray(DB_KEY_BYTES).also { SecureRandom().nextBytes(it) }
        val wrappingKey = existingWrappingKey() ?: generateWrappingKey()
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, wrappingKey) }
        val blob = WrappedKeyCodec.encode(cipher.iv, cipher.doFinal(key))
        val tmp = File(wrappedKeyFile.parentFile, "$WRAPPED_KEY_FILE.tmp")
        tmp.writeBytes(blob)
        if (!tmp.renameTo(wrappedKeyFile)) {
            tmp.delete()
            error("could not store wrapped key")
        }
        // A new key cannot open a database encrypted with a previous one
        markedEncrypted = false
        return key
    }

    private fun existingWrappingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (keyStore.getEntry(WRAPPING_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private fun generateWrappingKey(): SecretKey =
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    WRAPPING_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
}
