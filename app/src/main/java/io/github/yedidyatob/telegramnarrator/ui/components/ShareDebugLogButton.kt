package io.github.yedidyatob.telegramnarrator.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import io.github.yedidyatob.telegramnarrator.R
import io.github.yedidyatob.telegramnarrator.core.diagnostics.AutoLog

/**
 * Settings → Share debug log: the Android Auto diagnostic log (IDs, counts, states and stack traces only; no
 * message text, chat titles or phone numbers) through the share sheet, for testing in a car without a PC.
 */
@Composable
fun ShareDebugLogButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    TextButton(onClick = { shareDebugLog(context) }, modifier = modifier) {
        Text(stringResource(R.string.settings_share_debug_log))
    }
}

private fun shareDebugLog(context: Context) {
    val file = AutoLog.file(context)
    if (!file.exists() || file.length() == 0L) {
        Toast.makeText(context, R.string.settings_debug_log_empty, Toast.LENGTH_SHORT).show()
        return
    }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".diagnostics", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.settings_debug_log_subject))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(send, context.getString(R.string.settings_share_debug_log)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.link_open_failed, Toast.LENGTH_SHORT).show()
    }
}
