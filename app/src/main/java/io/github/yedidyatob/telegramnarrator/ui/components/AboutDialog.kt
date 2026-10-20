package io.github.yedidyatob.telegramnarrator.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.yedidyatob.telegramnarrator.BuildConfig
import io.github.yedidyatob.telegramnarrator.R
import io.github.yedidyatob.telegramnarrator.domain.about.AppVersionInfo

/** Main menu → About (#87): which build is installed, the privacy policy, and Share debug log. */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val info = AppVersionInfo(
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        buildType = BuildConfig.BUILD_TYPE,
        ciRunNumber = BuildConfig.CI_RUN_NUMBER,
        gitSha = BuildConfig.GIT_SHA
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_name)) },
        text = {
            Column {
                Text(stringResource(R.string.about_version, info.version), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.about_build_type, info.buildType), style = MaterialTheme.typography.bodyMedium)
                info.ciBuild?.let { Text(stringResource(R.string.about_ci_build, it), style = MaterialTheme.typography.bodyMedium) }
                Text(stringResource(R.string.settings_about_unofficial), style = MaterialTheme.typography.bodySmall)
                PrivacyPolicyLink()
                ShareDebugLogButton()
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.about_close)) } }
    )
}
