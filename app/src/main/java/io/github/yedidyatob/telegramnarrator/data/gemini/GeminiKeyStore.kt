package io.github.yedidyatob.telegramnarrator.data.gemini

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the user's Gemini API key in EncryptedSharedPreferences (separate file from the former
 * OpenAI key store so no old OpenAI key is ever sent to Google).
 * The key is never written to logs; callers must not log [getApiKey] results.
 */
@Singleton
class GeminiKeyStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private companion object {
        const val FILE = "gemini_tts_secure"
        const val KEY_API = "api_key"
        const val LEGACY_PLAINTEXT_FILE = FILE + "_fallback"
    }

    /**
     * Encrypted prefs, or null when they cannot be created (broken Keystore): then no key is ever stored
     * (#67, never in plaintext) and Gemini stays unavailable until the device's secure storage works.
     */
    private val prefs: SharedPreferences? = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        android.util.Log.w("GeminiKeyStore", "Secure storage unavailable; the API key will not be saved (${e.javaClass.simpleName})")
        null
    }

    init {
        // Older builds fell back to a plaintext file; remove any key they may have left there (#67)
        runCatching { context.deleteSharedPreferences(LEGACY_PLAINTEXT_FILE) }
    }

    /** Whether a key can be stored securely on this device right now. */
    val isSecureStorageAvailable: Boolean get() = prefs != null

    /** Returns the trimmed key, or null when none is saved. Never log the return value. */
    fun getApiKey(): String? {
        val key = prefs?.getString(KEY_API, null)?.trim().orEmpty()
        return key.ifBlank { null }
    }

    fun hasApiKey(): Boolean = getApiKey() != null

    /** Saves (or removes, for null/blank) the key. Returns false, saving nothing, when secure storage is unavailable. */
    fun setApiKey(key: String?): Boolean {
        val store = prefs ?: return false
        val trimmed = key?.trim().orEmpty()
        return store.edit().run {
            if (trimmed.isEmpty()) remove(KEY_API) else putString(KEY_API, trimmed)
            commit()
        }
    }

    fun clear() = setApiKey(null)
}
