package io.github.yedidyatob.telegramnarrator.core.service

/**
 * Which MediaControllers may connect to the playback session (#41 groundwork).
 *
 * The service is an exported MediaLibraryService for Android Auto (#41). Only this app (including Media3's
 * own notification controller), the system UI and the Android Auto hosts are accepted as Media3 controllers;
 * every other package is rejected, and only [canBrowse] controllers may list chats or start reading, so
 * other apps can never start reading Telegram messages.
 *
 * Controllers of the platform session (lock screen, Bluetooth / headset keys, watches, notification
 * listeners) are accepted as before: they can only send transport commands to a session that is
 * already playing, which the legacy MediaSessionCompat always allowed.
 */
object SessionControllerPolicy {
    /** Android Auto hosts (#41), accepted while [ANDROID_AUTO_ENABLED]. */
    val ANDROID_AUTO_PACKAGES = setOf(
        "com.google.android.projection.gearhead",
        "com.google.android.carassistant"
    )
    const val ANDROID_AUTO_ENABLED = true
    private val SYSTEM_PACKAGES = setOf("android", "com.android.systemui")

    fun isTrusted(
        packageName: String,
        ownPackage: String,
        isPlatformController: Boolean,
        autoEnabled: Boolean = ANDROID_AUTO_ENABLED
    ): Boolean = isPlatformController || packageName == ownPackage || packageName in SYSTEM_PACKAGES || (autoEnabled && packageName in ANDROID_AUTO_PACKAGES)

    /**
     * May list chats and start reading (browse tree, setMediaItems): only this app and Android Auto. Platform
     * (legacy) controllers, which the exported service now also lets any app create, only get transport.
     */
    fun canBrowse(
        packageName: String,
        ownPackage: String,
        autoEnabled: Boolean = ANDROID_AUTO_ENABLED
    ): Boolean = packageName == ownPackage || (autoEnabled && packageName in ANDROID_AUTO_PACKAGES)
}
