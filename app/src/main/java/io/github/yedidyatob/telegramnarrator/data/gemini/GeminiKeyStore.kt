package io.github.yedidyatob.telegramnarrator.data.gemini

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.yedidyatob.telegramnarrator.data.security.encryptedPrefsOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the user's Gemini API key in EncryptedSharedPreferences.
 * The key is never written to logs; callers must not log [getApiKey] results.
 */
@Singleton
class GeminiKeyStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private companion object {
            const val FILE = "gemini_tts_secure"
            const val KEY_API = "api_key"
        }

        /**
         * Encrypted prefs, or null when they cannot be created (broken Keystore): then no key is ever stored
         * (#67, never in plaintext) and Gemini stays unavailable until the device's secure storage works.
         */
        private val prefs: SharedPreferences? =
            encryptedPrefsOrNull(context, FILE, "GeminiKeyStore", "the API key will not be saved")

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

            // Not edit {}: commit()'s result is reported to the user
            @SuppressLint("UseKtx")
            val editor = store.edit()
            if (trimmed.isEmpty()) editor.remove(KEY_API) else editor.putString(KEY_API, trimmed)
            return editor.commit()
        }

        fun clear() = setApiKey(null)
    }
