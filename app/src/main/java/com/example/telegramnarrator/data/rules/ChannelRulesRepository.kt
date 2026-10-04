package com.example.telegramnarrator.data.rules

import android.content.Context
import android.util.Log
import com.example.telegramnarrator.domain.cleaning.ChannelRulesConfig
import com.example.telegramnarrator.domain.cleaning.ChannelRulesEngine
import com.example.telegramnarrator.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the channel rules shipped in assets/channel_rules.json. If the file is missing or invalid
 * the error is logged and no rules are applied (only the generic MessageCleaner).
 */
@Singleton
class ChannelRulesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val ASSET_NAME = "channel_rules.json"
    }

    val engine: ChannelRulesEngine by lazy { ChannelRulesEngine(load(), defaultLinkLabel = { context.getString(R.string.playback_link) }) }

    private fun load(): ChannelRulesConfig = try {
        val json = context.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
        ChannelRulesParser.parse(json).also {
            Log.d("ChannelRules", "Loaded ${it.channels.size} channel presets")
        }
    } catch (e: Exception) {
        Log.e("ChannelRules", "Could not load $ASSET_NAME, using no channel rules", e)
        ChannelRulesConfig.EMPTY
    }
}
