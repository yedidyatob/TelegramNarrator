package io.github.yedidyatob.telegramnarrator.ui.screens.auth

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.yedidyatob.telegramnarrator.R
import io.github.yedidyatob.telegramnarrator.domain.auth.CodeDelivery
import io.github.yedidyatob.telegramnarrator.domain.auth.CodeResend
import io.github.yedidyatob.telegramnarrator.domain.auth.ResendKind
import io.github.yedidyatob.telegramnarrator.domain.model.AuthState
import io.github.yedidyatob.telegramnarrator.domain.model.UnsupportedLoginStep
import io.github.yedidyatob.telegramnarrator.ui.components.PrivacyPolicyLink
import io.github.yedidyatob.telegramnarrator.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            onLoginSuccess()
        }
    }

    val authError by viewModel.error.collectAsStateWithLifecycle()
    val authErrorText = authError?.let { stringResource(it.messageRes()) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    // Edge-to-edge: keep the field and button above the keyboard, and scroll on small screens
                    .imePadding()
                    .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
            ) {
                when (val state = authState) {
                    is AuthState.Initializing -> {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.login_status_initializing), style = MaterialTheme.typography.bodyMedium)
                    }

                    is AuthState.Unauthenticated, is AuthState.WaitPhoneNumber -> {
                        PhoneNumberInput(
                            isLoading = isLoading,
                            error = authErrorText,
                            normalizer = viewModel.phoneNumbers,
                            countries = { viewModel.phoneCountries },
                            defaultRegion = viewModel.defaultPhoneRegion,
                            onEnter = viewModel::onPhoneNumberEntered,
                        )
                    }

                    is AuthState.WaitCode -> {
                        CodeInput(
                            state = state,
                            isLoading = isLoading,
                            error = authErrorText,
                            onEnter = viewModel::onCodeEntered,
                            onResend = viewModel::onResendCode,
                            onChangeNumber = viewModel::onChangePhoneNumber,
                        )
                    }

                    is AuthState.Unsupported -> {
                        Text(
                            stringResource(state.step.messageRes()),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(0.8f).semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = viewModel::onChangePhoneNumber) { Text(stringResource(R.string.login_btn_back)) }
                    }

                    is AuthState.WaitPassword -> {
                        PasswordInput(
                            isLoading = isLoading,
                            error = authErrorText,
                            onEnter = viewModel::onPasswordEntered,
                        )
                    }

                    is AuthState.Authenticated -> {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.login_status_redirecting), style = MaterialTheme.typography.bodyMedium)
                    }

                    is AuthState.Error -> {
                        Text(stringResource(R.string.login_error_title), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(state.reason.messageRes()),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = viewModel::onRetry) {
                            Text(stringResource(R.string.login_btn_retry))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Footer of the phone step ([PhoneNumberInput]), shown before the user enters their phone number: the app is
 * unofficial and built on the Telegram API (Telegram API Terms of Service 2.2 / 2.3), plus the in-app privacy
 * policy link Google Play requires.
 */
@Composable
internal fun UnofficialAppNotice() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(0.8f).padding(top = 24.dp),
    ) {
        Text(
            stringResource(R.string.login_unofficial_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        PrivacyPolicyLink()
    }
}

@Composable
fun CodeInput(
    state: AuthState.WaitCode,
    isLoading: Boolean,
    error: String?,
    onEnter: (String) -> Unit,
    onResend: () -> Unit,
    onChangeNumber: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val number = CodeResend.displayNumber(state.phoneNumber)
    val subtitle =
        listOfNotNull(
            number.takeIf { it.isNotBlank() }?.let { stringResource(R.string.login_code_sent_to, it) },
            stringResource(state.type.messageRes()),
        ).joinToString("\n")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LoginStep(
            title = stringResource(R.string.login_title_code),
            subtitle = subtitle,
            label = stringResource(R.string.login_label_code),
            buttonText = stringResource(R.string.login_btn_verify_code),
            text = text,
            onTextChange = { text = it },
            keyboardType = if (state.type.isText) KeyboardType.Text else KeyboardType.Number,
            isLoading = isLoading,
            error = error,
            onEnter = onEnter,
        )
        if (state.nextType != null) {
            // Counts down from codeInfo.timeout; a new code (resend) is a new state and restarts it
            var secondsLeft by remember(state) { mutableIntStateOf(state.timeoutSec) }
            LaunchedEffect(state) {
                val start = System.currentTimeMillis()
                while (secondsLeft > 0) {
                    delay(250)
                    secondsLeft = CodeResend.secondsLeft(state.timeoutSec, System.currentTimeMillis() - start)
                }
            }
            TextButton(onClick = onResend, enabled = !isLoading && secondsLeft == 0) {
                Text(
                    if (secondsLeft > 0) {
                        stringResource(R.string.login_code_resend_in, secondsLeft)
                    } else {
                        stringResource(CodeResend.kindOf(state.nextType).messageRes())
                    },
                )
            }
        }
        TextButton(onClick = onChangeNumber, enabled = !isLoading) { Text(stringResource(R.string.login_code_wrong_number)) }
    }
}

private fun CodeDelivery.messageRes(): Int =
    when (this) {
        CodeDelivery.TELEGRAM_APP -> R.string.login_code_via_app
        CodeDelivery.SMS -> R.string.login_code_via_sms
        CodeDelivery.SMS_WORD -> R.string.login_code_via_sms_word
        CodeDelivery.SMS_PHRASE -> R.string.login_code_via_sms_phrase
        CodeDelivery.CALL -> R.string.login_code_via_call
        CodeDelivery.MISSED_CALL -> R.string.login_code_via_missed_call
        CodeDelivery.FRAGMENT -> R.string.login_code_via_fragment
        CodeDelivery.FIREBASE -> R.string.login_code_via_firebase
        CodeDelivery.UNKNOWN -> R.string.login_code_via_unknown
    }

private fun ResendKind.messageRes(): Int =
    when (this) {
        ResendKind.SMS -> R.string.login_code_resend_sms
        ResendKind.CALL -> R.string.login_code_resend_call
        ResendKind.AGAIN -> R.string.login_code_resend
    }

private fun UnsupportedLoginStep.messageRes(): Int =
    when (this) {
        UnsupportedLoginStep.EMAIL -> R.string.login_unsupported_email
        UnsupportedLoginStep.REGISTRATION -> R.string.login_unsupported_registration
        UnsupportedLoginStep.OTHER_DEVICE -> R.string.login_unsupported_other_device
    }

@Composable
fun PasswordInput(
    isLoading: Boolean,
    error: String?,
    onEnter: (String) -> Unit,
) {
    // Not rememberSaveable on purpose: the password must not end up in the saved instance state
    var text by remember { mutableStateOf("") }
    LoginStep(
        title = stringResource(R.string.login_title_password),
        subtitle = stringResource(R.string.login_subtitle_password),
        label = stringResource(R.string.login_label_password),
        buttonText = stringResource(R.string.login_btn_unlock),
        text = text,
        onTextChange = { text = it },
        keyboardType = KeyboardType.Password,
        isPassword = true,
        isLoading = isLoading,
        error = error,
        onEnter = onEnter,
    )
}

/** One step of the login: headline, subtitle, a single input, an inline error and the action button. */
@Composable
private fun LoginStep(
    title: String,
    subtitle: String,
    label: String,
    buttonText: String,
    text: String,
    onTextChange: (String) -> Unit,
    keyboardType: KeyboardType,
    isLoading: Boolean,
    error: String?,
    onEnter: (String) -> Unit,
    isPassword: Boolean = false,
) {
    val submit = { if (!isLoading && text.isNotBlank()) onEnter(text) }
    LoginStepFrame(
        title = title,
        subtitle = subtitle,
        buttonText = buttonText,
        canSubmit = text.isNotBlank(),
        isLoading = isLoading,
        error = error,
        onSubmit = submit,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(0.8f),
            enabled = !isLoading,
            isError = error != null,
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
    }
}

/**
 * Layout shared by the login steps: headline, subtitle, [input], the server error, the action button and an
 * optional [footer] (the phone step's unofficial-app notice).
 */
@Composable
internal fun LoginStepFrame(
    title: String,
    subtitle: String,
    buttonText: String,
    canSubmit: Boolean,
    isLoading: Boolean,
    error: String?,
    onSubmit: () -> Unit,
    footer: @Composable () -> Unit = {},
    input: @Composable () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Centered: the app name ("Narri") can wrap to two lines
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(32.dp))
        input()
        if (error != null) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                // TalkBack announces the error when it appears
                modifier =
                    Modifier
                        .padding(top = 8.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onSubmit,
            enabled = !isLoading && canSubmit,
            modifier = Modifier.fillMaxWidth(0.8f).height(50.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(buttonText)
            }
        }
        footer()
    }
}
