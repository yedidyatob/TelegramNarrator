package io.github.yedidyatob.telegramnarrator.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.yedidyatob.telegramnarrator.BuildConfig
import io.github.yedidyatob.telegramnarrator.R
import io.github.yedidyatob.telegramnarrator.domain.feedback.FeedbackEmail

/** About: opens the email app with a feedback email (app / device facts only, no chat data). */
@Composable
fun SendFeedbackButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    TextButton(onClick = { sendFeedback(context) }, modifier = modifier) {
        Text(stringResource(R.string.feedback_send))
    }
}

private fun sendFeedback(context: Context) {
    val subject = context.getString(R.string.feedback_subject)
    val body = FeedbackEmail.body(
        context.getString(R.string.feedback_body_prompt),
        FeedbackEmail.Info(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            buildType = BuildConfig.BUILD_TYPE,
            ciRun = BuildConfig.CI_RUN_NUMBER,
            gitSha = BuildConfig.GIT_SHA,
            androidRelease = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL
        )
    )
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(FeedbackEmail.mailto(subject, body) { Uri.encode(it) }))
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(FeedbackEmail.ADDRESS))
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, body)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.feedback_no_email_app, Toast.LENGTH_SHORT).show()
    }
}
