package io.github.yedidyatob.telegramnarrator.domain.sponsored

import java.net.URI

/**
 * How the ad button / link is opened. Telegram: "A confirmation prompt should be shown before opening the URL,
 * unless the host part of the URL matches the following regex: `(^|\.)(telegram\.(org|me|dog)|t\.me|te\.?legra\.ph|graph\.org|fragment\.com|telesco\.pe)$`".
 */
object SponsoredLinkPolicy {
    val TELEGRAM_HOST = Regex("""(^|\.)(telegram\.(org|me|dog)|t\.me|te\.?legra\.ph|graph\.org|fragment\.com|telesco\.pe)$""")
    private val TELEGRAM_APP_HOST = Regex("""(^|\.)(t\.me|telegram\.(me|dog))$""")

    /** Lowercase host of [url] (a scheme-less `t.me/...` is read as https), or null if it has none. */
    fun hostOf(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if (trimmed.contains("://") || trimmed.startsWith("tg:")) trimmed else "https://$trimmed"
        return try {
            URI(withScheme).host?.lowercase()?.trimEnd('.')
        } catch (e: Exception) {
            null
        }
    }

    private fun isTgScheme(url: String) = url.trim().startsWith("tg:", ignoreCase = true)

    /** Telegram's own hosts (and `tg:` deep links): opened without confirmation. */
    fun isTelegramLink(url: String): Boolean {
        if (isTgScheme(url)) return true
        val host = hostOf(url) ?: return false
        return TELEGRAM_HOST.containsMatchIn(host)
    }

    /** True when the user must confirm before the link is opened. */
    fun requiresConfirmation(url: String): Boolean = !isTelegramLink(url)

    /** `t.me` / `tg:` links: open in the Telegram app (this app is not a full Telegram client). */
    fun opensInTelegramApp(url: String): Boolean {
        if (isTgScheme(url)) return true
        val host = hostOf(url) ?: return false
        return TELEGRAM_APP_HOST.containsMatchIn(host)
    }

    /** The URL as it should be handed to the system: scheme-less links get https. */
    fun normalized(url: String): String {
        val trimmed = url.trim()
        return if (trimmed.contains("://") || isTgScheme(trimmed)) trimmed else "https://$trimmed"
    }
}
