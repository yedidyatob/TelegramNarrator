package com.example.telegramnarrator.core

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import java.util.Locale

/**
 * Looks up string resources in a specific language, independent of the device locale (used for the phrases
 * the app speaks, see [com.example.telegramnarrator.domain.audio.SpokenPhraseLanguage]).
 * English is the default `values/` resource set, Hebrew is `values-he/`.
 */
class SpokenStrings(private val context: Context) {
    private val contexts = HashMap<String, Context>()

    private fun contextFor(locale: Locale): Context = synchronized(contexts) {
        contexts.getOrPut(locale.language) {
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            context.createConfigurationContext(config)
        }
    }

    fun get(locale: Locale, @StringRes id: Int, vararg args: Any): String =
        contextFor(locale).getString(id, *args)
}
