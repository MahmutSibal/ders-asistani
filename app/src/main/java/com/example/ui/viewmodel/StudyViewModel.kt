package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.CourseEntity
import com.example.data.local.entity.QuizAttemptEntity
import com.example.data.local.entity.StudyNoteEntity
import com.example.data.model.ConnectionMode
import com.example.data.model.QuizQuestion
import com.example.data.model.ServerConfig
import com.example.data.remote.AssistantRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class StudyUiState(
    val selectedCourseId: String = "c_cs101",
    val serverConfig: ServerConfig = ServerConfig(),
    // Note creation/editing
    val noteTitleInput: String = "",
    val noteContentInput: String = "",
    val activeSummary: String? = null,
    val activeKeyPoints: List<String> = emptyList(),
    // Active Quiz
    val activeQuizQuestions: List<QuizQuestion> = emptyList(),
    val currentQuestionIndex: Int = 0,
    val userAnswers: Map<Int, String> = emptyMap(), // question index -> chosen option
    val isQuizFinished: Boolean = false,
    val latestQuizAttempt: QuizAttemptEntity? = null,
    val aiRecommendation: String? = null,
    // Loading flags
    val isSummarizing: Boolean = false,
    val isAskingChat: Boolean = false,
    val isGeneratingQuiz: Boolean = false,
    val isEvaluatingQuiz: Boolean = false,
    val isTestingConnection: Boolean = false,
    val connectionTestResult: String? = null,
    val statusMessage: String? = null
)

class StudyViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val courseDao = database.courseDao()
    private val noteDao = database.studyNoteDao()
    private val chatDao = database.chatMessageDao()
    private val quizDao = database.quizAttemptDao()
    private val repository = AssistantRepository()

    private val _uiState = MutableStateFlow(StudyUiState())
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

    val courses: StateFlow<List<CourseEntity>> = courseDao.getAllCourses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allNotes: StateFlow<List<StudyNoteEntity>> = noteDao.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allQuizAttempts: StateFlow<List<QuizAttemptEntity>> = quizDao.getAllAttempts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val averageScore: StateFlow<Double?> = quizDao.getAverageScore()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val totalQuizCount: StateFlow<Int> = quizDao.getTotalQuizCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _chatMessages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessageEntity>> = _chatMessages.asStateFlow()

    init {
        // Ensure initial courses exist in Room
        viewModelScope.launch {
            if (courseDao.getCount() == 0) {
                AppDatabase.populateInitialCourses(courseDao, noteDao)
            }
            loadChatForCourse(_uiState.value.selectedCourseId)
        }
    }

    fun selectCourse(courseId: String) {
        _uiState.update { it.copy(selectedCourseId = courseId) }
        loadChatForCourse(courseId)
    }

    private fun loadChatForCourse(courseId: String) {
        viewModelScope.launch {
            chatDao.getMessagesByCourse(courseId).collect { msgs ->
                _chatMessages.value = msgs
            }
        }
    }

    // Config updates
    fun updateServerConfig(config: ServerConfig) {
        _uiState.update { it.copy(serverConfig = config) }
    }

    fun testConnection() {
        val config = _uiState.value.serverConfig
        val targetUrl = if (config.mode == ConnectionMode.N8N_WEBHOOK) {
            config.n8nBaseUrl
        } else {
            config.ollamaBaseUrl
        }

        _uiState.update { it.copy(isTestingConnection = true, connectionTestResult = null) }
        viewModelScope.launch {
            val result = repository.testConnection(targetUrl)
            _uiState.update {
                it.copy(
                    isTestingConnection = false,
                    connectionTestResult = if (result.first) "✅ ${result.second}" else "❌ ${result.second}"
                )
            }
        }
    }

    // Chat actions
    fun sendChatMessage(question: String) {
        if (question.isBlank()) return
        val currentCourseId = _uiState.value.selectedCourseId
        val currentCourseName = courses.value.find { it.id == currentCourseId }?.name ?: "Genel"

        viewModelScope.launch {
            val userMsg = ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                courseId = currentCourseId,
                isUser = true,
                content = question.trim(),
                timestamp = System.currentTimeMillis()
            )
            chatDao.insertMessage(userMsg)

            _uiState.update { it.copy(isAskingChat = true) }

            val result = repository.askQuestion(
                question = question,
                courseName = currentCourseName,
                contextText = null,
                config = _uiState.value.serverConfig
            )

            val answerText = result.getOrElse { "Hata: ${it.localizedMessage}" }
            val botMsg = ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                courseId = currentCourseId,
                isUser = false,
                content = answerText,
                timestamp = System.currentTimeMillis(),
                modelName = _uiState.value.serverConfig.selectedModel
            )
            chatDao.insertMessage(botMsg)
            _uiState.update { it.copy(isAskingChat = false) }
        }
    }

    fun clearChat() {
        val courseId = _uiState.value.selectedCourseId
        viewModelScope.launch {
            chatDao.clearMessagesForCourse(courseId)
        }
    }

    // Notes actions
    fun onNoteTitleChange(title: String) {
        _uiState.update { it.copy(noteTitleInput = title) }
    }

    fun onNoteContentChange(content: String) {
        _uiState.update { it.copy(noteContentInput = content) }
    }

    fun loadTemplateNote(type: String) {
        val (title, content, courseId) = when (type) {
            "os" -> Triple(
                "İşletim Sistemleri: Process Yönetimi ve Deadlock",
                """Process (Süreç), bellekte yürütülen bir program örneğidir. Her process kendi adres uzayına, sayaçlarına ve kaynaklarına sahiptir.

Coffman Deadlock (Ölümcül Kilitlenme) Koşulları:
1. Mutual Exclusion (Karşılıklı Dışlama): Kaynağın aynı anda tek bir process tarafından kullanımı.
2. Hold and Wait (Tut ve Bekle): Kaynağı tutarken diğerini bekleme durumu.
3. No Preemption (Zorla Kaynak Almama): Kaynak ancak kullanan process bırakınca serbest kalır.
4. Circular Wait (Döngüsel Bekleme): Dairesel bir bekleme zincirinin oluşması.

Banker's Algoritması, işletim sistemlerinde deadlock'tan kaçınma (avoidance) için kullanılır ve sistemin daima güvenli durumda (safe state) kalmasını sağlar.""".trimIndent(),
                "c_cs202"
            )
            "algorithms" -> Triple(
                "Algoritmalar: Big-O Notasyonu ve Sıralama",
                """Asimptotik karmaşıklık analizi, algoritmaların girdi büyüklüğü (n) arttıkça zaman ve bellek kullanımını inceler.

Temel Karmaşıklık Sınıfları:
- O(1): Doğrudan erişim
- O(log n): İkili Arama (Binary Search)
- O(n): Doğrusal arama
- O(n log n): Merge Sort, Quick Sort (ortalama), Heap Sort
- O(n²): Bubble Sort, Insertion Sort

Böl ve Yönet (Divide and Conquer): Problemi bağımsız alt problemlere böler, çözer ve birleştirir. Merge Sort bu yaklaşımın en temel örneğidir.""".trimIndent(),
                "c_cs301"
            )
            else -> Triple(
                "Veri Yapıları: Yığın (Stack) ve Kuyruk (Queue)",
                """Yığın (Stack) LIFO (Last-In First-Out) ilkesiyle çalışır. Temel işlemler push (ekleme), pop (çıkarma) ve peek (en üsttekini görme)'dir. Fonksiyon çağrıları (call stack) ve parantez eşleştirme için kullanılır.

Kuyruk (Queue) FIFO (First-In First-Out) ilkesiyle çalışır. Temel işlemler enqueue ve dequeue'dur. İşletim sistemi CPU kuyrukları ve yazdırma havuzu (print spooler) tipik örnekleridir.""".trimIndent(),
                "c_cs101"
            )
        }
        _uiState.update {
            it.copy(
                noteTitleInput = title,
                noteContentInput = content,
                selectedCourseId = courseId,
                activeSummary = null,
                activeKeyPoints = emptyList()
            )
        }
    }

    fun summarizeCurrentNote() {
        val content = _uiState.value.noteContentInput.trim()
        if (content.isBlank()) return
        val courseName = courses.value.find { it.id == _uiState.value.selectedCourseId }?.name ?: "Genel"

        viewModelScope.launch {
            _uiState.update { it.copy(isSummarizing = true) }
            val result = repository.summarizeNotes(content, courseName, _uiState.value.serverConfig)
            result.onSuccess { (summary, keyPoints) ->
                _uiState.update {
                    it.copy(
                        isSummarizing = false,
                        activeSummary = summary,
                        activeKeyPoints = keyPoints
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isSummarizing = false) }
            }
        }
    }

    fun saveCurrentNote() {
        val title = _uiState.value.noteTitleInput.trim().ifBlank { "Başlıksız Not" }
        val content = _uiState.value.noteContentInput.trim()
        if (content.isBlank()) return

        val summary = _uiState.value.activeSummary ?: "Özet oluşturulmadı."
        val keyPoints = _uiState.value.activeKeyPoints.joinToString(",")

        viewModelScope.launch {
            val note = StudyNoteEntity(
                id = UUID.randomUUID().toString(),
                courseId = _uiState.value.selectedCourseId,
                title = title,
                content = content,
                summary = summary,
                keyPointsJson = keyPoints,
                createdAt = System.currentTimeMillis()
            )
            noteDao.insertNote(note)
            _uiState.update {
                it.copy(
                    statusMessage = "Ders notu ve özeti kaydedildi!"
                )
            }
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            noteDao.deleteNoteById(noteId)
        }
    }

    fun openExistingNote(note: StudyNoteEntity) {
        _uiState.update {
            it.copy(
                selectedCourseId = note.courseId,
                noteTitleInput = note.title,
                noteContentInput = note.content,
                activeSummary = note.summary,
                activeKeyPoints = if (note.keyPointsJson.isNotBlank()) note.keyPointsJson.split(",") else emptyList()
            )
        }
    }

    // Quiz actions
    fun generateQuizFromCurrentNote(onNavigateToQuiz: () -> Unit) {
        val content = _uiState.value.noteContentInput.ifBlank {
            // If empty, fallback to sample or course content
            "Veri Yapıları ve Temel Algoritmalar"
        }
        val courseName = courses.value.find { it.id == _uiState.value.selectedCourseId }?.name ?: "Genel"

        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingQuiz = true) }
            val result = repository.generateQuiz(content, courseName, 5, _uiState.value.serverConfig)
            result.onSuccess { questions ->
                _uiState.update {
                    it.copy(
                        isGeneratingQuiz = false,
                        activeQuizQuestions = questions,
                        currentQuestionIndex = 0,
                        userAnswers = emptyMap(),
                        isQuizFinished = false,
                        latestQuizAttempt = null,
                        aiRecommendation = null
                    )
                }
                onNavigateToQuiz()
            }.onFailure {
                _uiState.update { it.copy(isGeneratingQuiz = false) }
            }
        }
    }

    fun selectQuizAnswer(questionIndex: Int, selectedOption: String) {
        _uiState.update {
            it.copy(userAnswers = it.userAnswers + (questionIndex to selectedOption))
        }
    }

    fun nextQuizQuestion() {
        if (_uiState.value.currentQuestionIndex < _uiState.value.activeQuizQuestions.size - 1) {
            _uiState.update { it.copy(currentQuestionIndex = it.currentQuestionIndex + 1) }
        }
    }

    fun previousQuizQuestion() {
        if (_uiState.value.currentQuestionIndex > 0) {
            _uiState.update { it.copy(currentQuestionIndex = it.currentQuestionIndex - 1) }
        }
    }

    fun submitQuiz() {
        val questions = _uiState.value.activeQuizQuestions
        if (questions.isEmpty()) return

        var correct = 0
        var wrong = 0
        var empty = 0
        val wrongQuestions = mutableListOf<String>()

        questions.forEachIndexed { index, q ->
            val userChoice = _uiState.value.userAnswers[index]
            if (userChoice == null) {
                empty++
            } else if (userChoice.trim().equals(q.correctAnswer.trim(), ignoreCase = true)) {
                correct++
            } else {
                wrong++
                wrongQuestions.add(q.question)
            }
        }

        val total = questions.size
        val percentage = if (total > 0) (correct * 100) / total else 0
        val courseName = courses.value.find { it.id == _uiState.value.selectedCourseId }?.name ?: "Genel"
        val noteTitle = _uiState.value.noteTitleInput.ifBlank { "$courseName Testi" }

        viewModelScope.launch {
            _uiState.update { it.copy(isEvaluatingQuiz = true, isQuizFinished = true) }

            val evalResult = repository.evaluatePerformance(
                total = total,
                correct = correct,
                wrong = wrong,
                wrongQuestions = wrongQuestions,
                courseName = courseName,
                config = _uiState.value.serverConfig
            )

            val rec = evalResult.getOrElse { "Test tamamlandı." }

            val attempt = QuizAttemptEntity(
                id = UUID.randomUUID().toString(),
                courseId = _uiState.value.selectedCourseId,
                noteTitle = noteTitle,
                totalQuestions = total,
                correctCount = correct,
                wrongCount = wrong,
                emptyCount = empty,
                percentage = percentage,
                recommendations = rec,
                date = System.currentTimeMillis()
            )

            quizDao.insertAttempt(attempt)

            _uiState.update {
                it.copy(
                    isEvaluatingQuiz = false,
                    latestQuizAttempt = attempt,
                    aiRecommendation = rec
                )
            }
        }
    }

    fun restartQuiz() {
        _uiState.update {
            it.copy(
                currentQuestionIndex = 0,
                userAnswers = emptyMap(),
                isQuizFinished = false,
                latestQuizAttempt = null,
                aiRecommendation = null
            )
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}
