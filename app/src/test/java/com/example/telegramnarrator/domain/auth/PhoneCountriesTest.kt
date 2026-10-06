package com.example.telegramnarrator.domain.auth

import com.example.telegramnarrator.domain.auth.PhoneNumberNormalizer.Reason
import com.example.telegramnarrator.domain.auth.PhoneNumberNormalizer.Result
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneCountriesTest {

    private val normalizer = PhoneNumberNormalizer()
    private val countries = PhoneCountries.all(normalizer, Locale.ENGLISH)

    @Test
    fun `list has every country with its dial code, sorted by name`() {
        assertTrue(countries.size > 200)
        assertEquals(PhoneCountry("IL", 972, "Israel"), countries.first { it.regionCode == "IL" })
        assertEquals(countries.map { it.name }.sortedWith(java.text.Collator.getInstance(Locale.ENGLISH)), countries.map { it.name })
    }

    @Test
    fun `search by name, ISO code or dial code`() {
        assertTrue(PhoneCountries.filter(countries, "isr").any { it.regionCode == "IL" })
        assertTrue(PhoneCountries.filter(countries, "IL").any { it.regionCode == "IL" })
        assertEquals(listOf("IL"), PhoneCountries.filter(countries, "+972").map { it.regionCode })
        assertEquals(listOf("IL"), PhoneCountries.filter(countries, "972").map { it.regionCode })
        assertTrue(PhoneCountries.filter(countries, "+1").map { it.regionCode }.containsAll(listOf("US", "CA")))
        assertEquals(countries, PhoneCountries.filter(countries, "  "))
        assertTrue(PhoneCountries.filter(countries, "zzzz").isEmpty())
    }

    @Test
    fun `names in the UI language are searchable, English names too`() {
        val hebrew = PhoneCountries.all(normalizer, Locale("he"))
        val israel = hebrew.first { it.regionCode == "IL" }
        assertTrue(PhoneCountries.filter(hebrew, israel.name).contains(israel))
        assertTrue(PhoneCountries.filter(hebrew, "Israel").contains(israel))
    }

    @Test
    fun `flag emoji from the region code`() {
        assertEquals("\uD83C\uDDEE\uD83C\uDDF1", PhoneCountries.flagEmoji("IL"))
        assertEquals("\uD83C\uDDFA\uD83C\uDDF8", PhoneCountries.flagEmoji("us"))
        assertEquals("", PhoneCountries.flagEmoji("419"))
    }

    @Test
    fun `inline validation shows too long right away and too short only after send`() {
        val tooShort = normalizer.normalize("05212", "IL")
        val tooLong = normalizer.normalize("0521234567890", "IL")
        assertNull(PhoneFieldValidation.visibleReason(tooShort, submitAttempted = false))
        assertEquals(Reason.TOO_SHORT, PhoneFieldValidation.visibleReason(tooShort, submitAttempted = true))
        assertEquals(Reason.TOO_LONG, PhoneFieldValidation.visibleReason(tooLong, submitAttempted = false))
        assertNull(PhoneFieldValidation.visibleReason(normalizer.normalize("", "IL"), submitAttempted = true))
        assertNull(PhoneFieldValidation.visibleReason(Result.Valid("+972521234567", "IL"), submitAttempted = true))
        assertEquals(
            Reason.NO_COUNTRY,
            PhoneFieldValidation.visibleReason(normalizer.normalize("0521234567", null), submitAttempted = true)
        )
    }

    @Test
    fun `picker shows the country of a pasted international number`() {
        assertEquals("IL", PhoneFieldValidation.effectiveRegion("+972 52 123 4567", "US", normalizer))
        assertEquals("US", PhoneFieldValidation.effectiveRegion("4155552671", "US", normalizer))
        assertNull(PhoneFieldValidation.effectiveRegion("052", null, normalizer))
    }
}
