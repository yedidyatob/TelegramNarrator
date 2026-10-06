package io.github.yedidyatob.telegramnarrator.domain.model

/** Checks the Telegram API id / hash that are injected into BuildConfig from local.properties. */
object ApiCredentials {
    const val MISSING_MESSAGE =
        "Telegram API credentials are missing. Add TELEGRAM_API_ID and TELEGRAM_API_HASH " +
            "(from https://my.telegram.org) to local.properties and rebuild the app."

    /** @return the numeric api id, or null if [apiId] / [apiHash] is not usable. */
    fun parse(apiId: String, apiHash: String): Int? {
        val id = apiId.trim().toIntOrNull() ?: return null
        return if (id > 0 && apiHash.isNotBlank()) id else null
    }
}
