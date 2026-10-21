package io.github.yedidyatob.telegramnarrator.data.language

import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import io.github.yedidyatob.telegramnarrator.domain.language.LanguageGuess
import io.github.yedidyatob.telegramnarrator.domain.language.LanguageIdentifier
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** ML Kit Language ID with the bundled model: fully on-device, no download, no text leaves the phone (#89). */
class MlKitLanguageIdentifier : LanguageIdentifier {
    private val client by lazy {
        LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder().setConfidenceThreshold(MIN_CONFIDENCE).build()
        )
    }

    override suspend fun identify(text: String): List<LanguageGuess> = suspendCancellableCoroutine { cont ->
        client.identifyPossibleLanguages(text.take(MAX_CHARS))
            .addOnSuccessListener { list ->
                cont.resume(list.map { LanguageGuess(it.languageTag, it.confidence) }.sortedByDescending { it.confidence })
            }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    private companion object {
        const val MIN_CONFIDENCE = 0.1f
        const val MAX_CHARS = 1000
    }
}
