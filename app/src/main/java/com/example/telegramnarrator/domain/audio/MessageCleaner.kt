package com.example.telegramnarrator.domain.audio

import java.util.regex.Pattern

object MessageCleaner {
    // Regex for URLs (http/https/www)
    private val URL_PATTERN = Pattern.compile(
        "(https?://\\S+|www\\.\\S+)",
        Pattern.CASE_INSENSITIVE
    )

    // Regex for Markdown bold/italic (*, _, `)
    private val MARKDOWN_PATTERN = Pattern.compile("[*`_]")

    fun clean(text: String): String {
        var cleaned = text

        // Replace URLs with "Link"
        val urlMatcher = URL_PATTERN.matcher(cleaned)
        cleaned = urlMatcher.replaceAll("Link")

        // Remove Markdown characters (simple approach: just remove the chars, rely on TTS to read text)
        // Alternatively, we could try to parse them, but for TTS just stripping separators is usually enough.
        // We might want to keep some punctuation.
        // let's just strip double asterisks or underscores often used for bold
        cleaned = cleaned.replace("**", "").replace("__", "")

        // Collapse multiple spaces
        cleaned = cleaned.replace(Regex("\\s+"), " ")

        return cleaned.trim()
    }
}
