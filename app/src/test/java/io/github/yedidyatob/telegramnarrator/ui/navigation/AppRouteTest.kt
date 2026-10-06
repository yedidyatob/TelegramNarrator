package io.github.yedidyatob.telegramnarrator.ui.navigation

import io.github.yedidyatob.telegramnarrator.domain.model.AuthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppRouteTest {

    @Test
    fun `stays put while TDLib starts`() {
        assertNull(AppRoute.redirect(AuthState.Initializing, onboardingCompleted = false, currentRoute = AppRoute.LOGIN))
    }

    @Test
    fun `first run goes to onboarding, then login`() {
        assertEquals(AppRoute.ONBOARDING, AppRoute.redirect(AuthState.WaitPhoneNumber, false, AppRoute.LOGIN))
        assertEquals(AppRoute.LOGIN, AppRoute.redirect(AuthState.WaitPhoneNumber, true, AppRoute.ONBOARDING))
        assertNull(AppRoute.redirect(AuthState.WaitCode, true, AppRoute.LOGIN))
    }

    @Test
    fun `login errors stay on login once onboarded`() {
        assertNull(AppRoute.redirect(AuthState.Error("PHONE_NUMBER_INVALID"), true, AppRoute.LOGIN))
    }

    @Test
    fun `signed in goes home and leaves the player alone`() {
        assertEquals(AppRoute.HOME, AppRoute.redirect(AuthState.Authenticated, false, AppRoute.LOGIN))
        assertNull(AppRoute.redirect(AuthState.Authenticated, true, AppRoute.HOME))
        assertNull(AppRoute.redirect(AuthState.Authenticated, true, AppRoute.PLAYER))
    }

    @Test
    fun `logging out from home or the player returns to login`() {
        assertEquals(AppRoute.LOGIN, AppRoute.redirect(AuthState.WaitPhoneNumber, true, AppRoute.HOME))
        assertEquals(AppRoute.LOGIN, AppRoute.redirect(AuthState.WaitPhoneNumber, true, AppRoute.PLAYER))
    }
}
