package io.github.yedidyatob.telegramnarrator.data.rules

import io.github.yedidyatob.telegramnarrator.domain.cleaning.ChannelMatch
import io.github.yedidyatob.telegramnarrator.domain.cleaning.ChannelPreset
import io.github.yedidyatob.telegramnarrator.domain.cleaning.ChannelRulesConfig
import io.github.yedidyatob.telegramnarrator.domain.cleaning.CleaningPreset
import io.github.yedidyatob.telegramnarrator.domain.cleaning.DropNextRule
import io.github.yedidyatob.telegramnarrator.domain.cleaning.NumberedListMode
import io.github.yedidyatob.telegramnarrator.domain.cleaning.ReplaceRule
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.regex.PatternSyntaxException

class ChannelRulesParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Parses the channel rules JSON (see docs/channel-rules.md). Uses org.json, which is part of
 * Android, so no extra dependency is needed. Any mistake (bad JSON, invalid regex, unknown option
 * value) throws a [ChannelRulesParseException] naming the offending entry.
 */
object ChannelRulesParser {

    fun parse(json: String): ChannelRulesConfig {
        try {
            val root = JSONObject(json)
            val default = resolve(parseSpec(root.optJSONObject("default"), "default"), inherited = null)
            val channels = mutableListOf<ChannelPreset>()
            val channelsJson = root.optJSONArray("channels") ?: JSONArray()
            for (i in 0 until channelsJson.length()) {
                val channel = channelsJson.getJSONObject(i)
                val name = channel.optString("name", "channels[$i]")
                val where = "channel '$name'"
                val spec = parseSpec(channel, where)
                val inherit = if (channel.has("inheritDefault")) channel.getBoolean("inheritDefault") else true
                channels.add(
                    ChannelPreset(
                        name = name,
                        match = parseMatch(channel.optJSONObject("match"), where),
                        preset = resolve(spec, inherited = if (inherit) default else null)
                    )
                )
            }
            return ChannelRulesConfig(default, channels)
        } catch (e: ChannelRulesParseException) {
            throw e
        } catch (e: JSONException) {
            throw ChannelRulesParseException("Invalid channel rules JSON: ${e.message}", e)
        }
    }

    // --- parsing into a spec where unset options are null, so they can inherit from the default ---

    private class Spec(
        val dropMessage: List<Regex>,
        val dropNext: List<DropNextRule>,
        val cut: List<Regex>,
        val replace: List<ReplaceRule>,
        val readLinks: Boolean?,
        val linkLabel: String?,
        val removeTelegramLinks: Boolean?,
        val numberedLists: NumberedListMode?,
        val stripSymbols: List<String>
    )

    private fun parseSpec(json: JSONObject?, where: String): Spec {
        val rules = json?.optJSONObject("rules")
        val options = json?.optJSONObject("options")
        return Spec(
            dropMessage = strings(rules, "dropMessage").map { regex(it, "$where: dropMessage") },
            dropNext = objects(rules, "dropNext").map {
                val count = it.optInt("count", 1)
                if (count < 0) throw ChannelRulesParseException("$where: dropNext count must be >= 0")
                DropNextRule(regex(requireString(it, "pattern", "$where: dropNext"), "$where: dropNext"), count)
            },
            cut = strings(rules, "cut").map { regex(it, "$where: cut") },
            replace = objects(rules, "replace").map {
                ReplaceRule(
                    regex(requireString(it, "pattern", "$where: replace"), "$where: replace"),
                    it.optString("replacement", "")
                )
            },
            readLinks = if (options != null && options.has("readLinks")) options.getBoolean("readLinks") else null,
            linkLabel = if (options != null && options.has("linkLabel") && !options.isNull("linkLabel")) options.getString("linkLabel") else null,
            removeTelegramLinks = if (options != null && options.has("removeTelegramLinks")) options.getBoolean("removeTelegramLinks") else null,
            numberedLists = options?.optString("numberedLists", "")?.takeIf { it.isNotEmpty() }?.let {
                when (it.lowercase()) {
                    "keep" -> NumberedListMode.KEEP
                    "strip" -> NumberedListMode.STRIP
                    "natural" -> NumberedListMode.NATURAL
                    else -> throw ChannelRulesParseException("$where: numberedLists must be keep, strip or natural (was '$it')")
                }
            },
            stripSymbols = strings(options, "stripSymbols")
        )
    }

    private fun resolve(spec: Spec, inherited: CleaningPreset?): CleaningPreset {
        val base = inherited ?: CleaningPreset()
        return CleaningPreset(
            dropMessage = base.dropMessage + spec.dropMessage,
            dropNext = base.dropNext + spec.dropNext,
            cut = base.cut + spec.cut,
            replace = base.replace + spec.replace,
            readLinks = spec.readLinks ?: base.readLinks,
            linkLabel = spec.linkLabel ?: base.linkLabel,
            removeTelegramLinks = spec.removeTelegramLinks ?: base.removeTelegramLinks,
            numberedLists = spec.numberedLists ?: base.numberedLists,
            stripSymbols = base.stripSymbols + spec.stripSymbols
        )
    }

    private fun parseMatch(json: JSONObject?, where: String): ChannelMatch {
        if (json == null) throw ChannelRulesParseException("$where: missing 'match'")
        val ids = json.optJSONArray("chatIds")
        val chatIds = if (ids == null) emptySet() else (0 until ids.length()).map { ids.getLong(it) }.toSet()
        val equals = if (json.has("titleEquals")) json.getString("titleEquals") else null
        val contains = if (json.has("titleContains")) json.getString("titleContains") else null
        if (chatIds.isEmpty() && equals == null && contains == null) {
            throw ChannelRulesParseException("$where: 'match' needs chatIds, titleEquals or titleContains")
        }
        return ChannelMatch(chatIds, equals, contains)
    }

    private fun regex(pattern: String, where: String): Regex = try {
        Regex(pattern, RegexOption.MULTILINE)
    } catch (e: PatternSyntaxException) {
        throw ChannelRulesParseException("$where: invalid regex '$pattern': ${e.description}", e)
    }

    private fun strings(json: JSONObject?, key: String): List<String> {
        val array = json?.optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).map { array.getString(it) }
    }

    private fun objects(json: JSONObject?, key: String): List<JSONObject> {
        val array = json?.optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).map { array.getJSONObject(it) }
    }

    private fun requireString(json: JSONObject, key: String, where: String): String {
        if (!json.has(key)) throw ChannelRulesParseException("$where: missing '$key'")
        return json.getString(key)
    }
}
