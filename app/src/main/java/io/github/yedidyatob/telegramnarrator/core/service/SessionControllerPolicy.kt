package io.github.yedidyatob.telegramnarrator.core.service

/**
 * Which MediaControllers may connect to the playback session (#41 groundwork).
 *
 * The service is not exported, so today only this app (including Media3's own notification controller)
 * and the system UI can reach the session. Android Auto needs an exported MediaLibraryService; when it is
 * enabled, its hosts must be added here and every other package keeps being rejected, so other apps can
 * never start reading Telegram messages.
 *
 * Controllers of the platform session (lock screen, Bluetooth / headset keys, watches, notification
 * listeners) are accepted as before: they can only send transport commands to a session that is
 * already playing, which the legacy MediaSessionCompat always allowed.
 */
object SessionControllerPolicy {
    /** Android Auto hosts, accepted only once [ANDROID_AUTO_ENABLED] is turned on (#41). */
    val ANDROID_AUTO_PACKAGES = setOf(
        "com.google.android.projection.gearhead",
        "com.google.android.carassistant"
    )
    const val ANDROID_AUTO_ENABLED = false
    private val SYSTEM_PACKAGES = setOf("android", "com.android.systemui")

    fun isTrusted(
        packageName: String,
        ownPackage: String,
        isPlatformController: Boolean,
        autoEnabled: Boolean = ANDROID_AUTO_ENABLED
    ): Boolean = isPlatformController || packageName == ownPackage || packageName in SYSTEM_PACKAGES || (autoEnabled && packageName in ANDROID_AUTO_PACKAGES)
}
