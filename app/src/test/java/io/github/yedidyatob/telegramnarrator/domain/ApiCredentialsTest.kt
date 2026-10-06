package io.github.yedidyatob.telegramnarrator.domain

import io.github.yedidyatob.telegramnarrator.domain.model.ApiCredentials
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiCredentialsTest {
    @Test fun `valid credentials`() = assertEquals(12345, ApiCredentials.parse("12345", "abcdef"))
    @Test fun `trims the id`() = assertEquals(7, ApiCredentials.parse(" 7 ", "h"))
    @Test fun `empty id is rejected`() = assertNull(ApiCredentials.parse("", "abcdef"))
    @Test fun `non numeric id is rejected`() = assertNull(ApiCredentials.parse("abc", "abcdef"))
    @Test fun `zero or negative id is rejected`() {
        assertNull(ApiCredentials.parse("0", "abcdef"))
        assertNull(ApiCredentials.parse("-5", "abcdef"))
    }
    @Test fun `blank hash is rejected`() = assertNull(ApiCredentials.parse("12345", "  "))
}
