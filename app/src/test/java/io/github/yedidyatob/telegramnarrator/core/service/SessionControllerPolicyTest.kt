package io.github.yedidyatob.telegramnarrator.core.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionControllerPolicyTest {
    private val own = "io.github.yedidyatob.telegramnarrator"

    @Test
    fun `own app, system UI and platform controllers are accepted`() {
        assertTrue(SessionControllerPolicy.isTrusted(own, own, isPlatformController = false))
        assertTrue(SessionControllerPolicy.isTrusted("com.android.systemui", own, isPlatformController = false))
        assertTrue(SessionControllerPolicy.isTrusted("com.android.bluetooth", own, isPlatformController = true))
    }

    @Test
    fun `other Media3 controllers are rejected`() {
        assertFalse(SessionControllerPolicy.isTrusted("com.example.other", own, isPlatformController = false))
    }

    @Test
    fun `Android Auto is accepted only while enabled`() {
        assertTrue(SessionControllerPolicy.ANDROID_AUTO_ENABLED)
        val auto = "com.google.android.projection.gearhead"
        assertTrue(SessionControllerPolicy.isTrusted(auto, own, isPlatformController = false))
        assertFalse(SessionControllerPolicy.isTrusted(auto, own, isPlatformController = false, autoEnabled = false))
    }

    @Test
    fun `only this app and Android Auto may browse and start reading`() {
        assertTrue(SessionControllerPolicy.canBrowse(own, own))
        assertTrue(SessionControllerPolicy.canBrowse("com.google.android.projection.gearhead", own))
        assertTrue(SessionControllerPolicy.canBrowse("com.google.android.carassistant", own))
        assertFalse(SessionControllerPolicy.canBrowse("com.example.other", own))
        assertFalse(SessionControllerPolicy.canBrowse("com.android.systemui", own))
        assertFalse(SessionControllerPolicy.canBrowse("com.google.android.projection.gearhead", own, autoEnabled = false))
    }
}
