package io.github.yedidyatob.telegramnarrator.ui.components

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.yedidyatob.telegramnarrator.R
import io.github.yedidyatob.telegramnarrator.domain.gemini.GeminiTts

/**
 * Gemini API key text field (masked, with paste and show/hide), shared by onboarding and Voice settings.
 * Callers keep [value] in plain `remember` state: the key must not end up in the saved instance state.
 */
@Composable
fun GeminiApiKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onDone: (() -> Unit)? = null,
) {
    var visible by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.settings_gemini_api_key)) },
        placeholder = { Text(stringResource(R.string.settings_gemini_api_key_hint)) },
        visualTransformation = if (visible) VisualTransformation.None else ApiKeyVisualTransformation,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                autoCorrect = false,
                imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default,
            ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        trailingIcon = {
            Row {
                if (value.isBlank() && enabled) {
                    IconButton(onClick = { clipboardManager.getText()?.text?.let(onValueChange) }) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = stringResource(R.string.settings_gemini_paste_key))
                    }
                }
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription =
                            stringResource(if (visible) R.string.settings_gemini_hide_key else R.string.settings_gemini_show_key),
                    )
                }
            }
        },
        singleLine = true,
        enabled = enabled,
    )
}

/** Opens Google AI Studio's API key page in the browser; a toast when no browser can open it. */
@Composable
fun GetGeminiKeyButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val opensInBrowser = stringResource(R.string.settings_gemini_get_key_a11y)
    TextButton(
        onClick = {
            try {
                uriHandler.openUri(GeminiTts.API_KEYS_URL)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, R.string.settings_gemini_get_key_no_browser, Toast.LENGTH_SHORT).show()
            } catch (e: IllegalArgumentException) {
                Toast.makeText(context, R.string.settings_gemini_get_key_no_browser, Toast.LENGTH_SHORT).show()
            }
        },
        modifier = modifier.heightIn(min = 48.dp).semantics { contentDescription = opensInBrowser },
    ) {
        Text(stringResource(R.string.settings_gemini_get_key))
        Spacer(Modifier.size(4.dp))
        // Decorative: the button's content description already says it opens the browser
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}

private object ApiKeyVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(AnnotatedString("•".repeat(text.text.length)), OffsetMapping.Identity)
}
