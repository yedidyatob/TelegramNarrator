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
fun AppNavigation() {
    val navController = rememberNavController()
    
    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }
        composable("home") {
            val context = androidx.compose.ui.platform.LocalContext.current
            HomeScreen(
                onPlayAll = { chats ->
                    val intent = android.content.Intent(context, com.example.telegramnarrator.core.service.PlaybackService::class.java).apply {
                        action = com.example.telegramnarrator.core.service.PlaybackService.ACTION_PLAY_ALL
                        putExtra(com.example.telegramnarrator.core.service.PlaybackService.EXTRA_CHAT_IDS, chats.map { it.id }.toLongArray())
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                },
                onChatClick = { chatId ->
                    // For now, just play this single chat
                    val intent = android.content.Intent(context, com.example.telegramnarrator.core.service.PlaybackService::class.java).apply {
                        action = com.example.telegramnarrator.core.service.PlaybackService.ACTION_PLAY_ALL
                        putExtra(com.example.telegramnarrator.core.service.PlaybackService.EXTRA_CHAT_IDS, longArrayOf(chatId))
                    }
                     if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                }
            )
        }
    }
}
