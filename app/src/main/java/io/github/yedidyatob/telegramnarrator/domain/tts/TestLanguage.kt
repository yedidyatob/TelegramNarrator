package io.github.yedidyatob.telegramnarrator.domain.tts

/** Languages offered for the "Test voice" sample (kept small on purpose). */
enum class TestLanguage(
    val code: String,
) {
    ENGLISH("en"),
    SPANISH("es"),
    FRENCH("fr"),
    RUSSIAN("ru"),
    ARABIC("ar"),
    HEBREW("he"),
    ;

    companion object {
        val FALLBACK = ENGLISH

        /**
         * Default test language: the device UI language when it is in the list (legacy codes such as "iw" are
         * normalized), otherwise English.
         */
        fun defaultFor(uiLanguage: String?): TestLanguage {
            if (uiLanguage.isNullOrBlank()) return FALLBACK
            val code = TtsVoiceLogic.normalizeLanguage(uiLanguage)
            return values().firstOrNull { it.code == code } ?: FALLBACK
        }
    }
}
