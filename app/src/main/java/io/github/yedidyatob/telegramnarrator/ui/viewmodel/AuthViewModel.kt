package io.github.yedidyatob.telegramnarrator.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.yedidyatob.telegramnarrator.data.auth.DeviceCountrySignals
import io.github.yedidyatob.telegramnarrator.domain.auth.PhoneCountries
import io.github.yedidyatob.telegramnarrator.domain.auth.PhoneCountry
import io.github.yedidyatob.telegramnarrator.domain.auth.PhoneCountryResolver
import io.github.yedidyatob.telegramnarrator.domain.auth.PhoneNumberNormalizer
import io.github.yedidyatob.telegramnarrator.domain.model.AuthState
import io.github.yedidyatob.telegramnarrator.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    deviceCountrySignals: DeviceCountrySignals
) : ViewModel() {

    /** Parses / validates the login phone field (libphonenumber). */
    val phoneNumbers = PhoneNumberNormalizer()

    /** Country preselected in the phone field: SIM, then network, then locale region; null = user picks. */
    val defaultPhoneRegion: String? =
        PhoneCountryResolver.resolve(deviceCountrySignals.read(), phoneNumbers::isSupportedRegion)

    /** Countries for the picker, built on first use (names in the device language). */
    val phoneCountries: List<PhoneCountry> by lazy { PhoneCountries.all(phoneNumbers) }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val authState: StateFlow<AuthState> = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuthState.Initializing)

    fun onRetry() {
        viewModelScope.launch { authRepository.retryInitialization() }
    }

    /** [phoneNumber] is already normalized to E.164 by the phone field ([phoneNumbers]). */
    fun onPhoneNumberEntered(phoneNumber: String) {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                authRepository.setPhoneNumber(phoneNumber)
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onCodeEntered(code: String) {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                authRepository.checkAuthenticationCode(code)
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun onPasswordEntered(password: String) {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                authRepository.checkAuthenticationPassword(password)
            } catch (e: Exception) {
                _error.value = e.message ?: "Unknown error"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
