package io.github.yedidyatob.telegramnarrator.data.language

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.yedidyatob.telegramnarrator.data.security.encryptedPrefsOrNull
import io.github.yedidyatob.telegramnarrator.domain.language.ChatLanguage
import io.github.yedidyatob.telegramnarrator.domain.language.ChatLanguagePolicy
import io.github.yedidyatob.telegramnarrator.domain.language.ChatLanguageStore
import java.util.concurrent.ConcurrentHashMap

/**
 * Detected chat languages (#89) in EncryptedSharedPreferences, keyed by chat id only: no titles, no text.
 * When secure storage is unavailable they are kept in memory only (never in plaintext). Wiped on logout.
 */
class EncryptedChatLanguageStore(
    context: Context,
) : ChatLanguageStore {
    private val memory = ConcurrentHashMap<Long, ChatLanguage>()

    private val prefs: SharedPreferences? =
        encryptedPrefsOrNull(context, FILE, "ChatLanguageStore", "chat languages kept in memory")

    override fun get(chatId: Long): ChatLanguage? =
        memory[chatId] ?: ChatLanguagePolicy.decode(prefs?.getString(key(chatId), null))?.also { memory[chatId] = it }

    override fun put(
        chatId: Long,
        value: ChatLanguage,
    ) {
        memory[chatId] = value
        prefs?.edit { putString(key(chatId), ChatLanguagePolicy.encode(value)) }
    }

    override fun remove(chatId: Long) {
        memory.remove(chatId)
        prefs?.edit { remove(key(chatId)) }
    }

    override fun clear() {
        memory.clear()
        prefs?.edit { clear() }
    }

    private fun key(chatId: Long) = chatId.toString()

    private companion object {
        const val FILE = "chat_languages_secure"
    }
}
