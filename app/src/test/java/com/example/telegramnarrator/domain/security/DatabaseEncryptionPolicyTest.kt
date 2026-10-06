package com.example.telegramnarrator.domain.security

import com.example.telegramnarrator.domain.security.DatabaseEncryptionPolicy.KeyChoice.APP_KEY
import com.example.telegramnarrator.domain.security.DatabaseEncryptionPolicy.KeyChoice.LEGACY_EMPTY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseEncryptionPolicyTest {
    private val policy = DatabaseEncryptionPolicy

    @Test fun `fresh install opens with the app key directly`() =
        assertEquals(listOf(APP_KEY), policy.openOrder(databaseExists = false, markedEncrypted = false, appKeyAvailable = true))

    @Test fun `logged out (no binlog) but marked still opens with the app key only`() =
        assertEquals(listOf(APP_KEY), policy.openOrder(databaseExists = false, markedEncrypted = true, appKeyAvailable = true))

    @Test fun `legacy install tries the empty key first, then the app key`() =
        assertEquals(listOf(LEGACY_EMPTY, APP_KEY), policy.openOrder(databaseExists = true, markedEncrypted = false, appKeyAvailable = true))

    @Test fun `migrated install tries the app key first, then the empty key`() =
        assertEquals(listOf(APP_KEY, LEGACY_EMPTY), policy.openOrder(databaseExists = true, markedEncrypted = true, appKeyAvailable = true))

    @Test fun `without a keystore key only the empty key is possible`() {
        assertEquals(listOf(LEGACY_EMPTY), policy.openOrder(databaseExists = true, markedEncrypted = true, appKeyAvailable = false))
        assertEquals(listOf(LEGACY_EMPTY), policy.openOrder(databaseExists = false, markedEncrypted = false, appKeyAvailable = false))
    }

    @Test fun `rekey only after opening with the empty key when a key exists`() {
        assertTrue(policy.needsRekey(LEGACY_EMPTY, appKeyAvailable = true))
        assertFalse(policy.needsRekey(LEGACY_EMPTY, appKeyAvailable = false))
        assertFalse(policy.needsRekey(APP_KEY, appKeyAvailable = true))
    }

    @Test fun `reset starts encrypted when possible`() {
        assertEquals(APP_KEY, policy.keyAfterReset(appKeyAvailable = true))
        assertEquals(LEGACY_EMPTY, policy.keyAfterReset(appKeyAvailable = false))
    }

    @Test fun `never reset the database while the keystore is unavailable`() {
        assertTrue(policy.mayResetAfterWrongKey(appKeyAvailable = true))
        assertFalse(policy.mayResetAfterWrongKey(appKeyAvailable = false))
    }

    @Test fun `recognises TDLib's wrong key error`() {
        assertTrue(policy.isWrongKeyError(401, "Wrong database encryption key"))
        assertFalse(policy.isWrongKeyError(401, "Unauthorized"))
        assertFalse(policy.isWrongKeyError(400, "Wrong database encryption key"))
        assertFalse(policy.isWrongKeyError(401, null))
    }
}
