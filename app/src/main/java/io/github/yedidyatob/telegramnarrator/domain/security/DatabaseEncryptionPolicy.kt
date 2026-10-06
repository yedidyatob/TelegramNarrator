package io.github.yedidyatob.telegramnarrator.domain.security

/**
 * Decides how to open the TDLib database so that it ends up encrypted with the Keystore-protected key
 * (issue #15) without losing the session of existing installs.
 *
 * TDLib rejects `setTdlibParameters` with error 401 "Wrong database encryption key" when the key does not
 * match the database, and stays in `AuthorizationStateWaitTdlibParameters`, so another key can be tried.
 * An install from before #15 has a database opened with the empty key; it is opened with the empty key
 * once and then re-keyed with `setDatabaseEncryptionKey`.
 */
object DatabaseEncryptionPolicy {

    enum class KeyChoice {
        /** The empty key (TDLib's built-in default, i.e. effectively unencrypted). */
        LEGACY_EMPTY,

        /** The random key wrapped by the Android Keystore. */
        APP_KEY
    }

    /**
     * Keys to try, in order.
     *
     * @param databaseExists whether TDLib's binlog already exists (a fresh install / logged-out state has none)
     * @param markedEncrypted whether we recorded a successful re-key / encrypted open before
     * @param appKeyAvailable whether the Keystore-wrapped key could be loaded or created
     */
    fun openOrder(databaseExists: Boolean, markedEncrypted: Boolean, appKeyAvailable: Boolean): List<KeyChoice> =
        when {
            !appKeyAvailable -> listOf(KeyChoice.LEGACY_EMPTY)
            !databaseExists -> listOf(KeyChoice.APP_KEY)
            // The re-key may have succeeded right before the app died, without the marker being written,
            // and vice versa: always fall back to the other key before giving up.
            markedEncrypted -> listOf(KeyChoice.APP_KEY, KeyChoice.LEGACY_EMPTY)
            else -> listOf(KeyChoice.LEGACY_EMPTY, KeyChoice.APP_KEY)
        }

    /** After a successful open with [openedWith], whether to call `setDatabaseEncryptionKey(appKey)`. */
    fun needsRekey(openedWith: KeyChoice, appKeyAvailable: Boolean): Boolean =
        openedWith == KeyChoice.LEGACY_EMPTY && appKeyAvailable

    /**
     * When every key in [openOrder] was rejected as wrong (e.g. the Keystore key was lost), the only way
     * forward is to delete the local database and log in again; start it encrypted if a key is available.
     */
    fun keyAfterReset(appKeyAvailable: Boolean): KeyChoice =
        if (appKeyAvailable) KeyChoice.APP_KEY else KeyChoice.LEGACY_EMPTY

    /**
     * Deleting the database is only acceptable when the Keystore works (a key is available) and every key
     * was rejected. If the Keystore itself is unavailable the database may be fine; report an error and
     * let the user retry instead of logging them out.
     */
    fun mayResetAfterWrongKey(appKeyAvailable: Boolean): Boolean = appKeyAvailable

    /** TDLib's answer to `setTdlibParameters` with a key that does not match the database. */
    fun isWrongKeyError(code: Int, message: String?): Boolean =
        code == 401 && message?.contains("encryption key", ignoreCase = true) == true
}
