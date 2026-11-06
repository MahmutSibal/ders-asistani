package com.example.data.remote

import com.example.data.model.EvaluationWebhookRequest
import com.example.data.model.EvaluationWebhookResponse
import com.example.data.model.OllamaGenerateRequest
import com.example.data.model.OllamaGenerateResponse
import com.example.data.model.QaWebhookRequest
import com.example.data.model.QaWebhookResponse
import com.example.data.model.QuizWebhookRequest
import com.example.data.model.QuizWebhookResponse
import com.example.data.model.SummaryWebhookRequest
import com.example.data.model.SummaryWebhookResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

interface ApiService {

    // n8n Webhooks (can take dynamic or relative URLs)
    @POST
    suspend fun sendQaWebhook(
        @Url url: String,
        @Body request: QaWebhookRequest
    ): Response<QaWebhookResponse>

    @POST
    suspend fun sendSummaryWebhook(
        @Url url: String,
        @Body request: SummaryWebhookRequest
    ): Response<SummaryWebhookResponse>

    @POST
    suspend fun sendQuizWebhook(
        @Url url: String,
        @Body request: QuizWebhookRequest
    ): Response<QuizWebhookResponse>

    @POST
    suspend fun sendEvaluationWebhook(
        @Url url: String,
        @Body request: EvaluationWebhookRequest
    ): Response<EvaluationWebhookResponse>

    // Direct Ollama endpoints
    @POST
    suspend fun generateWithOllama(
        @Url url: String,
        @Body request: OllamaGenerateRequest
    ): Response<OllamaGenerateResponse>

    @GET
    suspend fun pingEndpoint(
        @Url url: String
    ): Response<ResponseBody>
}
