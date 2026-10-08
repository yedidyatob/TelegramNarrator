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
    }

    private val prefs: SharedPreferences = try {
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
        // Encrypted prefs can fail on broken keystore; fall back to private prefs (still not logged).
        android.util.Log.w("GeminiKeyStore", "Encrypted prefs unavailable, using private SharedPreferences")
        context.getSharedPreferences(FILE + "_fallback", Context.MODE_PRIVATE)
    }

    /** Returns the trimmed key, or null when none is saved. Never log the return value. */
    fun getApiKey(): String? {
        val key = prefs.getString(KEY_API, null)?.trim().orEmpty()
        return key.ifBlank { null }
    }

    fun hasApiKey(): Boolean = getApiKey() != null

    fun setApiKey(key: String?) {
        val trimmed = key?.trim().orEmpty()
        prefs.edit().apply {
            if (trimmed.isEmpty()) remove(KEY_API) else putString(KEY_API, trimmed)
            apply()
        }
    }

    fun clear() = setApiKey(null)
}
