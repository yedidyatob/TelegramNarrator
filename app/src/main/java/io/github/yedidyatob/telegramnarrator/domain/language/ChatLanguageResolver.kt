package io.github.yedidyatob.telegramnarrator.domain.language

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Identifies the language of one text, best guesses first (on-device; ML Kit in the app). */
fun interface LanguageIdentifier {
    suspend fun identify(text: String): List<LanguageGuess>
}

/** Per-chat language storage, keyed by chat id only (no text is ever stored). */
interface ChatLanguageStore {
    fun get(chatId: Long): ChatLanguage?
    fun put(chatId: Long, value: ChatLanguage)
    fun remove(chatId: Long)
    fun clear()
}

/** Debug-log sink (never receives message text). */
fun interface LanguageLog {
    fun event(message: String)
}

/**
 * Decides and remembers each chat's language (#89): detects on the chat's most recent messages when
 * [ChatLanguagePolicy.needsDetection] says so, keeps a confirmed language when a re-check is inconclusive,
 * and otherwise counts the messages read since the last check.
 */
class ChatLanguageResolver(
    private val store: ChatLanguageStore,
    private val identifier: LanguageIdentifier,
    private val deviceLanguage: () -> String,
    private val log: LanguageLog = LanguageLog { }
) {
    private val _changes = MutableStateFlow(0)

    /** Bumps whenever a stored language changes (for UI that shows it). */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    /** The stored language of [chatId], or null when it has not been detected. */
    fun languageOf(chatId: Long): ChatLanguage? = store.get(chatId)

    /** The language to read [chatId] in: detected, else the device language. */
    fun languageCodeOf(chatId: Long): String =
        store.get(chatId)?.language ?: ChatLanguagePolicy.normalize(deviceLanguage()) ?: "en"

    /** Resolves [chatId]'s language before reading its new [texts] (oldest first, already cleaned). */
    suspend fun resolve(chatId: Long, isPrivate: Boolean, texts: List<String>): ChatLanguage? {
        val stored = store.get(chatId)
        val usable = texts.filter { t -> t.any { it.isLetter() } }
        if (!ChatLanguagePolicy.needsDetection(stored, usable)) {
            if (stored != null && usable.isNotEmpty()) {
                store.put(chatId, stored.copy(messagesSinceCheck = stored.messagesSinceCheck + usable.size))
            }
            return stored
        }
        val sample = usable.takeLast(ChatLanguagePolicy.sampleSize(isPrivate))
        if (sample.isEmpty()) return stored
        val guesses = sample.map { text ->
            try {
                identifier.identify(text)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                emptyList()
            }
        }
        val detected = ChatLanguagePolicy.aggregate(sample, guesses, isPrivate, deviceLanguage())
        val result = if (stored != null && stored.confirmed && !detected.confirmed) {
            // Inconclusive re-check: keep the confirmed language, restart the count
            stored.copy(messagesSinceCheck = 0)
        } else {
            detected
        }
        store.put(chatId, result)
        if (result != stored) _changes.value++
        log.event(ChatLanguagePolicy.logLine(chatId, result))
        return result
    }

    /** Forgets [chatId]'s language; it is detected again next time. */
    fun reset(chatId: Long) {
        store.remove(chatId)
        _changes.value++
        log.event("lang reset chat=$chatId")
    }

    /** Forgets every chat's language. */
    fun resetAll() {
        store.clear()
        _changes.value++
        log.event("lang reset all")
    }
}
