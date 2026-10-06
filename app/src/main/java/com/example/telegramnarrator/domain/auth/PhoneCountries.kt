package com.example.telegramnarrator.domain.auth

import java.util.Locale

/** One entry of the login country picker. */
data class PhoneCountry(
    /** ISO 3166-1 alpha-2, e.g. "IL". */
    val regionCode: String,
    /** Without "+", e.g. 972. */
    val dialCode: Int,
    /** Country name in the UI language. */
    val name: String
) {
    val flag: String get() = PhoneCountries.flagEmoji(regionCode)
    val dialCodeText: String get() = "+$dialCode"
}

object PhoneCountries {

    /** All countries with a dial code, sorted by their name in [displayLocale]. */
    fun all(normalizer: PhoneNumberNormalizer, displayLocale: Locale = Locale.getDefault()): List<PhoneCountry> =
        normalizer.supportedRegions
            .mapNotNull { region ->
                val dial = normalizer.dialCodeFor(region) ?: return@mapNotNull null
                PhoneCountry(region, dial, displayName(region, displayLocale))
            }
            .sortedWith(compareBy(java.text.Collator.getInstance(displayLocale)) { it.name })

    fun displayName(regionCode: String, displayLocale: Locale = Locale.getDefault()): String =
        Locale("", regionCode).getDisplayCountry(displayLocale).ifBlank { regionCode }

    /**
     * Search by country name (UI language or English), ISO code or dial code: "isr", "ישראל", "IL", "972", "+972".
     * Blank query returns [countries] unchanged.
     */
    fun filter(countries: List<PhoneCountry>, query: String): List<PhoneCountry> {
        val q = query.trim()
        if (q.isEmpty()) return countries
        val digits = q.removePrefix("+").trim()
        if (digits.isNotEmpty() && digits.all { it.isDigit() }) {
            return countries.filter { it.dialCode.toString().startsWith(digits) }
        }
        return countries.filter { country ->
            country.name.contains(q, ignoreCase = true) ||
                displayName(country.regionCode, Locale.ENGLISH).contains(q, ignoreCase = true) ||
                country.regionCode.equals(q, ignoreCase = true)
        }
    }

    /** "IL" -> 🇮🇱 (regional indicator symbols); empty for anything that is not two letters. */
    fun flagEmoji(regionCode: String): String {
        val code = regionCode.uppercase(Locale.ROOT)
        if (code.length != 2 || !code.all { it in 'A'..'Z' }) return ""
        val first = Character.toChars(REGIONAL_INDICATOR_A + (code[0] - 'A'))
        val second = Character.toChars(REGIONAL_INDICATOR_A + (code[1] - 'A'))
        return String(first) + String(second)
    }

    private const val REGIONAL_INDICATOR_A = 0x1F1E6
}
