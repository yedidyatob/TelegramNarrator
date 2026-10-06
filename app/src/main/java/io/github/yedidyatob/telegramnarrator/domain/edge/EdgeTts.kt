package io.github.yedidyatob.telegramnarrator.domain.edge

import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Pure helpers for the **experimental** Microsoft Edge "Read aloud" neural TTS engine.
 *
 * This mirrors what the open-source `edge-tts` Python library (github.com/rany2/edge-tts) does: open a
 * WebSocket to the endpoint the Edge browser uses for "Read aloud", send a `speech.config` message and an
 * SSML request, and collect the MP3 chunks until `turn.end`. The service is **unofficial and undocumented**:
 * Microsoft may change or block it at any time, which is why playback falls back to system TTS on any error.
 * No API key is involved; the "trusted client token" below is the public constant baked into the Edge browser.
 */
object EdgeTts {
    const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    private const val BASE_PATH = "speech.platform.bing.com/consumer/speech/synthesize/readaloud"
    const val WSS_BASE_URL = "wss://$BASE_PATH/edge/v1"

    /** Edge version the request pretends to come from (kept in sync with edge-tts 7.2.x). */
    const val CHROMIUM_FULL_VERSION = "143.0.3650.75"
    val CHROMIUM_MAJOR_VERSION: String = CHROMIUM_FULL_VERSION.substringBefore('.')
    val SEC_MS_GEC_VERSION: String = "1-$CHROMIUM_FULL_VERSION"

    const val ORIGIN = "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold"
    val USER_AGENT: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/$CHROMIUM_MAJOR_VERSION.0.0.0 Safari/537.36 Edg/$CHROMIUM_MAJOR_VERSION.0.0.0"

    /** 48 kbps CBR mono MP3; plays fine in MediaPlayer and chunks can simply be concatenated. */
    const val OUTPUT_FORMAT = "audio-24khz-48kbitrate-mono-mp3"

    const val VOICE_AVRI = "he-IL-AvriNeural"
    const val VOICE_HILA = "he-IL-HilaNeural"
    const val VOICE_AVA_MULTILINGUAL = "en-US-AvaMultilingualNeural"
    const val VOICE_ANDREW_MULTILINGUAL = "en-US-AndrewMultilingualNeural"
    /** Fallback when a stored / typed voice name is invalid (only reachable through the Advanced field). */
    const val DEFAULT_VOICE = VOICE_AVRI

    /** The service rejects SSML text longer than this (UTF-8 bytes, after XML escaping); longer text is split. */
    const val MAX_TEXT_BYTES_PER_REQUEST = 4096

    private const val WIN_EPOCH_SECONDS = 11_644_473_600L
    private val VOICE_NAME = Regex("^[a-z]{2,3}(-[A-Za-z0-9]+)+Neural$")

    // ---- Voices ---------------------------------------------------------------------------------

    /** Edge voice short names look like `he-IL-AvriNeural`, `en-US-AvaMultilingualNeural`, `zh-CN-liaoning-XiaobeiNeural`. */
    fun isValidVoiceName(name: String?): Boolean {
        val trimmed = name?.trim() ?: return false
        return trimmed.length <= 64 && VOICE_NAME.matches(trimmed)
    }

    fun normalizeVoice(name: String?): String = if (isValidVoiceName(name)) name!!.trim() else DEFAULT_VOICE

    /** The Hebrew voice of [gender]: Avri (male) or Hila (female). */
    fun hebrewVoice(gender: EdgeVoiceGender): String = when (gender) {
        EdgeVoiceGender.MALE -> VOICE_AVRI
        EdgeVoiceGender.FEMALE -> VOICE_HILA
    }

    /** The multilingual voice of [gender] for every non-Hebrew message: Andrew (male) or Ava (female). */
    fun multilingualVoice(gender: EdgeVoiceGender): String = when (gender) {
        EdgeVoiceGender.MALE -> VOICE_ANDREW_MULTILINGUAL
        EdgeVoiceGender.FEMALE -> VOICE_AVA_MULTILINGUAL
    }

    /**
     * Voice actually used for one message. A custom (Advanced) voice is used as-is for every message.
     * Otherwise the Hebrew voices cannot read other scripts, so a message the
     * [io.github.yedidyatob.telegramnarrator.domain.audio.LanguageDetector] classifies as non-Hebrew ([language] is an
     * ISO code like "en") is read by the multilingual voice of the selected gender.
     */
    fun voiceFor(options: EdgeTtsOptions, language: String): String {
        options.customVoice?.let { if (isValidVoiceName(it)) return it.trim() }
        val hebrew = language.equals("he", ignoreCase = true) || language.equals("iw", ignoreCase = true)
        return if (hebrew) hebrewVoice(options.gender) else multilingualVoice(options.gender)
    }

    /**
     * Reads the stored Edge choice. Current builds store `edge_gender` (+ optional `edge_custom_voice`);
     * builds before the voice-settings redesign stored a single `edge_voice` short name, which is migrated:
     * Avri / Andrew -> male, Hila / Ava -> female, any other valid voice -> custom (Advanced) voice.
     */
    fun optionsFromStored(gender: String?, customVoice: String?, legacyVoice: String?): EdgeTtsOptions {
        if (gender != null || customVoice != null) {
            return EdgeTtsOptions(
                gender = EdgeVoiceGender.fromId(gender),
                customVoice = customVoice?.trim()?.takeIf { isValidVoiceName(it) }
            )
        }
        return when (legacyVoice?.trim()) {
            null, "" -> EdgeTtsOptions()
            VOICE_AVRI, VOICE_ANDREW_MULTILINGUAL -> EdgeTtsOptions(gender = EdgeVoiceGender.MALE)
            VOICE_HILA, VOICE_AVA_MULTILINGUAL -> EdgeTtsOptions(gender = EdgeVoiceGender.FEMALE)
            else -> EdgeTtsOptions(customVoice = legacyVoice.trim().takeIf { isValidVoiceName(it) })
        }
    }

    // ---- Cache ----------------------------------------------------------------------------------

    /**
     * Disk cache key: SHA-256 hex of voice + text. The speech rate is applied at playback (MediaPlayer
     * speed), so changing the rate does not invalidate the cache.
     */
    fun cacheKey(text: String, voice: String): String = sha256Hex("edge\u0000$voice\u0000$text")

    // ---- Request building -----------------------------------------------------------------------

    /**
     * The `Sec-MS-GEC` anti-abuse token: SHA-256 (upper-case hex) of the current Windows file time, rounded
     * down to 5 minutes, followed by the trusted client token. [unixSeconds] should already include any
     * clock-skew correction.
     */
    fun secMsGec(unixSeconds: Long): String {
        var seconds = unixSeconds + WIN_EPOCH_SECONDS
        seconds -= seconds % 300
        val ticks = seconds * 10_000_000L // 100-ns intervals
        return sha256Hex("$ticks$TRUSTED_CLIENT_TOKEN").uppercase(Locale.ROOT)
    }

    fun buildWssUrl(connectionId: String, secMsGec: String): String =
        "$WSS_BASE_URL?TrustedClientToken=$TRUSTED_CLIENT_TOKEN" +
            "&ConnectionId=$connectionId" +
            "&Sec-MS-GEC=$secMsGec" +
            "&Sec-MS-GEC-Version=$SEC_MS_GEC_VERSION"

    /** UUID without dashes, as the service expects for ConnectionId / X-RequestId. */
    fun newConnectionId(): String = UUID.randomUUID().toString().replace("-", "")

    /** Random 32-hex-char MUID sent as a cookie (like edge-tts). */
    fun newMuid(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(16).also { random.nextBytes(it) }
        return bytes.joinToString("") { "%02X".format(it) }
    }

    /** JavaScript-style UTC date string used in X-Timestamp headers. */
    fun dateToString(epochMillis: Long): String {
        val format = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return format.format(Date(epochMillis)) + " GMT+0000 (Coordinated Universal Time)"
    }

    /** Parses an HTTP `Date` header (RFC 2616) to unix seconds, or null. Used for clock-skew correction. */
    fun parseHttpDate(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("GMT")
            }
            format.parse(value.trim())?.time?.div(1000)
        } catch (e: Exception) {
            null
        }
    }

    fun speechConfigMessage(timestamp: String): String =
        "X-Timestamp:$timestamp\r\n" +
            "Content-Type:application/json; charset=utf-8\r\n" +
            "Path:speech.config\r\n\r\n" +
            "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{" +
            "\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"" +
            "},\"outputFormat\":\"$OUTPUT_FORMAT\"}}}}\r\n"

    /** [escapedText] must already be XML-escaped (see [prepareTextChunks]). */
    fun buildSsml(voice: String, escapedText: String, rate: String = "+0%"): String =
        "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>" +
            "<voice name='${normalizeVoice(voice)}'>" +
            "<prosody pitch='+0Hz' rate='$rate' volume='+0%'>" +
            escapedText +
            "</prosody></voice></speak>"

    fun ssmlMessage(requestId: String, timestamp: String, ssml: String): String =
        "X-RequestId:$requestId\r\n" +
            "Content-Type:application/ssml+xml\r\n" +
            // The trailing "Z" is not a mistake: Edge itself sends it (edge-tts copies the quirk)
            "X-Timestamp:${timestamp}Z\r\n" +
            "Path:ssml\r\n\r\n" +
            ssml

    // ---- Text preparation -----------------------------------------------------------------------

    /** The service rejects some control characters (e.g. vertical tab); replace them with spaces. */
    fun removeIncompatibleCharacters(text: String): String {
        val chars = text.toCharArray()
        for (i in chars.indices) {
            val code = chars[i].code
            if (code in 0..8 || code in 11..12 || code in 14..31) chars[i] = ' '
        }
        return String(chars)
    }

    /** Same as Python's `xml.sax.saxutils.escape`: &, < and >. */
    fun escapeXml(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** Cleans, escapes and splits [text] into SSML-ready chunks of at most [maxBytes] UTF-8 bytes. */
    fun prepareTextChunks(text: String, maxBytes: Int = MAX_TEXT_BYTES_PER_REQUEST): List<String> =
        splitByUtf8Bytes(escapeXml(removeIncompatibleCharacters(text)), maxBytes)

    /**
     * Splits [text] into trimmed, non-empty chunks of at most [maxBytes] UTF-8 bytes, preferring newlines,
     * then spaces, never cutting a surrogate pair or an XML entity like `&amp;`.
     */
    fun splitByUtf8Bytes(text: String, maxBytes: Int): List<String> {
        require(maxBytes > 0) { "maxBytes must be positive" }
        val chunks = mutableListOf<String>()
        var rest = text
        while (utf8Length(rest) > maxBytes) {
            // Longest prefix (in chars) that fits in maxBytes without splitting a surrogate pair
            var fit = 0
            var bytes = 0
            while (fit < rest.length) {
                val cp = rest.codePointAt(fit)
                val cpBytes = utf8Length(cp)
                if (bytes + cpBytes > maxBytes) break
                bytes += cpBytes
                fit += Character.charCount(cp)
            }
            var splitAt = rest.lastIndexOf('\n', fit - 1).takeIf { it > 0 }
                ?: rest.lastIndexOf(' ', fit - 1).takeIf { it > 0 }
                ?: fit
            // Do not cut inside an XML entity
            val amp = rest.lastIndexOf('&', splitAt - 1)
            if (amp >= 0 && rest.indexOf(';', amp).let { it == -1 || it >= splitAt }) {
                if (amp > 0) splitAt = amp
            }
            if (splitAt <= 0) splitAt = maxOf(1, fit)
            rest.substring(0, splitAt).trim().takeIf { it.isNotEmpty() }?.let(chunks::add)
            rest = rest.substring(splitAt)
        }
        rest.trim().takeIf { it.isNotEmpty() }?.let(chunks::add)
        return chunks
    }

    // ---- Response parsing -----------------------------------------------------------------------

    /** Header block of a text frame (`Path:turn.end` etc.), keys as sent. */
    fun parseTextFrameHeaders(frame: String): Map<String, String> {
        val end = frame.indexOf("\r\n\r\n").let { if (it < 0) frame.length else it }
        return parseHeaderLines(frame.substring(0, end))
    }

    /** A binary frame: 2-byte big-endian header length, the header lines, then the audio payload. */
    data class BinaryFrame(val path: String?, val contentType: String?, val audio: ByteArray)

    fun parseBinaryFrame(frame: ByteArray): BinaryFrame? {
        if (frame.size < 2) return null
        val headerLength = ((frame[0].toInt() and 0xFF) shl 8) or (frame[1].toInt() and 0xFF)
        if (2 + headerLength > frame.size) return null
        val headers = parseHeaderLines(String(frame, 2, headerLength, Charsets.UTF_8))
        return BinaryFrame(
            path = headers["Path"],
            contentType = headers["Content-Type"],
            audio = frame.copyOfRange(2 + headerLength, frame.size)
        )
    }

    private fun parseHeaderLines(block: String): Map<String, String> =
        block.split("\r\n").mapNotNull { line ->
            val colon = line.indexOf(':')
            if (colon <= 0) null else line.substring(0, colon).trim() to line.substring(colon + 1).trim()
        }.toMap()

    // ---- Utils ----------------------------------------------------------------------------------

    private fun utf8Length(text: String): Int {
        var total = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            total += utf8Length(cp)
            i += Character.charCount(cp)
        }
        return total
    }

    private fun utf8Length(codePoint: Int): Int = when {
        codePoint < 0x80 -> 1
        codePoint < 0x800 -> 2
        codePoint < 0x10000 -> 3
        else -> 4
    }

    private fun sha256Hex(payload: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

/** Male / female voice family for Edge TTS (see [EdgeTts.voiceFor]). */
enum class EdgeVoiceGender(val id: String) {
    MALE("male"),
    FEMALE("female");

    companion object {
        val DEFAULT = MALE

        fun fromId(id: String?): EdgeVoiceGender = values().firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** Non-secret Edge TTS choices persisted with the rest of [io.github.yedidyatob.telegramnarrator.domain.tts.TtsSettings]. */
data class EdgeTtsOptions(
    val gender: EdgeVoiceGender = EdgeVoiceGender.DEFAULT,
    /** Advanced: any Edge voice short name, used for every message and overriding [gender]; null = none. */
    val customVoice: String? = null
)
