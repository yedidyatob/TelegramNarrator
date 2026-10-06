package io.github.yedidyatob.telegramnarrator.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import io.github.yedidyatob.telegramnarrator.domain.model.AuthState
import io.github.yedidyatob.telegramnarrator.ui.viewmodel.AuthViewModel
import io.github.yedidyatob.telegramnarrator.R

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            onLoginSuccess()
        }
    }

    val authError by viewModel.error.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
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
                            error = authError,
                            onEnter = viewModel::onPhoneNumberEntered
                        )
                    }
                    is AuthState.WaitCode -> {
                        CodeInput(
                            isLoading = isLoading,
                            error = authError,
                            onEnter = viewModel::onCodeEntered
                        )
                    }
                    is AuthState.WaitPassword -> {
                        PasswordInput(
                            isLoading = isLoading,
                            error = authError,
                            onEnter = viewModel::onPasswordEntered
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
                            state.message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
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

@Composable
fun PhoneNumberInput(isLoading: Boolean, error: String?, onEnter: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    LoginStep(
        title = stringResource(R.string.app_name),
        subtitle = stringResource(R.string.login_title_phone),
        label = stringResource(R.string.login_label_phone),
        buttonText = stringResource(R.string.login_btn_send_code),
        text = text,
        onTextChange = { text = it },
        keyboardType = KeyboardType.Phone,
        isLoading = isLoading,
        error = error,
        onEnter = onEnter
    )
}

@Composable
fun CodeInput(isLoading: Boolean, error: String?, onEnter: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    LoginStep(
        title = stringResource(R.string.login_title_code),
        subtitle = stringResource(R.string.login_subtitle_code),
        label = stringResource(R.string.login_label_code),
        buttonText = stringResource(R.string.login_btn_verify_code),
        text = text,
        onTextChange = { text = it },
        keyboardType = KeyboardType.Number,
        isLoading = isLoading,
        error = error,
        onEnter = onEnter
    )
}

@Composable
fun PasswordInput(isLoading: Boolean, error: String?, onEnter: (String) -> Unit) {
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
        onEnter = onEnter
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
    isPassword: Boolean = false
) {
    val submit = { if (!isLoading && text.isNotBlank()) onEnter(text) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(32.dp))
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
            keyboardActions = KeyboardActions(onDone = { submit() })
        )
        if (error != null) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                // TalkBack announces the error when it appears
                modifier = Modifier
                    .padding(top = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { submit() },
            enabled = !isLoading && text.isNotBlank(),
            modifier = Modifier.fillMaxWidth(0.8f).height(50.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(buttonText)
            }
        }
    }
}
