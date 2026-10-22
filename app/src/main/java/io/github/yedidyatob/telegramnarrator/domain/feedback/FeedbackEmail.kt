package io.github.yedidyatob.telegramnarrator.domain.feedback

/**
 * The "Send feedback" email (About): only app / build / device facts and an empty area for the user's
 * message. Never message text, chat data or the phone number.
 */
object FeedbackEmail {
    const val ADDRESS = "telegram.narrator.app@gmail.com"

    data class Info(
        val versionName: String,
        val versionCode: Int,
        val buildType: String,
        val ciRun: String,
        val gitSha: String,
        val androidRelease: String,
        val sdkInt: Int,
        val manufacturer: String,
        val model: String
    )

    fun body(prompt: String, info: Info): String = buildString {
        append(prompt).append("\n\n\n\n\n")
        append("----\n")
        append("App: ${info.versionName} (${info.versionCode}) ${info.buildType}")
        val ci = listOf(info.ciRun.takeIf { it.isNotBlank() }?.let { "CI #$it" }, info.gitSha.takeIf { it.isNotBlank() })
            .filterNotNull()
        if (ci.isNotEmpty()) append(" · ").append(ci.joinToString(" "))
        append('\n')
        append("Android: ${info.androidRelease} (API ${info.sdkInt})\n")
        append("Device: ${info.manufacturer} ${info.model}\n")
    }

    /** mailto: URI with the subject and body encoded by [encode] (Uri.encode in the app). */
    fun mailto(subject: String, body: String, encode: (String) -> String): String =
        "mailto:$ADDRESS?subject=${encode(subject)}&body=${encode(body)}"
}
