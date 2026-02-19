package com.example.telegramnarrator.domain.model

sealed class AuthState {
    object Unauthenticated : AuthState()
    object WaitPhoneNumber : AuthState()
    object WaitCode : AuthState()
    object WaitPassword : AuthState()
    object Authenticated : AuthState()
    data class Error(val message: String) : AuthState()
}
