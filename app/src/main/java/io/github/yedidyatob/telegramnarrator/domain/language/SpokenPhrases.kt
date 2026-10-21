package io.github.yedidyatob.telegramnarrator.domain.language

/**
 * The few fixed phrases that are spoken, not shown (#89): in the chat's language, English when there is no
 * translation. These follow the content, not the app's UI language, so they are not string resources.
 */
object SpokenPhrases {
    private val SPONSORED = mapOf(
        "en" to "Sponsored", "he" to "ממומן", "ar" to "إعلان ممول", "ru" to "Реклама", "uk" to "Реклама",
        "es" to "Patrocinado", "fr" to "Sponsorisé", "de" to "Gesponsert", "it" to "Sponsorizzato",
        "pt" to "Patrocinado", "tr" to "Sponsorlu", "fa" to "تبلیغ", "nl" to "Gesponsord", "pl" to "Sponsorowane"
    )
    private val RECOMMENDED = mapOf(
        "en" to "Recommended", "he" to "מומלץ", "ar" to "موصى به", "ru" to "Рекомендуем", "uk" to "Рекомендовано",
        "es" to "Recomendado", "fr" to "Recommandé", "de" to "Empfohlen", "it" to "Consigliato",
        "pt" to "Recomendado", "tr" to "Önerilen", "fa" to "پیشنهادی", "nl" to "Aanbevolen", "pl" to "Polecane"
    )

    /** Languages with their own spoken phrases (others use English). */
    val LANGUAGES: Set<String> get() = SPONSORED.keys

    /** "Sponsored" / "Recommended" before an official sponsored message, in [language] (else English). */
    fun sponsoredCue(language: String, recommended: Boolean): String {
        val table = if (recommended) RECOMMENDED else SPONSORED
        return table[ChatLanguagePolicy.normalize(language)] ?: table.getValue("en")
    }
}
