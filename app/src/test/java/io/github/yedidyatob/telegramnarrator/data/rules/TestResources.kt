package io.github.yedidyatob.telegramnarrator.data.rules

import java.io.File

object TestResources {
    /** A file from src/test/resources/channel_rules/abu_ali, read as UTF-8. */
    fun sample(name: String): String {
        val stream = TestResources::class.java.getResourceAsStream("/channel_rules/abu_ali/$name")
            ?: error("Missing test resource $name")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    /** The channel_rules.json that ships in the app assets (Gradle runs unit tests from app/). */
    fun shippedRulesJson(): String {
        val file = listOf("src/main/assets/channel_rules.json", "app/src/main/assets/channel_rules.json")
            .map { File(it) }.firstOrNull { it.exists() } ?: error("channel_rules.json asset not found")
        return file.readText(Charsets.UTF_8)
    }
}
