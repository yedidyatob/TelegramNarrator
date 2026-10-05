package com.example.telegramnarrator.domain.tts

import com.example.telegramnarrator.domain.openai.OpenAiTtsOptions

/** An installed offline voice of the system TTS engine (a platform-independent copy of android.speech.tts.Voice). */
data class VoiceOption(
    val name: String,
    /** BCP-47 tag, e.g. "he-IL". */
    val localeTag: String,
    /** Language part only, normalized with [TtsVoiceLogic.normalizeLanguage] ("he", "en", ...). */
    val language: String,
    /** android.speech.tts.Voice quality (100 = very low ... 500 = very high). */
    val quality: Int,
    val requiresNetwork: Boolean,
    /** false when the voice data still has to be downloaded (not usable offline yet). */
    val installed: Boolean
)

/** A TTS engine app installed on the device (e.g. Google, Samsung, eSpeak). */
data class EngineOption(val packageName: String, val label: String)

/**
 * The user's TTS choices.
 * @param enginePackage engine to use; null = the system default engine.
 * @param speechRate 1.0 = normal speed.
 * @param voices chosen voice name per language ("he" -> "he-il-x-heb-local"); a language without an
 * entry uses the engine's default voice.
 */
data class TtsSettings(
    val enginePackage: String? = null,
    val speechRate: Float = TtsVoiceLogic.DEFAULT_RATE,
    val voices: Map<String, String> = emptyMap(),
    /** User's choice for "mark messages as read in Telegram"; null = not chosen, default on. */
    val markAsReadOverride: Boolean? = null,
    /** Optional Bring-Your-Own-Key OpenAI TTS (default off — system TTS). */
    val openAi: OpenAiTtsOptions = OpenAiTtsOptions()
) {
    /**
     * Whether played messages are marked as read in Telegram. Defaults to **on** for every build
     * (debug and release). Device testing previously left this off in debug APKs, so listening never
     * marked messages read and "Play from start" replayed the same unread set. The Voice settings
     * switch still lets the user turn it off explicitly ([markAsReadOverride] = false).
     *
     * [isDebugBuild] is kept for call-site compatibility; it no longer changes the default.
     */
    @Suppress("UNUSED_PARAMETER")
    fun markAsReadEnabled(isDebugBuild: Boolean): Boolean = markAsReadOverride ?: true
}

/** Pure helpers behind the TTS settings (no Android dependencies, unit tested). */
object TtsVoiceLogic {
    const val MIN_RATE = 0.5f
    const val MAX_RATE = 2.0f
    const val DEFAULT_RATE = 1.0f
    const val RATE_STEP = 0.1f

    private const val HEBREW = "he"
    private const val ENGLISH = "en"

    /** Lower-case language code with the legacy Java codes mapped to the modern ones ("iw" -> "he"). */
    fun normalizeLanguage(languageOrTag: String): String {
        val language = languageOrTag.trim().replace('_', '-').substringBefore('-').lowercase()
        return when (language) {
            "iw" -> "he"
            "in" -> "id"
            "ji" -> "yi"
            else -> language
        }
    }

    /** Clamps to [MIN_RATE]..[MAX_RATE] and rounds to one decimal; NaN / infinity give the default. */
    fun clampRate(rate: Float): Float {
        if (rate.isNaN() || rate.isInfinite()) return DEFAULT_RATE
        val clamped = rate.coerceIn(MIN_RATE, MAX_RATE)
        return Math.round(clamped * 10f) / 10f
    }

    /** "1.0x" style label. */
    fun rateLabel(rate: Float): String = String.format(java.util.Locale.US, "%.1fx", clampRate(rate))

    /**
     * The languages to offer voices for: Hebrew first (the app's main language), then the device
     * languages in their order of preference, then English (the language of Latin-script text and of the
     * English spoken phrases). No duplicates.
     */
    fun languagesToShow(deviceLanguages: List<String>): List<String> =
        (listOf(HEBREW) + deviceLanguages.map { normalizeLanguage(it) } + ENGLISH)
            .filter { it.isNotEmpty() }
            .distinct()

    /** Only voices that work offline with the system engine: installed and not needing a network. */
    fun usableVoices(all: List<VoiceOption>): List<VoiceOption> =
        all.filter { it.installed && !it.requiresNetwork }

    /**
     * Usable voices grouped by language for the given [languages] (in that order, empty groups kept so
     * that the UI can say "no voice installed"). Inside a group the best quality comes first, then by name.
     */
    fun voicesByLanguage(all: List<VoiceOption>, languages: List<String>): LinkedHashMap<String, List<VoiceOption>> {
        val usable = usableVoices(all)
        val result = LinkedHashMap<String, List<VoiceOption>>()
        for (language in languages) {
            result[language] = usable
                .filter { it.language == language }
                .sortedWith(compareByDescending<VoiceOption> { it.quality }.thenBy { it.name })
        }
        return result
    }

    /** The voice to apply for [language]: the user's choice if it is (still) usable and of that language, else null = engine default. */
    fun chosenVoiceFor(settings: TtsSettings, language: String, all: List<VoiceOption>): VoiceOption? {
        val key = normalizeLanguage(language)
        val name = settings.voices[key] ?: return null
        return usableVoices(all).firstOrNull { it.name == name && it.language == key }
    }

    /** [settings] with the voice for [language] set to [voiceName], or back to the default when null. */
    fun withVoice(settings: TtsSettings, language: String, voiceName: String?): TtsSettings {
        val key = normalizeLanguage(language)
        val voices = settings.voices.toMutableMap()
        if (voiceName == null) voices.remove(key) else voices[key] = voiceName
        return settings.copy(voices = voices)
    }

    fun withRate(settings: TtsSettings, rate: Float): TtsSettings = settings.copy(speechRate = clampRate(rate))

    /** Switching the engine drops the voice choices: voice names belong to one engine. */
    fun withEngine(settings: TtsSettings, enginePackage: String?): TtsSettings =
        if (settings.enginePackage == enginePackage) settings else settings.copy(enginePackage = enginePackage, voices = emptyMap())

    /** Short human readable name for a voice, e.g. "he-IL · high quality". */
    fun describe(voice: VoiceOption): String {
        val quality = when {
            voice.quality >= 400 -> "high quality"
            voice.quality >= 300 -> "normal quality"
            else -> "basic quality"
        }
        return "${voice.localeTag} · $quality"
    }

    /** The sentence used by the "Test voice" button. */
    fun testSentence(language: String): String = when (normalizeLanguage(language)) {
        HEBREW -> "שלום, זהו קול לבדיקה. כך נשמעות ההודעות שלך."
        else -> "Hello, this is a test of the selected voice. This is how your messages will sound."
    }
}
