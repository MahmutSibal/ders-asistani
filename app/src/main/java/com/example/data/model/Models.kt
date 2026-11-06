package com.example.data.model

data class QuizQuestion(
    val id: String = java.util.UUID.randomUUID().toString(),
    val question: String,
    val options: List<String>,
    val correctAnswer: String,
    val explanation: String = "",
    val userSelectedOption: String? = null
)

data class QuizData(
    val questions: List<QuizQuestion> = emptyList()
)

enum class ConnectionMode(val label: String, val description: String) {
    N8N_WEBHOOK("n8n Webhook (Docker)", "İstekler n8n konteynerine (port 5678) gider, iş akışları Ollama'yı yönetir"),
    OLLAMA_DIRECT("Doğrudan Ollama", "İstekler doğrudan yerel Ollama API'sine (port 11434) gönderilir"),
    BUILTIN_AI("Yerleşik Akıllı Motor", "Docker bağlantısı olmadan da kesintisiz çalışan akıllı üniversite asistanı")
}

data class ServerConfig(
    val n8nBaseUrl: String = "http://10.0.2.2:5678",
    val ollamaBaseUrl: String = "http://10.0.2.2:11434",
    val selectedModel: String = "qwen2.5:3b",
    val mode: ConnectionMode = ConnectionMode.N8N_WEBHOOK
)

// API Request/Response models for n8n Webhooks
data class QaWebhookRequest(
    val question: String,
    val course: String,
    val context: String? = null,
    val model: String = "qwen2.5:3b"
)

data class QaWebhookResponse(
    val answer: String?,
    val response: String?,
    val text: String?,
    val model: String?
) {
    fun getEffectiveAnswer(): String {
        return answer ?: response ?: text ?: "Yanıt alınamadı."
    }
}

data class SummaryWebhookRequest(
    val content: String,
    val course: String,
    val model: String = "qwen2.5:3b"
)

data class SummaryWebhookResponse(
    val summary: String?,
    val key_points: List<String>?,
    val keyPoints: List<String>?,
    val response: String?
) {
    fun getEffectiveSummary(): String {
        return summary ?: response ?: "Özet oluşturulamadı."
    }
    fun getEffectiveKeyPoints(): List<String> {
        return key_points ?: keyPoints ?: emptyList()
    }
}

data class QuizWebhookRequest(
    val content: String,
    val course: String,
    val count: Int = 5,
    val model: String = "qwen2.5:3b"
)

data class QuizItemDto(
    val question: String,
    val options: List<String>,
    val correct_answer: String?,
    val correctAnswer: String?,
    val explanation: String?
)

data class QuizWebhookResponse(
    val questions: List<QuizItemDto>? = null
)

data class EvaluationWebhookRequest(
    val total: Int,
    val correct: Int,
    val wrong: Int,
    val course: String,
    val wrongQuestions: List<String> = emptyList(),
    val model: String = "qwen2.5:3b"
)

data class EvaluationWebhookResponse(
    val recommendations: String?,
    val weak_topics: List<String>?,
    val weakTopics: List<String>?,
    val study_plan: List<String>?,
    val studyPlan: List<String>?
) {
    fun getEffectiveRecommendations(): String {
        return recommendations ?: "Belirlenen konularda soru çözümünü artırın ve ders notlarını tekrar gözden geçirin."
    }
}

// Ollama native API models
data class OllamaGenerateRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = false,
    val format: String? = null
)

data class OllamaGenerateResponse(
    val model: String?,
    val response: String?,
    val done: Boolean?
)
