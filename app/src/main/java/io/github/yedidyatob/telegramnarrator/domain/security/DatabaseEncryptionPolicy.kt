package io.github.yedidyatob.telegramnarrator.domain.security

/**
 * The TDLib database is always opened with the Keystore-protected key (issue #15). TDLib rejects
 * `setTdlibParameters` with error 401 "Wrong database encryption key" when the key does not match the database
 * (e.g. the Keystore key was lost); the database is then reset and the user logs in again.
 */
object DatabaseEncryptionPolicy {
    /** TDLib's answer to `setTdlibParameters` with a key that does not match the database. */
    fun isWrongKeyError(code: Int, message: String?): Boolean =
        code == 401 && message?.contains("encryption key", ignoreCase = true) == true
}
