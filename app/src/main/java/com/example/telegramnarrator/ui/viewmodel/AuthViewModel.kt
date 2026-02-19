package com.example.telegramnarrator.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.telegramnarrator.domain.model.AuthState
import com.example.telegramnarrator.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuthState.Unauthenticated)

    fun onPhoneNumberEntered(phoneNumber: String) {
        viewModelScope.launch {
            authRepository.setPhoneNumber(phoneNumber)
        }
    }

    fun onCodeEntered(code: String) {
        viewModelScope.launch {
            authRepository.checkAuthenticationCode(code)
        }
    }

    fun onPasswordEntered(password: String) {
        viewModelScope.launch {
            authRepository.checkAuthenticationPassword(password)
        }
    }
}
