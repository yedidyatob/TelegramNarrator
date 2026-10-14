package io.github.yedidyatob.telegramnarrator.domain.audio

import java.util.regex.Pattern

object MessageCleaner {
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
     * Telegram-style hashtags: `#foo_bar_baz`, `#שלום_עולם`.
     * Leading `#` is kept (spoken as "hash" / "number sign"); underscores become spaces
     * so the engine reads words instead of "underscore". Letters and digits of any script are kept.
     */
    private val HASHTAG = Regex("#([${WORD}_]+)")

    /**
     * @param linkLabel what URLs are replaced with. By default URLs are removed (not read at all);
     * pass a word such as "link" to have them read as that word instead.
     */
    fun clean(text: String, linkLabel: String = ""): String {
        var cleaned = text

        // Remove URLs (or replace them with the link label). Done first so that markdown characters inside URLs are never touched.
        val urlMatcher = URL_PATTERN.matcher(cleaned)
        cleaned = urlMatcher.replaceAll(java.util.regex.Matcher.quoteReplacement(linkLabel))

        // Remove Markdown characters
        cleaned = stripMarkdown(cleaned)

        // Repeated punctuation / separator runs ("!!!", "....", "----", standalone "##"). Before the hashtag
        // expansion so "##tag" becomes "#tag" -> "# tag" instead of "## tag".
        cleaned = collapseSymbolRuns(cleaned)

        // Expand #hashtags for TTS (after markdown so #hash_tags were left intact by italic rules)
        cleaned = expandHashtags(cleaned)

        // Remove Emojis and unassigned characters, and the emoji variation selector / zero-width joiner they leave
        cleaned = cleaned.replace(Regex("[\\p{So}\\p{Cn}\\uFE0F\\u200D]"), "")

        // Drop symbol-only lines ("##", "---", "•••", "☝️") and join the rest with pauses. Needs the line
        // structure, so it runs before whitespace is collapsed.
        cleaned = joinSpeakableLines(cleaned)

        // Collapse multiple spaces
        cleaned = cleaned.replace(Regex("\\s+"), " ")

        return cleaned.trim()
    }

    // "!!!" -> "!", "???" -> "?" (mixed "?!" is kept: it is intonation)
    private val REPEATED_BANG_OR_QUESTION = Regex("([!?])\\1+")
    // "...." / "…" -> "." (a pause, never "dot dot dot")
    private val ELLIPSIS = Regex("\\.(?:[ \\t]?\\.)+|…+")
    // "##tag" -> "#tag"; a "##" / "###" run that does not start a word is removed
    private val HASH_RUN_BEFORE_WORD = Regex("#{2,}(?=[$WORD])")
    private val HASH_RUN = Regex("#{2,}")
    // Separator runs: "----", "====", "~~~~", "••••", "····" -> a space ("_" runs are removed with the markdown)
    private val SEPARATOR_RUN = Regex("([-=~•·])\\1+")
    // Line endings that already give a pause; closing brackets / quotes do not
    private const val PAUSE_PUNCTUATION = ".,!?:;"

    /**
     * Collapses runs of repeated punctuation that mean nothing when spoken. Single ordinary punctuation and
     * meaningful symbols ("-", "%", "₪", "$", "+", "/", ":", quotes and gershayim as in רע"מ / צה"ל) are kept.
     */
    internal fun collapseSymbolRuns(text: String): String {
        var result = text
        result = REPEATED_BANG_OR_QUESTION.replace(result, "$1")
        result = ELLIPSIS.replace(result, ".")
        result = HASH_RUN_BEFORE_WORD.replace(result, "#")
        result = HASH_RUN.replace(result, "")
        result = SEPARATOR_RUN.replace(result, " ")
        return result
    }

    /**
     * Drops every line without a letter or digit (separators like "##", "---", "***", "•••", ". . .", emoji-only
     * lines) and joins the remaining lines into one utterance with pauses: a line that does not already end in
     * punctuation gets ", " before the next line, or ". " when a blank or dropped line (a paragraph break)
     * follows, so list-like lines (party names, headlines) are read with pauses instead of running together.
     */
    internal fun joinSpeakableLines(text: String): String {
        val out = StringBuilder()
        var paragraphBreak = false
        for (raw in text.split('\n')) {
            val line = raw.trim()
            if (!line.any { it.isLetterOrDigit() }) {
                // Blank / symbol-only line: a paragraph break before the next spoken line
                if (out.isNotEmpty()) paragraphBreak = true
                continue
            }
            if (out.isNotEmpty()) {
                if (out.last() !in PAUSE_PUNCTUATION) out.append(if (paragraphBreak) '.' else ',')
                out.append(' ')
            }
            out.append(line)
            paragraphBreak = false
        }
        return out.toString()
    }

    /** `#foo_bar` → `# foo bar`; `#tag` → `# tag`. Non-hashtag snake_case is unchanged. */
    internal fun expandHashtags(text: String): String =
        HASHTAG.replace(text) { match ->
            "# " + match.groupValues[1].replace('_', ' ')
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
