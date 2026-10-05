package com.example.telegramnarrator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.telegramnarrator.ui.viewmodel.AuthViewModel
import com.example.telegramnarrator.domain.model.AuthState
import com.example.telegramnarrator.ui.components.rememberNotificationPermissionGate
import com.example.telegramnarrator.ui.screens.auth.LoginScreen
import com.example.telegramnarrator.ui.screens.home.HomeScreen
import com.example.telegramnarrator.ui.theme.TelegramNarratorTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TelegramNarratorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation(authViewModel: AuthViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()

    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            if (navController.currentDestination?.route != "home") {
                navController.navigate("home") {
                    popUpTo("login") { inclusive = true }
                }
            }
        } else if (authState !is AuthState.Initializing) {
            if (navController.currentDestination?.route != "login") {
                navController.navigate("login") {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }
    
    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    // Handled by LaunchedEffect
                },
                viewModel = authViewModel
            )
        }
        composable("home") {
            val context = androidx.compose.ui.platform.LocalContext.current
            val startPlayback = rememberNotificationPermissionGate<List<com.example.telegramnarrator.domain.model.Chat>> { chats ->
                    val intent = android.content.Intent(context, com.example.telegramnarrator.core.service.PlaybackService::class.java).apply {
                        action = com.example.telegramnarrator.core.service.PlaybackService.ACTION_PLAY_ALL
                        putExtra(com.example.telegramnarrator.core.service.PlaybackService.EXTRA_CHAT_IDS, chats.map { it.id }.toLongArray())
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
            }
            HomeScreen(onPlayAll = startPlayback)
        }
    }
}
