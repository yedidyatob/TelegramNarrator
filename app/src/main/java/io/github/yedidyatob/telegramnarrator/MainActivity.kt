package io.github.yedidyatob.telegramnarrator

import io.github.yedidyatob.telegramnarrator.data.appearance.AppearancePreferences
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.viewModels
import android.os.SystemClock
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import io.github.yedidyatob.telegramnarrator.core.playback.PlaybackController
import io.github.yedidyatob.telegramnarrator.domain.model.AuthState
import io.github.yedidyatob.telegramnarrator.domain.model.Chat
import io.github.yedidyatob.telegramnarrator.ui.components.rememberNotificationPermissionGate
import io.github.yedidyatob.telegramnarrator.ui.navigation.AppRoute
import io.github.yedidyatob.telegramnarrator.ui.screens.auth.LoginScreen
import io.github.yedidyatob.telegramnarrator.ui.screens.home.HomeScreen
import io.github.yedidyatob.telegramnarrator.ui.screens.onboarding.OnboardingScreen
import io.github.yedidyatob.telegramnarrator.ui.screens.player.PlayerScreen
import io.github.yedidyatob.telegramnarrator.ui.theme.TelegramNarratorTheme
import io.github.yedidyatob.telegramnarrator.ui.viewmodel.AppNavigationViewModel
import io.github.yedidyatob.telegramnarrator.ui.viewmodel.AuthViewModel
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var playbackController: PlaybackController

    @Inject
    lateinit var appearance: AppearancePreferences

    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // AndroidX SplashScreen (#17): the icon stays while TDLib starts (at most SPLASH_MAX_MS), so the login
        // screen's "initializing" spinner rarely flashes
        val splash = installSplashScreen()
        val launchedAt = SystemClock.elapsedRealtime()
        splash.setKeepOnScreenCondition {
            authViewModel.authState.value is AuthState.Initializing &&
                SystemClock.elapsedRealtime() - launchedAt < SPLASH_MAX_MS
        }
        super.onCreate(savedInstanceState)
        // Draw behind transparent system bars; screens consume the insets via Scaffold / navigationBarsPadding
        enableEdgeToEdge()
        setContent {
            val dynamicColor by appearance.dynamicColor.collectAsStateWithLifecycle()
            TelegramNarratorTheme(dynamicColor = dynamicColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(playbackController, authViewModel)
                }
            }
        }
    }

    private companion object {
        const val SPLASH_MAX_MS = 1_500L
    }
}

@Composable
fun AppNavigation(
    playbackController: PlaybackController,
    authViewModel: AuthViewModel = hiltViewModel(),
    navigationViewModel: AppNavigationViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val onboardingCompleted by navigationViewModel.onboardingCompleted.collectAsStateWithLifecycle()

    LaunchedEffect(authState, onboardingCompleted) {
        if (authState is AuthState.Authenticated) navigationViewModel.onSignedIn()
        val target = AppRoute.redirect(authState, onboardingCompleted, navController.currentDestination?.route)
            ?: return@LaunchedEffect
        navController.navigate(target) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    // While TDLib starts, the login screen shows its "initializing" spinner
    NavHost(navController = navController, startDestination = AppRoute.LOGIN) {
        composable(AppRoute.ONBOARDING) {
            // Finishing marks it completed; the redirect above then moves on to login
            OnboardingScreen(onFinished = {})
        }
        composable(AppRoute.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    // Handled by LaunchedEffect
                },
                viewModel = authViewModel
            )
        }
        composable(
            AppRoute.HOME,
            // The player slides up over Home, which stays put underneath
            exitTransition = { if (targetState.destination.route == AppRoute.PLAYER) ExitTransition.KeepUntilTransitionsFinished else fadeOut() },
            popEnterTransition = { if (initialState.destination.route == AppRoute.PLAYER) EnterTransition.None else fadeIn() }
        ) {
            val startPlayback = rememberNotificationPermissionGate<List<Chat>> { chats ->
                playbackController.playChats(chats.map { it.id })
            }
            HomeScreen(
                onPlayAll = startPlayback,
                onOpenPlayer = {
                    if (navController.currentDestination?.route == AppRoute.HOME) {
                        navController.navigate(AppRoute.PLAYER) { launchSingleTop = true }
                    }
                }
            )
        }
        composable(
            AppRoute.PLAYER,
            enterTransition = { slideInVertically(tween(350)) { it } + fadeIn(tween(200)) },
            popExitTransition = { slideOutVertically(tween(300)) { it } + fadeOut(tween(300)) }
        ) {
            PlayerScreen(
                onDismiss = {
                    // Guarded: the auto-dismiss and the back gesture can both ask
                    if (navController.currentDestination?.route == AppRoute.PLAYER) navController.popBackStack()
                }
            )
        }
    }
}
