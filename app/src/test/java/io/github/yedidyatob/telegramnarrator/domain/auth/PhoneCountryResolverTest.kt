package io.github.yedidyatob.telegramnarrator.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneCountryResolverTest {

    private val normalizer = PhoneNumberNormalizer()

    private fun resolve(sim: String?, network: String?, telephony: Boolean = true, locale: String?) =
        PhoneCountryResolver.resolve(
            PhoneCountryResolver.Signals(sim, network, telephony, locale),
            normalizer::isSupportedRegion
        )

    @Test
    fun `SIM country wins over network and locale`() {
        assertEquals("IL", resolve(sim = "il", network = "us", locale = "DE"))
    }

    @Test
    fun `network country is used when the SIM country is empty`() {
        assertEquals("US", resolve(sim = "", network = "us", locale = "DE"))
        assertEquals("US", resolve(sim = null, network = "US", locale = "DE"))
    }

    @Test
    fun `locale region is used when SIM and network are empty`() {
        assertEquals("DE", resolve(sim = "", network = "", locale = "DE"))
    }

    @Test
    fun `network country is skipped on devices without telephony`() {
        assertEquals("FR", resolve(sim = null, network = "us", telephony = false, locale = "FR"))
    }

    @Test
    fun `no preselection when nothing usable is known`() {
        assertNull(resolve(sim = null, network = null, locale = null))
        assertNull(resolve(sim = "", network = "", locale = ""))
        assertNull(resolve(sim = null, network = "us", telephony = false, locale = ""))
    }

    @Test
    fun `malformed or unsupported codes fall through to the next source`() {
        assertEquals("GB", resolve(sim = "XX", network = "gb", locale = "IL"))
        assertEquals("IL", resolve(sim = "isr", network = "4", locale = "il"))
        // UN M.49 region codes such as "419" (Latin America) are not countries
        assertNull(resolve(sim = null, network = null, locale = "419"))
    }

    @Test
    fun `codes are trimmed and upper-cased`() {
        assertEquals("IL", PhoneCountryResolver.normalize(" il "))
        assertNull(PhoneCountryResolver.normalize(""))
        assertNull(PhoneCountryResolver.normalize("1L"))
    }
}
