package com.example.telegramnarrator.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.example.telegramnarrator.domain.model.AuthState
import com.example.telegramnarrator.ui.viewmodel.AuthViewModel
import com.example.telegramnarrator.R

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

    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            onLoginSuccess()
        }
    }

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
                        Text("System Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { /* Could add a retry logic here by resetting auth state if needed */ }) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PhoneNumberInput(isLoading: Boolean, error: String?, onEnter: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.login_title_phone), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.login_label_phone)) },
            modifier = Modifier.fillMaxWidth(0.8f),
            enabled = !isLoading,
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
        )
        if (error != null) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onEnter(text) },
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
                Text(stringResource(R.string.login_btn_send_code))
            }
        }
    }
}

@Composable
fun CodeInput(isLoading: Boolean, error: String?, onEnter: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.login_title_code), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.login_title_code), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.login_label_code)) },
            modifier = Modifier.fillMaxWidth(0.8f),
            enabled = !isLoading,
            isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        if (error != null) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onEnter(text) },
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
                Text(stringResource(R.string.login_btn_verify_code))
            }
        }
    }
}

@Composable
fun PasswordInput(isLoading: Boolean, error: String?, onEnter: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.login_title_password), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.login_title_password), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.login_label_password)) },
            modifier = Modifier.fillMaxWidth(0.8f),
            enabled = !isLoading,
            isError = error != null,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        if (error != null) {
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onEnter(text) },
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
                Text(stringResource(R.string.login_btn_unlock))
            }
        }
    }
}
