package com.example.telegramnarrator.domain.audio

import java.util.regex.Pattern

object MessageCleaner {
    // Used when the caller doesn't supply a localized replacement for URLs
    const val DEFAULT_LINK_LABEL = "Link"

    // Regex for URLs (http/https/www)
    private val URL_PATTERN = Pattern.compile(
        "(https?://\\S+|www\\.\\S+)",
        Pattern.CASE_INSENSITIVE
    )

    // Letters/digits from any script (Hebrew included - note that \w is ASCII-only in Java)
    private const val WORD = "\\p{L}\\p{N}"

    // **bold** and __bold__ (the content must not start/end with whitespace)
    private val MARKDOWN_BOLD = Regex("(\\*\\*|__)(?=\\S)(.+?)(?<=\\S)\\1", RegexOption.DOT_MATCHES_ALL)

    // *italic* and _italic_. The markers must sit on a word boundary so that things like
    // snake_case_names, #hash_tags and 2*3 are left alone.
    private val MARKDOWN_ITALIC_STAR = Regex("(?<![$WORD*])\\*(?=[^\\s*])(.+?)(?<=[^\\s*])\\*(?![$WORD*])")
    private val MARKDOWN_ITALIC_UNDERSCORE = Regex("(?<![${WORD}_])_(?=[^\\s_])(.+?)(?<=[^\\s_])_(?![${WORD}_])")

    // `inline code` and ```code blocks```: the backticks are never meaningful when read aloud
    private val MARKDOWN_BACKTICKS = Regex("`+")

    // Unpaired markers left over after the pairs above were removed (e.g. "**" without a closing "**")
    private val STRAY_STARS = Regex("\\*+")
    private val STRAY_UNDERSCORE_RUNS = Regex("_{2,}")

    /**
     * @param linkLabel what URLs are replaced with (pass the localized word for "link").
     */
    fun clean(text: String, linkLabel: String = DEFAULT_LINK_LABEL): String {
        var cleaned = text

        // Replace URLs with the link label. Done first so that markdown characters inside URLs are never touched.
        val urlMatcher = URL_PATTERN.matcher(cleaned)
        cleaned = urlMatcher.replaceAll(java.util.regex.Matcher.quoteReplacement(linkLabel))

        // Remove Markdown characters
        cleaned = stripMarkdown(cleaned)

        // Remove Emojis and unassigned characters
        cleaned = cleaned.replace(Regex("[\\p{So}\\p{Cn}]"), "")

        // Collapse multiple spaces
        cleaned = cleaned.replace(Regex("\\s+"), " ")

        return cleaned.trim()
    }

    private fun stripMarkdown(text: String): String {
        var result = text
        result = MARKDOWN_BOLD.replace(result, "$2")
        result = MARKDOWN_ITALIC_STAR.replace(result, "$1")
        result = MARKDOWN_ITALIC_UNDERSCORE.replace(result, "$1")
        result = MARKDOWN_BACKTICKS.replace(result, "")
        result = STRAY_UNDERSCORE_RUNS.replace(result, "")
        // A lone "*" between two numbers is probably a multiplication ("2 * 3"), keep it
        val source = result
        result = STRAY_STARS.replace(source) { match ->
            if (isBetweenDigits(source, match.range)) match.value else ""
        }
        return result
    }

    private fun isBetweenDigits(text: String, range: IntRange): Boolean {
        var before = range.first - 1
        while (before >= 0 && text[before] == ' ') before--
        var after = range.last + 1
        while (after < text.length && text[after] == ' ') after++
        return before >= 0 && after < text.length && text[before].isDigit() && text[after].isDigit()
    }
}
