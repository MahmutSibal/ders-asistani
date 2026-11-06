package com.example

import com.example.data.model.ConnectionMode
import com.example.data.model.QuizQuestion
import com.example.data.model.ServerConfig
import com.example.ui.viewmodel.AuthViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `auth viewmodel demo login populates user profile`() {
        val authVm = AuthViewModel()
        assertFalse(authVm.uiState.value.isLoggedIn)

        authVm.quickDemoLogin()

        assertTrue(authVm.uiState.value.isLoggedIn)
        assertEquals("mahmutsibal9@gmail.com", authVm.uiState.value.currentUser?.email)
        assertEquals("Mahmut Şibal", authVm.uiState.value.currentUser?.fullName)
    }

    @Test
    fun `auth viewmodel validates invalid email`() {
        val authVm = AuthViewModel()
        authVm.onEmailChange("invalid-email")
        authVm.onPasswordChange("123")
        authVm.login()

        assertFalse(authVm.uiState.value.isLoggedIn)
        assertNotNull(authVm.uiState.value.emailError)
    }

    @Test
    fun `quiz question schema structure matches case study requirements`() {
        val sampleQuestion = QuizQuestion(
            question = "Flutter hangi programlama dilini kullanır?",
            options = listOf("Java", "Dart", "Python", "C#"),
            correctAnswer = "Dart",
            explanation = "Flutter, Dart dilini kullanır."
        )

        assertEquals(4, sampleQuestion.options.size)
        assertTrue(sampleQuestion.options.contains(sampleQuestion.correctAnswer))
        assertEquals("Dart", sampleQuestion.correctAnswer)
    }

    @Test
    fun `server config defaults point to local Docker endpoints`() {
        val config = ServerConfig()
        assertEquals("http://10.0.2.2:5678", config.n8nBaseUrl)
        assertEquals("http://10.0.2.2:11434", config.ollamaBaseUrl)
        assertEquals("qwen2.5:3b", config.selectedModel)
        assertEquals(ConnectionMode.N8N_WEBHOOK, config.mode)
    }
}
