package io.github.yedidyatob.telegramnarrator.domain.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseEncryptionPolicyTest {
    @Test fun `recognises TDLib's wrong key error`() {
        assertTrue(DatabaseEncryptionPolicy.isWrongKeyError(401, "Wrong database encryption key"))
        assertFalse(DatabaseEncryptionPolicy.isWrongKeyError(401, "Unauthorized"))
        assertFalse(DatabaseEncryptionPolicy.isWrongKeyError(400, "Wrong database encryption key"))
        assertFalse(DatabaseEncryptionPolicy.isWrongKeyError(401, null))
    }
}
