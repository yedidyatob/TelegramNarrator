package io.github.yedidyatob.telegramnarrator.ui.viewmodel

import androidx.annotation.StringRes
import io.github.yedidyatob.telegramnarrator.R
import io.github.yedidyatob.telegramnarrator.domain.tts.TestLanguage

/** The language's own name, shown in the Test voice language dropdown. */
@get:StringRes
val TestLanguage.nameRes: Int
    get() = when (this) {
        TestLanguage.ENGLISH -> R.string.test_language_en
        TestLanguage.HEBREW -> R.string.test_language_he
        TestLanguage.ARABIC -> R.string.test_language_ar
        TestLanguage.RUSSIAN -> R.string.test_language_ru
        TestLanguage.SPANISH -> R.string.test_language_es
        TestLanguage.FRENCH -> R.string.test_language_fr
    }

/** The sample sentence spoken by Test voice in this language. */
@get:StringRes
val TestLanguage.sentenceRes: Int
    get() = when (this) {
        TestLanguage.ENGLISH -> R.string.test_sentence_en
        TestLanguage.HEBREW -> R.string.test_sentence_he
        TestLanguage.ARABIC -> R.string.test_sentence_ar
        TestLanguage.RUSSIAN -> R.string.test_sentence_ru
        TestLanguage.SPANISH -> R.string.test_sentence_es
        TestLanguage.FRENCH -> R.string.test_sentence_fr
    }
