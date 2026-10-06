package com.example.telegramnarrator.domain.auth

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber

/**
 * Turns what the user typed in the login phone field into the E.164 number TDLib expects
 * (`setAuthenticationPhoneNumber`), using libphonenumber for per-country trunk prefixes and lengths.
 *
 * - A local number with the selected country: with or without the national trunk prefix
 *   (Israel: `0521234567` or `521234567` -> `+972521234567`).
 * - A full international number (`+972…`) wins over the selected country (its own country is used).
 * - Spaces, dashes, parentheses, dots and slashes are ignored; non-ASCII digits (e.g. Arabic-Indic) count as digits.
 */
class PhoneNumberNormalizer(private val util: PhoneNumberUtil = PhoneNumberUtil.getInstance()) {

    sealed interface Result {
        data class Valid(val e164: String, val regionCode: String?) : Result
        data class Invalid(val reason: Reason) : Result
    }

    enum class Reason {
        /** Nothing typed yet. */
        EMPTY,
        /** A local number but no country is selected. */
        NO_COUNTRY,
        TOO_SHORT,
        TOO_LONG,
        /** Between the shortest and longest length of the country, but no number type has that length. */
        WRONG_LENGTH,
        /** Unknown country code / not a number. */
        INVALID
    }

    /** Region codes that have a dial code (e.g. "IL", "US"), for the country picker. */
    val supportedRegions: Set<String> get() = util.supportedRegions

    fun isSupportedRegion(regionCode: String): Boolean = regionCode in util.supportedRegions

    /** Dial code of [regionCode] without "+" (972 for "IL"), or null if unknown. */
    fun dialCodeFor(regionCode: String): Int? = util.getCountryCodeForRegion(regionCode).takeIf { it != 0 }

    /** An example mobile number of [regionCode] in national format (placeholder text), e.g. "050-234-5678". */
    fun exampleNationalNumber(regionCode: String): String? = try {
        util.getExampleNumberForType(regionCode, PhoneNumberUtil.PhoneNumberType.MOBILE)
            ?.let { util.format(it, PhoneNumberUtil.PhoneNumberFormat.NATIONAL) }
    } catch (e: Exception) {
        null
    }

    fun normalize(input: String, selectedRegion: String?): Result {
        val sanitized = sanitize(input)
        if (sanitized.isEmpty() || sanitized == "+") return Result.Invalid(Reason.EMPTY)
        val international = sanitized.startsWith("+")
        if (!international && selectedRegion == null) return Result.Invalid(Reason.NO_COUNTRY)
        val number = try {
            util.parse(sanitized, if (international) UNKNOWN_REGION else selectedRegion)
        } catch (e: NumberParseException) {
            return Result.Invalid(reasonFor(e.errorType))
        }
        return when (lengthCheck(number)) {
            PhoneNumberUtil.ValidationResult.IS_POSSIBLE ->
                Result.Valid(util.format(number, PhoneNumberUtil.PhoneNumberFormat.E164), regionOf(number))
            PhoneNumberUtil.ValidationResult.IS_POSSIBLE_LOCAL_ONLY,
            PhoneNumberUtil.ValidationResult.TOO_SHORT -> Result.Invalid(Reason.TOO_SHORT)
            PhoneNumberUtil.ValidationResult.TOO_LONG -> Result.Invalid(Reason.TOO_LONG)
            PhoneNumberUtil.ValidationResult.INVALID_LENGTH -> Result.Invalid(Reason.WRONG_LENGTH)
            PhoneNumberUtil.ValidationResult.INVALID_COUNTRY_CODE, null -> Result.Invalid(Reason.INVALID)
        }
    }

    /**
     * Length check for the numbers a Telegram account can have: a mobile number, or a (shorter) landline.
     * The country-wide check alone would also accept e.g. Israel's 12-digit service numbers, so "0521234567890"
     * would not be "too long".
     *
     * Non-geographic codes (+800, +882, +888, ...) have no per-type rules and libphonenumber does not know every
     * range (e.g. Telegram's anonymous +888 numbers), so they only need a plausible E.164 length; TDLib decides.
     */
    private fun lengthCheck(number: Phonenumber.PhoneNumber): PhoneNumberUtil.ValidationResult {
        val general = util.isPossibleNumberWithReason(number)
        if (general == PhoneNumberUtil.ValidationResult.INVALID_COUNTRY_CODE) return general
        if (util.getRegionCodeForCountryCode(number.countryCode) == NON_GEO_REGION) {
            val digits = util.getNationalSignificantNumber(number).length + number.countryCode.toString().length
            return when {
                digits > MAX_E164_DIGITS -> PhoneNumberUtil.ValidationResult.TOO_LONG
                digits < MIN_NON_GEO_DIGITS -> PhoneNumberUtil.ValidationResult.TOO_SHORT
                else -> PhoneNumberUtil.ValidationResult.IS_POSSIBLE
            }
        }
        val mobile = util.isPossibleNumberForTypeWithReason(number, PhoneNumberUtil.PhoneNumberType.MOBILE)
        if (mobile == PhoneNumberUtil.ValidationResult.IS_POSSIBLE) return mobile
        val fixed = util.isPossibleNumberForTypeWithReason(number, PhoneNumberUtil.PhoneNumberType.FIXED_LINE)
        // Too short for a mobile but a valid landline length (Israel 02-123-4567)
        if (mobile == PhoneNumberUtil.ValidationResult.TOO_SHORT && fixed == PhoneNumberUtil.ValidationResult.IS_POSSIBLE) {
            return fixed
        }
        // Region without mobile data: fall back to the country-wide rules
        if (mobile == PhoneNumberUtil.ValidationResult.INVALID_LENGTH && fixed == PhoneNumberUtil.ValidationResult.INVALID_LENGTH) {
            return general
        }
        return mobile
    }

    /**
     * Country of a (possibly still incomplete) international number, for auto-selecting it in the picker:
     * `+972 52…` -> "IL", `+44` -> "GB". Null for local numbers or an unknown / incomplete country code.
     * Shared codes (+1, +7, …) resolve to the exact country once the number is complete, before that to the
     * code's main country.
     */
    fun regionForInternational(input: String): String? {
        val sanitized = sanitize(input)
        if (!sanitized.startsWith("+")) return null
        try {
            val number = util.parse(sanitized, UNKNOWN_REGION)
            regionOf(number)?.let { return it }
        } catch (e: NumberParseException) {
            // Still typing: fall back to the country code prefix below
        }
        val digits = sanitized.drop(1)
        // Country codes are prefix-free (1-3 digits), so the first known prefix is the code
        for (length in 1..minOf(3, digits.length)) {
            val region = util.getRegionCodeForCountryCode(digits.take(length).toInt())
            if (region != UNKNOWN_REGION && region in util.supportedRegions) return region
        }
        return null
    }

    /**
     * The digits after the country code of an international number (`+972 52-123-4567` -> `521234567`), used when
     * the user picks a different country for it. Local numbers are returned unchanged.
     */
    fun nationalPart(input: String): String {
        val sanitized = sanitize(input)
        if (!sanitized.startsWith("+")) return input
        try {
            return util.getNationalSignificantNumber(util.parse(sanitized, UNKNOWN_REGION))
        } catch (e: NumberParseException) {
            // Incomplete: drop the country code prefix if we can tell it
        }
        val digits = sanitized.drop(1)
        for (length in 1..minOf(3, digits.length)) {
            if (util.getRegionCodeForCountryCode(digits.take(length).toInt()) != UNKNOWN_REGION) return digits.drop(length)
        }
        return digits
    }

    private fun regionOf(number: Phonenumber.PhoneNumber): String? {
        val exact = util.getRegionCodeForNumber(number)
        if (exact != null && exact in util.supportedRegions) return exact
        return util.getRegionCodeForCountryCode(number.countryCode).takeIf { it in util.supportedRegions }
    }

    private fun reasonFor(type: NumberParseException.ErrorType): Reason = when (type) {
        NumberParseException.ErrorType.TOO_SHORT_NSN,
        NumberParseException.ErrorType.TOO_SHORT_AFTER_IDD -> Reason.TOO_SHORT
        NumberParseException.ErrorType.TOO_LONG -> Reason.TOO_LONG
        NumberParseException.ErrorType.INVALID_COUNTRY_CODE,
        NumberParseException.ErrorType.NOT_A_NUMBER -> Reason.INVALID
    }

    companion object {
        private const val UNKNOWN_REGION = "ZZ"
        private const val NON_GEO_REGION = "001"
        private const val MAX_E164_DIGITS = 15
        private const val MIN_NON_GEO_DIGITS = 8

        /** Characters the field accepts besides digits (they are ignored when normalizing). */
        private const val SEPARATORS = " -().\u00A0/"

        /** Digits (as ASCII) plus a leading "+"; separators and anything else are dropped. */
        fun sanitize(input: String): String {
            val trimmed = input.trim()
            val sb = StringBuilder(trimmed.length)
            trimmed.forEachIndexed { index, c ->
                when {
                    c == '+' && sb.isEmpty() && index == trimmed.indexOfFirst { it == '+' || it.isDigit() } -> sb.append('+')
                    c.isDigit() -> sb.append(Character.getNumericValue(c).coerceIn(0, 9))
                }
            }
            return sb.toString()
        }

        /**
         * What the text field keeps while typing: digits, separators and one "+" at the very start.
         * Letters and other symbols are dropped so the field never shows something we would ignore.
         */
        fun filterTyped(input: String): String {
            val sb = StringBuilder(input.length)
            input.forEach { c ->
                when {
                    c.isDigit() || c in SEPARATORS -> sb.append(c)
                    c == '+' && sb.none { it.isDigit() || it == '+' } -> sb.append(c)
                }
            }
            return sb.toString()
        }
    }
}
