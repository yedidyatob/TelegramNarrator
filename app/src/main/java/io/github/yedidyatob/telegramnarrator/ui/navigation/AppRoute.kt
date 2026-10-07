package io.github.yedidyatob.telegramnarrator.ui.navigation

import io.github.yedidyatob.telegramnarrator.domain.model.AuthState
import io.github.yedidyatob.telegramnarrator.domain.onboarding.OnboardingFlow

/** Top-level destinations of the app's navigation graph. */
object AppRoute {
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val HOME = "home"
    const val PLAYER = "player"

    /** Destinations reachable while signed in; being on one of them doesn't need a redirect. */
    private val SIGNED_IN = setOf(HOME, PLAYER)

    /**
     * Where the auth state should take the user, or null to stay where they are: nothing while TDLib starts,
     * Home (or the Player on top of it) when signed in, otherwise the first-run onboarding once and then login.
     */
    fun redirect(authState: AuthState, onboardingCompleted: Boolean, currentRoute: String?): String? {
        val target = when (authState) {
            AuthState.Initializing -> return null
            AuthState.Authenticated -> if (currentRoute in SIGNED_IN) return null else HOME
            else -> if (OnboardingFlow.shouldShow(onboardingCompleted, signedIn = false)) ONBOARDING else LOGIN
        }
        return target.takeIf { it != currentRoute }
    }
}
