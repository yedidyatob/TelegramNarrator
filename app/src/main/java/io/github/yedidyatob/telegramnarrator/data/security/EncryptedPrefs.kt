package io.github.yedidyatob.telegramnarrator.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * EncryptedSharedPreferences [file], or null when the device's secure storage is broken. Callers then keep their
 * data in memory or not at all, never in plaintext (#67). [unavailable] is logged with the exception type only.
 * The deprecated androidx.security-crypto API is kept deliberately for launch (no migration for now).
 */
@Suppress("DEPRECATION")
fun encryptedPrefsOrNull(
    context: Context,
    file: String,
    tag: String,
    unavailable: String,
): SharedPreferences? =
    try {
        EncryptedSharedPreferences.create(
            context,
            file,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        Log.w(tag, "Secure storage unavailable; $unavailable (${e.javaClass.simpleName})")
        null
    }
