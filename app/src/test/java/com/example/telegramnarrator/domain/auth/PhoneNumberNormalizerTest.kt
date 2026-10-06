package com.example.telegramnarrator.domain.auth

import com.example.telegramnarrator.domain.auth.PhoneNumberNormalizer.Reason
import com.example.telegramnarrator.domain.auth.PhoneNumberNormalizer.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneNumberNormalizerTest {

    private val normalizer = PhoneNumberNormalizer()

    private fun e164(input: String, region: String?): String? =
        (normalizer.normalize(input, region) as? Result.Valid)?.e164

    private fun reason(input: String, region: String?): Reason? =
        (normalizer.normalize(input, region) as? Result.Invalid)?.reason

    @Test
    fun `Israel - with or without trunk prefix, international and formatted all give the same E164`() {
        listOf("0521234567", "521234567", "+972521234567", "052-123-4567").forEach { input ->
            assertEquals(input, "+972521234567", e164(input, "IL"))
        }
    }

    @Test
    fun `Israel - spaces dashes and parentheses are ignored`() {
        listOf("052 123 4567", "(052) 123-4567", " 052-1234567 ", "+972 (52) 123-4567", "+972-52-123-4567").forEach {
            assertEquals(it, "+972521234567", e164(it, "IL"))
        }
    }

    @Test
    fun `Israel - IDD prefix and country code without plus are accepted`() {
        assertEquals("+972521234567", e164("00972521234567", "IL"))
        assertEquals("+972521234567", e164("972521234567", "IL"))
    }

    @Test
    fun `non-IL countries use their own trunk and length rules`() {
        // UK: trunk 0 is stripped
        assertEquals("+447400123456", e164("07400 123456", "GB"))
        assertEquals("+447400123456", e164("7400 123456", "GB"))
        // US: no trunk prefix, the 1 in front is the national prefix
        assertEquals("+14155552671", e164("(415) 555-2671", "US"))
        assertEquals("+14155552671", e164("1 415 555 2671", "US"))
        // Italy: the leading 0 of landlines is part of the number and must be kept
        assertEquals("+390612345678", e164("06 1234 5678", "IT"))
    }

    @Test
    fun `an international number wins over the selected country`() {
        assertEquals("+447400123456", e164("+44 7400 123456", "IL"))
        assertEquals("+972521234567", e164("+972521234567", null))
        assertEquals("GB", (normalizer.normalize("+44 7400 123456", "IL") as Result.Valid).regionCode)
    }

    @Test
    fun `landlines and non-geographic numbers are accepted`() {
        assertEquals("+97221234567", e164("02-123-4567", "IL"))
        // Fragment / anonymous Telegram numbers (+888) are a non-geographic code
        assertEquals("+88804516516", e164("+888 0451 6516", null))
    }

    @Test
    fun `Arabic-Indic digits count as digits`() {
        assertEquals("+972521234567", e164("٠٥٢١٢٣٤٥٦٧", "IL"))
    }

    @Test
    fun `wrong lengths are reported`() {
        assertEquals(Reason.TOO_SHORT, reason("05212", "IL"))
        assertEquals(Reason.TOO_LONG, reason("0521234567890", "IL"))
        assertEquals(Reason.TOO_LONG, reason("+97252123456789012", null))
        assertEquals(Reason.TOO_SHORT, reason("+9725", null))
    }

    @Test
    fun `empty, missing country and unknown country codes`() {
        assertEquals(Reason.EMPTY, reason("", "IL"))
        assertEquals(Reason.EMPTY, reason(" - ( ) ", "IL"))
        assertEquals(Reason.EMPTY, reason("+", "IL"))
        assertEquals(Reason.NO_COUNTRY, reason("0521234567", null))
        assertEquals(Reason.INVALID, reason("+999 123456789", null))
    }

    @Test
    fun `typed or pasted international numbers select their country`() {
        assertEquals("IL", normalizer.regionForInternational("+972 52-123-4567"))
        assertEquals("IL", normalizer.regionForInternational("+972"))
        assertEquals("GB", normalizer.regionForInternational("+44"))
        assertEquals("RU", normalizer.regionForInternational("+7"))
        assertEquals("US", normalizer.regionForInternational("+1"))
        assertNull(normalizer.regionForInternational("+"))
        assertNull(normalizer.regionForInternational("+97"))
        assertNull(normalizer.regionForInternational("0521234567"))
    }

    @Test
    fun `national part of an international number`() {
        assertEquals("521234567", normalizer.nationalPart("+972 52-123-4567"))
        assertEquals("52", normalizer.nationalPart("+97252"))
        assertEquals("052-123", normalizer.nationalPart("052-123"))
    }

    @Test
    fun `sanitize keeps digits and only a leading plus`() {
        assertEquals("+972521234567", PhoneNumberNormalizer.sanitize(" +972 (52) 123-4567 "))
        assertEquals("0521234567", PhoneNumberNormalizer.sanitize("052-123-4567"))
        assertEquals("972", PhoneNumberNormalizer.sanitize("9+72"))
    }

    @Test
    fun `typing filter drops letters and extra plus signs but keeps separators`() {
        assertEquals("052-123 (4567)", PhoneNumberNormalizer.filterTyped("052-123 (4567)"))
        assertEquals("+972 52", PhoneNumberNormalizer.filterTyped("+972 52"))
        assertEquals("05212", PhoneNumberNormalizer.filterTyped("05a2+12"))
        assertEquals("+972", PhoneNumberNormalizer.filterTyped("++972"))
    }

    @Test
    fun `dial codes and example numbers`() {
        assertEquals(972, normalizer.dialCodeFor("IL"))
        assertEquals(1, normalizer.dialCodeFor("US"))
        assertNull(normalizer.dialCodeFor("XX"))
        assertEquals(true, normalizer.exampleNationalNumber("IL")?.startsWith("05"))
    }
}
