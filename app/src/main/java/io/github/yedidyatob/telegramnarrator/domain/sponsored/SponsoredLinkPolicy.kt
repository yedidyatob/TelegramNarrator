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

    /**
     * The URL to open, or null when it must not be opened at all (#68). Allowed: `https` (any host, confirmed
     * by the user unless Telegram's), `tg:` deep links, and `http` on Telegram's hosts (upgraded to https).
     * Every other scheme (`intent:`, `content:`, `file:`, `javascript:`, custom...) is blocked.
     */
    fun safeUrl(rawUrl: String): String? {
        val url = normalized(rawUrl)
        if (url.isEmpty()) return null
        if (isTgScheme(url)) return url
        val uri = try { URI(url) } catch (e: Exception) { return null }
        val host = uri.host?.lowercase()?.trimEnd('.')?.takeIf { it.isNotEmpty() } ?: return null
        return when (uri.scheme?.lowercase()) {
            "https" -> url
            "http" -> if (TELEGRAM_HOST.containsMatchIn(host)) "https" + url.substring(url.indexOf(':')) else null
            else -> null
        }
    }

    /**
     * Where a `tg:` link goes when no official Telegram app is installed: never to any other app that claims
     * `tg:` (#68). `tg://resolve?domain=x` becomes `https://t.me/x`; anything else is not opened (null).
     */
    fun browserFallback(url: String): String? {
        if (!isTgScheme(url)) return url
        val domain = Regex("""^tg://resolve\?(?:.*&)?domain=([A-Za-z0-9_]{3,64})(?:&|$)""", RegexOption.IGNORE_CASE)
            .find(url.trim())?.groupValues?.get(1) ?: return null
        return "https://t.me/$domain"
    }
}
