package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class UserProfile(
    val email: String = "mahmutsibal9@gmail.com",
    val fullName: String = "Mahmut Şibal",
    val studentId: String = "20210801045",
    val department: String = "Bilgisayar Mühendisliği",
    val semester: String = "5. Yarıyıl (3. Sınıf)"
)

data class AuthUiState(
    val isLoggedIn: Boolean = false,
    val emailInput: String = "mahmutsibal9@gmail.com",
    val passwordInput: String = "••••••••",
    val emailError: String? = null,
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val currentUser: UserProfile? = null
)

class AuthViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(emailInput = email, emailError = null) }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(passwordInput = password, passwordError = null) }
    }

    fun login() {
        val email = _uiState.value.emailInput.trim()
        val password = _uiState.value.passwordInput.trim()

        var hasError = false
        var emailErr: String? = null
        var passErr: String? = null

        if (email.isBlank() || !email.contains("@")) {
            emailErr = "Lütfen geçerli bir öğrenci e-posta adresi girin"
            hasError = true
        }
        if (password.length < 4) {
            passErr = "Şifre en az 4 karakter olmalıdır"
            hasError = true
        }

        if (hasError) {
            _uiState.update { it.copy(emailError = emailErr, passwordError = passErr) }
            return
        }

        _uiState.update {
            it.copy(
                isLoggedIn = true,
                currentUser = UserProfile(
                    email = email,
                    fullName = if (email.startsWith("mahmut", ignoreCase = true)) "Mahmut Şibal" else "Öğrenci Kullanıcı"
                )
            )
        }
    }

    fun quickDemoLogin() {
        _uiState.update {
            it.copy(
                emailInput = "mahmutsibal9@gmail.com",
                passwordInput = "123456",
                isLoggedIn = true,
                currentUser = UserProfile()
            )
        }
    }

    fun logout() {
        _uiState.update {
            it.copy(
                isLoggedIn = false,
                currentUser = null,
                passwordInput = ""
            )
        }
    }
}
