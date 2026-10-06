package com.example.telegramnarrator.domain.auth

import java.util.Locale

/**
 * Picks the login screen's default country: the phone's own country, never a hardcoded one.
 * No runtime permission is involved (SIM / network country ISO codes are readable without one).
 *
 * Order: 1. SIM country, 2. network country (skipped without telephony), 3. device locale region,
 * 4. nothing (null): the user picks the country.
 */
object PhoneCountryResolver {

    /** What the device reports, as raw ISO 3166-1 alpha-2 strings (any case, possibly empty). */
    data class Signals(
        val simCountryIso: String?,
        val networkCountryIso: String?,
        val hasTelephony: Boolean,
        val localeCountry: String?
    )

    /**
     * @param isSupported whether a normalized region code (e.g. "IL") has a phone dial code; unsupported or
     * malformed values (e.g. "", "419", "XX") fall through to the next source.
     */
    fun resolve(signals: Signals, isSupported: (String) -> Boolean): String? {
        val candidates = listOf(
            signals.simCountryIso,
            if (signals.hasTelephony) signals.networkCountryIso else null,
            signals.localeCountry
        )
        return candidates.firstNotNullOfOrNull { raw -> normalize(raw)?.takeIf(isSupported) }
    }

    /** "il " -> "IL"; null for blank or anything that is not two ASCII letters. */
    fun normalize(raw: String?): String? {
        val code = raw?.trim()?.uppercase(Locale.ROOT) ?: return null
        return code.takeIf { it.length == 2 && it.all { c -> c in 'A'..'Z' } }
    }
}
