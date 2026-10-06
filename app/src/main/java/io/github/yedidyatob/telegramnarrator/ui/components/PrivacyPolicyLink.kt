package io.github.yedidyatob.telegramnarrator.ui.components

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import io.github.yedidyatob.telegramnarrator.R

/**
 * Opens the hosted privacy policy (docs/privacy-policy.md) in the browser. Google Play requires the policy
 * to be reachable from inside the app as well as from the store listing.
 */
@Composable
fun PrivacyPolicyLink(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val url = stringResource(R.string.privacy_policy_url)
    TextButton(
        onClick = {
            try {
                uriHandler.openUri(url)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, R.string.link_open_failed, Toast.LENGTH_SHORT).show()
            } catch (e: IllegalArgumentException) {
                Toast.makeText(context, R.string.link_open_failed, Toast.LENGTH_SHORT).show()
            }
        },
        modifier = modifier
    ) {
        Text(stringResource(R.string.login_privacy_policy))
    }
}
