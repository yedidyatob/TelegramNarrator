package com.example.telegramnarrator.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.example.telegramnarrator.R

/**
 * Returns a wrapper around [onPlay] that, on Android 13+, first explains why the notification permission
 * is useful (it holds the playback controls) and asks for it - once - the first time the user presses
 * Play. Playback starts afterwards whether or not the permission was granted: the foreground service
 * works without it, only the notification is hidden.
 */
@Composable
fun <T> rememberNotificationPermissionGate(onPlay: (T) -> Unit): (T) -> Unit {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showRationale by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pending?.invoke()
        pending = null
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = {
                // Dismissed without choosing: just play
                showRationale = false
                pending?.invoke()
                pending = null
            },
            title = { Text(stringResource(R.string.permission_notifications_title)) },
            text = { Text(stringResource(R.string.permission_notifications_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text(stringResource(R.string.permission_notifications_allow)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRationale = false
                    pending?.invoke()
                    pending = null
                }) { Text(stringResource(R.string.permission_notifications_skip)) }
            }
        )
    }

    return { value ->
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission && !asked) {
            asked = true
            pending = { onPlay(value) }
            showRationale = true
        } else {
            onPlay(value)
        }
    }
}
