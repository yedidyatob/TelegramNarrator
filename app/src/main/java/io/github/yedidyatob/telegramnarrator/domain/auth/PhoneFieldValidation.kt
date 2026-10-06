package io.github.yedidyatob.telegramnarrator.domain.auth

/**
 * Which validation message the phone field shows inline. "Too long" and unknown country codes are shown while
 * typing (more digits cannot fix them); "too short", "wrong length" and "no country" only after the user tried to
 * send, so the field does not complain about a number that is still being typed.
 */
object PhoneFieldValidation {
    fun visibleReason(result: PhoneNumberNormalizer.Result, submitAttempted: Boolean): PhoneNumberNormalizer.Reason? {
        val reason = (result as? PhoneNumberNormalizer.Result.Invalid)?.reason ?: return null
        return when (reason) {
            PhoneNumberNormalizer.Reason.EMPTY -> null
            PhoneNumberNormalizer.Reason.TOO_LONG,
            PhoneNumberNormalizer.Reason.INVALID -> reason
            PhoneNumberNormalizer.Reason.TOO_SHORT,
            PhoneNumberNormalizer.Reason.WRONG_LENGTH,
            PhoneNumberNormalizer.Reason.NO_COUNTRY -> reason.takeIf { submitAttempted }
        }
    }

    /** Country the picker shows: the one of a typed / pasted international number, else the user's choice. */
    fun effectiveRegion(text: String, selectedRegion: String?, normalizer: PhoneNumberNormalizer): String? =
        normalizer.regionForInternational(text) ?: selectedRegion
}
