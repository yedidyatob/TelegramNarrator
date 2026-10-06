package io.github.yedidyatob.telegramnarrator.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class LoginErrorTest {
    @Test
    fun `maps TDLib auth errors`() {
        assertEquals(LoginError.PHONE_INVALID, LoginError.classify(400, "PHONE_NUMBER_INVALID"))
        assertEquals(LoginError.PHONE_BANNED, LoginError.classify(400, "PHONE_NUMBER_BANNED"))
        assertEquals(LoginError.CODE_INVALID, LoginError.classify(400, "PHONE_CODE_INVALID"))
        assertEquals(LoginError.CODE_INVALID, LoginError.classify(400, "PHONE_CODE_EMPTY"))
        assertEquals(LoginError.CODE_EXPIRED, LoginError.classify(400, "PHONE_CODE_EXPIRED"))
        assertEquals(LoginError.PASSWORD_INVALID, LoginError.classify(400, "PASSWORD_HASH_INVALID"))
    }

    @Test
    fun `flood waits are too many attempts`() {
        assertEquals(LoginError.TOO_MANY_ATTEMPTS, LoginError.classify(429, "Too Many Requests: retry after 60"))
        assertEquals(LoginError.TOO_MANY_ATTEMPTS, LoginError.classify(420, "FLOOD_WAIT_30"))
    }

    @Test
    fun `unknown and missing errors fall back`() {
        assertEquals(LoginError.UNKNOWN, LoginError.classify(500, "INTERNAL"))
        assertEquals(LoginError.UNKNOWN, LoginError.classify(null, null))
        assertEquals(LoginError.NETWORK, LoginError.classify(500, "Request timeout"))
    }
}
