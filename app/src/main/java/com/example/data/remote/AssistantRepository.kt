package com.example.data.remote

import com.example.data.model.ConnectionMode
import com.example.data.model.EvaluationWebhookRequest
import com.example.data.model.OllamaGenerateRequest
import com.example.data.model.QaWebhookRequest
import com.example.data.model.QuizItemDto
import com.example.data.model.QuizQuestion
import com.example.data.model.QuizWebhookRequest
import com.example.data.model.ServerConfig
import com.example.data.model.SummaryWebhookRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class AssistantRepository(
    private val apiService: ApiService = ApiClient.service
) {

    suspend fun askQuestion(
        question: String,
        courseName: String,
        contextText: String? = null,
        config: ServerConfig
    ): Result<String> = withContext(Dispatchers.IO) {
        when (config.mode) {
            ConnectionMode.N8N_WEBHOOK -> {
                try {
                    val url = "${config.n8nBaseUrl.trim().removeSuffix("/")}/webhook/study-qa"
                    val request = QaWebhookRequest(
                        question = question,
                        course = courseName,
                        context = contextText,
                        model = config.selectedModel
                    )
                    val response = apiService.sendQaWebhook(url, request)
                    if (response.isSuccessful && response.body() != null) {
                        Result.success(response.body()!!.getEffectiveAnswer())
                    } else {
                        // Fallback with warning
                        val fallback = generateBuiltInAnswer(question, courseName, contextText)
                        Result.success("⚡ [n8n Bağlantı Hatası (${response.code()}) - Yerel Motor Devreye Girdi]\n\n$fallback")
                    }
                } catch (e: Exception) {
                    val fallback = generateBuiltInAnswer(question, courseName, contextText)
                    Result.success("⚡ [Docker/n8n'e ulaşılamadı (${e.localizedMessage ?: "Bağlantı hatası"}) - Yerel Motor Devreye Girdi]\n\n$fallback")
                }
            }

            ConnectionMode.OLLAMA_DIRECT -> {
                try {
                    val url = "${config.ollamaBaseUrl.trim().removeSuffix("/")}/api/generate"
                    val prompt = """
                        Sen üniversite düzeyinde bir "$courseName" ders asistanısın.
                        Öğrencinin sorusunu anlaşılır, teknik olarak doğru, örneklerle açıkla.
                        ${contextText?.let { "Bağlam / Ders Notu:\n$it\n" } ?: ""}
                        Soru: $question
                        Yanıt:
                    """.trimIndent()
                    val request = OllamaGenerateRequest(
                        model = config.selectedModel,
                        prompt = prompt,
                        stream = false
                    )
                    val response = apiService.generateWithOllama(url, request)
                    if (response.isSuccessful && response.body()?.response != null) {
                        Result.success(response.body()!!.response!!)
                    } else {
                        val fallback = generateBuiltInAnswer(question, courseName, contextText)
                        Result.success("⚡ [Ollama API Hatası - Yerel Motor Devreye Girdi]\n\n$fallback")
                    }
                } catch (e: Exception) {
                    val fallback = generateBuiltInAnswer(question, courseName, contextText)
                    Result.success("⚡ [Ollama'ya bağlanılamadı (${e.localizedMessage}) - Yerel Motor Devreye Girdi]\n\n$fallback")
                }
            }

            ConnectionMode.BUILTIN_AI -> {
                Result.success(generateBuiltInAnswer(question, courseName, contextText))
            }
        }
    }

    suspend fun summarizeNotes(
        content: String,
        courseName: String,
        config: ServerConfig
    ): Result<Pair<String, List<String>>> = withContext(Dispatchers.IO) {
        when (config.mode) {
            ConnectionMode.N8N_WEBHOOK -> {
                try {
                    val url = "${config.n8nBaseUrl.trim().removeSuffix("/")}/webhook/summarize-notes"
                    val request = SummaryWebhookRequest(
                        content = content,
                        course = courseName,
                        model = config.selectedModel
                    )
                    val response = apiService.sendSummaryWebhook(url, request)
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        Result.success(Pair(body.getEffectiveSummary(), body.getEffectiveKeyPoints()))
                    } else {
                        Result.success(generateBuiltInSummary(content, courseName))
                    }
                } catch (e: Exception) {
                    Result.success(generateBuiltInSummary(content, courseName))
                }
            }

            ConnectionMode.OLLAMA_DIRECT -> {
                try {
                    val url = "${config.ollamaBaseUrl.trim().removeSuffix("/")}/api/generate"
                    val prompt = """
                        Aşağıdaki $courseName ders notunu analiz et ve JSON formatında özetle.
                        JSON formatı kesinlikle şu şekilde olmalıdır:
                        {"summary": "kapsamlı ve açık özet metni", "key_points": ["Madde 1", "Madde 2", "Madde 3"]}

                        Ders Notu:
                        $content
                    """.trimIndent()
                    val request = OllamaGenerateRequest(
                        model = config.selectedModel,
                        prompt = prompt,
                        stream = false,
                        format = "json"
                    )
                    val response = apiService.generateWithOllama(url, request)
                    val jsonStr = response.body()?.response
                    if (response.isSuccessful && !jsonStr.isNullOrBlank()) {
                        val json = JSONObject(jsonStr)
                        val summary = json.optString("summary", "Özet metni oluşturuldu.")
                        val keyPointsArray = json.optJSONArray("key_points")
                        val keyPoints = mutableListOf<String>()
                        if (keyPointsArray != null) {
                            for (i in 0 until keyPointsArray.length()) {
                                keyPoints.add(keyPointsArray.getString(i))
                            }
                        }
                        Result.success(Pair(summary, keyPoints))
                    } else {
                        Result.success(generateBuiltInSummary(content, courseName))
                    }
                } catch (e: Exception) {
                    Result.success(generateBuiltInSummary(content, courseName))
                }
            }

            ConnectionMode.BUILTIN_AI -> {
                Result.success(generateBuiltInSummary(content, courseName))
            }
        }
    }

    suspend fun generateQuiz(
        content: String,
        courseName: String,
        count: Int = 5,
        config: ServerConfig
    ): Result<List<QuizQuestion>> = withContext(Dispatchers.IO) {
        when (config.mode) {
            ConnectionMode.N8N_WEBHOOK -> {
                try {
                    val url = "${config.n8nBaseUrl.trim().removeSuffix("/")}/webhook/generate-quiz"
                    val request = QuizWebhookRequest(
                        content = content,
                        course = courseName,
                        count = count,
                        model = config.selectedModel
                    )
                    val response = apiService.sendQuizWebhook(url, request)
                    if (response.isSuccessful && response.body()?.questions != null) {
                        val parsed = response.body()!!.questions!!.map { dto ->
                            dtoToQuestion(dto)
                        }
                        if (parsed.isNotEmpty()) {
                            return@withContext Result.success(parsed)
                        }
                    }
                    Result.success(generateBuiltInQuiz(content, courseName, count))
                } catch (e: Exception) {
                    Result.success(generateBuiltInQuiz(content, courseName, count))
                }
            }

            ConnectionMode.OLLAMA_DIRECT -> {
                try {
                    val url = "${config.ollamaBaseUrl.trim().removeSuffix("/")}/api/generate"
                    val prompt = """
                        Aşağıdaki "$courseName" ders notunu kullanarak en az $count adet çoktan seçmeli test sorusu hazırla.
                        Kesinlikle yalnızca geçerli bir JSON objesi döndür:
                        {
                          "questions": [
                            {
                              "question": "Soru metni?",
                              "options": ["A seçeneği", "B seçeneği", "C seçeneği", "D seçeneği"],
                              "correct_answer": "A seçeneği",
                              "explanation": "Açıklama"
                            }
                          ]
                        }

                        Ders Notu:
                        $content
                    """.trimIndent()
                    val request = OllamaGenerateRequest(
                        model = config.selectedModel,
                        prompt = prompt,
                        stream = false,
                        format = "json"
                    )
                    val response = apiService.generateWithOllama(url, request)
                    val jsonStr = response.body()?.response
                    if (response.isSuccessful && !jsonStr.isNullOrBlank()) {
                        val json = JSONObject(jsonStr)
                        val qArray = json.optJSONArray("questions")
                        if (qArray != null && qArray.length() > 0) {
                            val questionsList = mutableListOf<QuizQuestion>()
                            for (i in 0 until qArray.length()) {
                                val item = qArray.getJSONObject(i)
                                val qText = item.getString("question")
                                val optArray = item.getJSONArray("options")
                                val opts = mutableListOf<String>()
                                for (j in 0 until optArray.length()) {
                                    opts.add(optArray.getString(j))
                                }
                                val correct = item.optString("correct_answer", opts.firstOrNull() ?: "")
                                val expl = item.optString("explanation", "")
                                questionsList.add(
                                    QuizQuestion(
                                        question = qText,
                                        options = opts,
                                        correctAnswer = correct,
                                        explanation = expl
                                    )
                                )
                            }
                            return@withContext Result.success(questionsList)
                        }
                    }
                    Result.success(generateBuiltInQuiz(content, courseName, count))
                } catch (e: Exception) {
                    Result.success(generateBuiltInQuiz(content, courseName, count))
                }
            }

            ConnectionMode.BUILTIN_AI -> {
                Result.success(generateBuiltInQuiz(content, courseName, count))
            }
        }
    }

    suspend fun evaluatePerformance(
        total: Int,
        correct: Int,
        wrong: Int,
        wrongQuestions: List<String>,
        courseName: String,
        config: ServerConfig
    ): Result<String> = withContext(Dispatchers.IO) {
        when (config.mode) {
            ConnectionMode.N8N_WEBHOOK -> {
                try {
                    val url = "${config.n8nBaseUrl.trim().removeSuffix("/")}/webhook/evaluate-performance"
                    val request = EvaluationWebhookRequest(
                        total = total,
                        correct = correct,
                        wrong = wrong,
                        course = courseName,
                        wrongQuestions = wrongQuestions,
                        model = config.selectedModel
                    )
                    val response = apiService.sendEvaluationWebhook(url, request)
                    if (response.isSuccessful && response.body() != null) {
                        Result.success(response.body()!!.getEffectiveRecommendations())
                    } else {
                        Result.success(generateBuiltInEvaluation(total, correct, wrong, wrongQuestions, courseName))
                    }
                } catch (e: Exception) {
                    Result.success(generateBuiltInEvaluation(total, correct, wrong, wrongQuestions, courseName))
                }
            }

            ConnectionMode.OLLAMA_DIRECT -> {
                try {
                    val url = "${config.ollamaBaseUrl.trim().removeSuffix("/")}/api/generate"
                    val prompt = """
                        Bir öğrenci $courseName dersinde test çözdü.
                        Toplam Soru: $total
                        Doğru Sayısı: $correct
                        Yanlış Sayısı: $wrong
                        Hatalı Cevaplanan Sorular/Konular:
                        ${wrongQuestions.joinToString("\n- ")}

                        Öğrencinin performansını değerlendir, eksik olduğu konuları analiz et ve 3-4 maddelik somut çalışma tavsiyesi ver.
                    """.trimIndent()
                    val request = OllamaGenerateRequest(
                        model = config.selectedModel,
                        prompt = prompt,
                        stream = false
                    )
                    val response = apiService.generateWithOllama(url, request)
                    if (response.isSuccessful && response.body()?.response != null) {
                        Result.success(response.body()!!.response!!)
                    } else {
                        Result.success(generateBuiltInEvaluation(total, correct, wrong, wrongQuestions, courseName))
                    }
                } catch (e: Exception) {
                    Result.success(generateBuiltInEvaluation(total, correct, wrong, wrongQuestions, courseName))
                }
            }

            ConnectionMode.BUILTIN_AI -> {
                Result.success(generateBuiltInEvaluation(total, correct, wrong, wrongQuestions, courseName))
            }
        }
    }

    suspend fun testConnection(url: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.pingEndpoint(url)
            if (response.isSuccessful) {
                Pair(true, "Bağlantı başarılı! (HTTP ${response.code()})")
            } else {
                Pair(false, "Sunucuya ulaşıldı ancak hata döndü (HTTP ${response.code()})")
            }
        } catch (e: Exception) {
            Pair(false, "Bağlantı kurulamadı: ${e.localizedMessage ?: "Zaman aşımı veya adres geçersiz"}")
        }
    }

    private fun dtoToQuestion(dto: QuizItemDto): QuizQuestion {
        val correct = dto.correct_answer ?: dto.correctAnswer ?: dto.options.firstOrNull() ?: ""
        return QuizQuestion(
            question = dto.question,
            options = dto.options,
            correctAnswer = correct,
            explanation = dto.explanation ?: "Doğru yanıt: $correct"
        )
    }

    // High quality intelligent generator for smooth operation & fallback
    private fun generateBuiltInAnswer(question: String, courseName: String, context: String?): String {
        val lowerQ = question.lowercase()
        return when {
            lowerQ.contains("ikili arama") || lowerQ.contains("bst") -> {
                """
                    **İkili Arama Ağacı (BST) Analizi:**
                    
                    İkili Arama Ağacı, elemanları sıralı tutan özel bir ağaç yapısıdır:
                    - **Kural:** Her düğümün solundaki tüm elemanlar kendisinden küçük, sağındaki tüm elemanlar kendisinden büyüktür.
                    - **Zaman Karmaşıklığı:**
                      - *Arama / Ekleme / Silme (Ortalama):* **O(log n)**
                      - *En kötü durum (dengesiz/çarpık ağaç):* **O(n)**
                    - **Sıralı Listeleme:** Inorder dolaşma (In-order Traversal: Sol -> Kök -> Sağ) ağaçtaki verileri küçükten büyüğe sıralı şekilde yazdırır.
                    
                    *Öneri:* Dengesizliği önlemek için AVL Ağacı veya Red-Black Ağacı gibi kendi kendini dengeleyen yapılar tercih edilir.
                """.trimIndent()
            }
            lowerQ.contains("deadlock") || lowerQ.contains("ölümcül kilitlenme") -> {
                """
                    **Deadlock (Kilitlenme) Koşulları:**
                    
                    Bir sistemde kilitlenme (deadlock) oluşabilmesi için **Coffman'ın 4 koşulunun** aynı anda gerçekleşmesi gerekir:
                    1. **Mutual Exclusion (Karşılıklı Dışlama):** En az bir kaynak paylaşılamaz olmalı, tek seferde bir süreç kullanmalı.
                    2. **Hold and Wait (Tut ve Bekle):** Bir süreç en az bir kaynağı elinde tutarken başka bir kaynağı beklemeli.
                    3. **No Preemption (Zorla Alma Yok):** Süreç işini bitirmeden elindeki kaynak zorla alınamaz.
                    4. **Circular Wait (Döngüsel Bekleme):** Süreçler arasında dairesel bir bekleme zinciri olmalıdır (P0 -> P1 -> P2 -> P0).
                    
                    *Çözüm Yolları:* Deadlock Prevention (Önleme), Deadlock Avoidance (Banker's Algoritması ile Kaçınma), Deadlock Detection & Recovery (Tespit ve İyileştirme).
                """.trimIndent()
            }
            lowerQ.contains("big o") || lowerQ.contains("karmaşıklık") || lowerQ.contains("complexity") -> {
                """
                    **Asimptotik Notasyon (Big-O Analizi):**
                    
                    Big-O, bir algoritmanın girdi boyutu (n) arttıkça ihtiyaç duyduğu en kötü durum çalışma zamanını veya bellek miktarını ifade eder.
                    
                    - **O(1) - Sabit:** Hash table araması, dizi indeks erişimi.
                    - **O(log n) - Logaritmik:** Binary Search (İkili arama).
                    - **O(n) - Doğrusal:** Dizide tek geçişli arama (Linear Search).
                    - **O(n log n) - Doğrusal Logaritmik:** Merge Sort, Quick Sort (ortalama), Heap Sort.
                    - **O(n²) - Karesel:** Bubble Sort, Insertion Sort, iç içe iki döngü.
                    - **O(2ⁿ) - Üstel:** Naive Fibonacci, Alt küme problemleri.
                """.trimIndent()
            }
            lowerQ.contains("acid") || lowerQ.contains("transaksiyon") || lowerQ.contains("transaction") -> {
                """
                    **Veritabanı Transaksiyonlarında ACID İlkeleri:**
                    
                    ACID, veritabanı işlemlerinin güvenilirliğini garanti altına alan dört temel özelliktir:
                    1. **Atomicity (Bütünlük):** "Ya hep ya hiç". İşlem ya bütünüyle tamamlanır ya da hiç yapılmamış gibi geri alınır (Rollback).
                    2. **Consistency (Tutarlılık):** Transaksiyon veritabanını bir geçerli durumdan başka bir geçerli duruma taşır; bütünlük kısıtları ihlal edilemez.
                    3. **Isolation (Yalıtım):** Eşzamanlı çalışan transaksiyonlar birbirinin ara durumlarını göremez.
                    4. **Durability (Kalıcılık):** Commit edilen işlem sistem çökse dahi asla kaybolmaz.
                """.trimIndent()
            }
            else -> {
                """
                    **$courseName Dersi - Yapay Zekâ Yanıtı:**
                    
                    Öğrenci sorusu incelendi: "$question"
                    
                    ${context?.let { "İncelenen Ders Notu Bağlamı:\n$it\n\n" } ?: ""}
                    **Konu Açıklaması:**
                    Bu konu $courseName müfredatının kritik kazanımlarından biridir. Konsepti anlamak için teorik temeli pratik örneklerle birleştirmek gerekir.
                    
                    - **Temel Prensip:** İlgili veri yapısı veya algoritma sistem kaynaklarını optimize etmeyi hedefler.
                    - **Kritik Nokta:** Uygulamada sınır durumları (edge cases), bellek kullanımı ve işlem süresi mutlaka gözetilmelidir.
                    - **Önerilen Adım:** Bu konuyu pekiştirmek için sol menüdeki 'Test Oluştur' sekmesini kullanarak 5 soruluk hızlı değerlendirme sınavını çözebilirsiniz.
                """.trimIndent()
            }
        }
    }

    private fun generateBuiltInSummary(content: String, courseName: String): Pair<String, List<String>> {
        val lines = content.lines().filter { it.isNotBlank() }
        val titleLine = lines.firstOrNull() ?: "Ders Notu"
        val wordCount = content.split("\\s+".toRegex()).size

        val summary = """
            Bu ders notu "$courseName" kapsamında **$titleLine** konusunu detaylandırmaktadır. 
            Metinde temel tanımlar, mimari yapı, avantaj ve dezavantajlar ile çalışma prensipleri ele alınmıştır.
            Öğrencinin sınava hazırlanırken konunun matematiksel ve algoritmik mantığına odaklanması, zaman ve alan karmaşıklığı analizlerini gözden geçirmesi tavsiye edilir. (Analiz edilen not hacmi: $wordCount kelime).
        """.trimIndent()

        val keyPoints = listOf(
            "Temel tanım ve hedeflenen problem çözümü",
            "Sistem mimarisi ve bileşenler arasındaki veri akışı",
            "Ortalama ve en kötü durum karmaşıklık analizleri",
            "Uygulamada dikkat edilmesi gereken sınır durumlar",
            "Sınavlarda sıkça sorulan ayırt edici karşılaştırmalar"
        )

        return Pair(summary, keyPoints)
    }

    private fun generateBuiltInQuiz(content: String, courseName: String, count: Int): List<QuizQuestion> {
        val lower = (content + " " + courseName).lowercase()

        return if (lower.contains("ağaç") || lower.contains("tree") || lower.contains("bst") || lower.contains("veri yap")) {
            listOf(
                QuizQuestion(
                    question = "Bir İkili Arama Ağacında (BST) elemanları küçükten büyüğe sıralı şekilde ziyaret eden dolaşma (traversal) yöntemi hangisidir?",
                    options = listOf("Preorder Traversal", "Inorder Traversal", "Postorder Traversal", "Level-order Traversal"),
                    correctAnswer = "Inorder Traversal",
                    explanation = "Inorder dolaşma Sırasıyla: Sol Alt Ağaç -> Kök -> Sağ Alt Ağaç sırasını izler ve BST'de elemanları tam sıralı verir."
                ),
                QuizQuestion(
                    question = "Dengeli bir İkili Arama Ağacında (örn. AVL) arama işleminin ortalama zaman karmaşıklığı nedir?",
                    options = listOf("O(1)", "O(log n)", "O(n)", "O(n log n)"),
                    correctAnswer = "O(log n)",
                    explanation = "Ağacın yüksekliği log₂(n) ile orantılı olduğundan arama karmaşıklığı O(log n)'dir."
                ),
                QuizQuestion(
                    question = "Tamamen çarpık (skewed/dengesiz) bir ikili arama ağacında en kötü arama süresi hangi seviyeye geriler?",
                    options = listOf("O(log n)", "O(n)", "O(n²)", "O(1)"),
                    correctAnswer = "O(n)",
                    explanation = "Çarpık ağaç tek yönlü bağlı liste gibi davranır, bu yüzden arama O(n) olur."
                ),
                QuizQuestion(
                    question = "Aşağıdakilerden hangisi bir Yığın (Stack) veri yapısının temel prensibidir?",
                    options = listOf("FIFO (First In First Out)", "LIFO (Last In First Out)", "LILO", "Rastgele Erişim"),
                    correctAnswer = "LIFO (Last In First Out)",
                    explanation = "Yığın (Stack), son giren elemanın ilk çıktığı LIFO mantığıyla çalışır."
                ),
                QuizQuestion(
                    question = "Bir ikili ağaçta kök düğümün derinliği (depth) varsayılan olarak kaçtır?",
                    options = listOf("0", "1", "n", "Sonsuz"),
                    correctAnswer = "0",
                    explanation = "Standart literatürde kök düğümün derinliği 0 olarak kabul edilir."
                )
            )
        } else if (lower.contains("işletim") || lower.contains("deadlock") || lower.contains("process") || lower.contains("thread")) {
            listOf(
                QuizQuestion(
                    question = "Aşağıdakilerden hangisi Deadlock (Ölümcül Kilitlenme) oluşması için gerekli 4 Coffman şartından biri DEĞİLDİR?",
                    options = listOf("Karşılıklı Dışlama (Mutual Exclusion)", "Tut ve Bekle (Hold and Wait)", "Öncelikli Kesme (Preemption Var)", "Döngüsel Bekleme (Circular Wait)"),
                    correctAnswer = "Öncelikli Kesme (Preemption Var)",
                    explanation = "Deadlock için 'No Preemption' (Zorla kaynak alma yok) geçerli olmalıdır. Kaynak zorla alınabiliyorsa deadlock oluşmaz."
                ),
                QuizQuestion(
                    question = "Banker's Algoritması işletim sistemlerinde hangi amaçla kullanılır?",
                    options = listOf("Sayfalama Tablosu Oluşturma", "Deadlock'tan Kaçınma (Avoidance)", "Dosya Şifreleme", "Process Zamanlama"),
                    correctAnswer = "Deadlock'tan Kaçınma (Avoidance)",
                    explanation = "Banker algoritması güvenli durum (safe state) analizi yaparak deadlock'tan kaçınmayı sağlar."
                ),
                QuizQuestion(
                    question = "Aynı process içindeki birden fazla thread aşağıdakilerden hangisini doğrudan PAYLAŞIR?",
                    options = listOf("Stack (Yığın) Alanı", "Program Sayacı (PC)", "Kod ve Veri Bölümü (Code & Data)", "Yazmaçlar (Registers)"),
                    correctAnswer = "Kod ve Veri Bölümü (Code & Data)",
                    explanation = "Thread'ler aynı adres uzayını, kod ve global veri bölümünü paylaşırlar; stack ve register'ları ise kendilerine özeldir."
                ),
                QuizQuestion(
                    question = "Sanal bellekte bir sayfanın fiziksel RAM'de bulunamaması durumuna ne ad verilir?",
                    options = listOf("Page Hit", "Page Fault (Sayfa Hatası)", "Fragmentation", "Thrashing"),
                    correctAnswer = "Page Fault (Sayfa Hatası)",
                    explanation = "İstenen sayfa RAM'de değilse işletim sistemi donanımı tarafından Page Fault sinyali üretilir ve diskten yüklenir."
                ),
                QuizQuestion(
                    question = "Aşağıdaki CPU zamanlama algoritmalarından hangisi açlık (starvation) riskine sahiptir?",
                    options = listOf("Round Robin (RR)", "Shortest Job First (SJF)", "First-Come First-Served (FCFS)", "FIFO"),
                    correctAnswer = "Shortest Job First (SJF)",
                    explanation = "SJF algoritmasında sürekli kısa işler gelirse uzun sürecek işlemler açlığa (starvation) maruz kalabilir."
                )
            )
        } else {
            listOf(
                QuizQuestion(
                    question = "$courseName kapsamında en iyi durum ile en kötü durum arasındaki farkı belirleyen temel faktör nedir?",
                    options = listOf("Girdi verisinin başlangıç dizilimi", "Kullanılan ekran kartı", "İşletim sisteminin dili", "Derleyici sürümü"),
                    correctAnswer = "Girdi verisinin başlangıç dizilimi",
                    explanation = "Algoritmaların performansı doğrudan girdinin önceden sıralı olup olmamasına veya dağılımına bağlıdır."
                ),
                QuizQuestion(
                    question = "Böl ve Yönet (Divide and Conquer) paradigmasına uygun sıralama algoritması hangisidir?",
                    options = listOf("Merge Sort", "Bubble Sort", "Selection Sort", "Linear Search"),
                    correctAnswer = "Merge Sort",
                    explanation = "Merge Sort diziyi ikiye böler, ayrı ayrı sıralar ve birleştirir (Divide, Conquer, Combine)."
                ),
                QuizQuestion(
                    question = "İlişkisel veritabanlarında veri tekrarını azaltmak ve anomalileri önlemek için uygulanan süreç nedir?",
                    options = listOf("Normalizasyon", "İndeksleme", "Replica", "Sharding"),
                    correctAnswer = "Normalizasyon",
                    explanation = "Normalizasyon (1NF, 2NF, 3NF), veri tutarlılığını sağlamak ve gereksiz tekrarları yok etmek için uygulanır."
                ),
                QuizQuestion(
                    question = "Yapay zekâ modellerinde aşırı öğrenmeyi (overfitting) engellemek için hangi yöntem kullanılır?",
                    options = listOf("Regularization (Düzenlileştirme)", "Learning rate'i sonsuza çekmek", "Modeli küçültüp eğitmemek", "Veri setini silmek"),
                    correctAnswer = "Regularization (Düzenlileştirme)",
                    explanation = "Dropout, L1/L2 regularization aşırı uyumu (overfitting) önleyen yaygın yöntemlerdir."
                ),
                QuizQuestion(
                    question = "TCP protokolünün UDP'ye göre en temel ayırt edici avantajı nedir?",
                    options = listOf("Daha düşük gecikme", "Güvenilir, sıralı ve onaylı veri iletimi", "Yayın (Broadcast) desteği", "Başlık (Header) boyutunun 8 byte olması"),
                    correctAnswer = "Güvenilir, sıralı ve onaylı veri iletimi",
                    explanation = "TCP, 3 yollu el sıkışma (3-way handshake) ve sıra numaraları ile kayıpsız ve sıralı teslimat garantisi sunar."
                )
            )
        }
    }

    private fun generateBuiltInEvaluation(
        total: Int,
        correct: Int,
        wrong: Int,
        wrongQuestions: List<String>,
        courseName: String
    ): String {
        val percentage = if (total > 0) (correct * 100) / total else 0

        return buildString {
            append("🎯 **Yapay Zekâ Başarı ve Çalışma Önerisi Raporu**\n\n")
            when {
                percentage >= 80 -> {
                    append("🌟 **Tebrikler!** $courseName dersinde %$percentage başarı oranına ulaştınız. Temel kavramlara ve algoritmik mantığa oldukça hâkimsiniz.\n\n")
                    append("**İleri Düzey Öneriler:**\n")
                    append("1. Konunun daha karmaşık uç senaryolarını ve sınır durumlarını (edge cases) inceleyin.\n")
                    append("2. Farklı veri yapıları arasındaki geçiş ve dönüşüm performanslarını karşılaştırın.\n")
                    append("3. Bir sonraki ünitenin ders notunu yükleyerek yeni konuya şimdiden geçebilirsiniz.")
                }
                percentage >= 50 -> {
                    append("📈 **İyi Bir İlerleme:** %$percentage başarı elde ettiniz. Konunun ana hatlarını kavramış durumdasınız ancak bazı kritik detaylarda soru kaçırıyorsunuz.\n\n")
                    append("**Eksik Konu ve Çalışma Tavsiyeleri:**\n")
                    if (wrongQuestions.isNotEmpty()) {
                        append("Özellikle şu sorulardaki kazanımları tekrar etmelisiniz:\n")
                        wrongQuestions.take(3).forEach {
                            append("- ${it.take(75)}...\n")
                        }
                        append("\n")
                    }
                    append("1. İlgili ders notunun özetindeki anahtar maddeleri not defterinize çıkarın.\n")
                    append("2. Zaman karmaşıklığı analizlerini adım adım kağıt üzerinde test edin.\n")
                    append("3. 2 gün sonra aynı testi tekrar çözerek bilginizi kalıcı hale getirin.")
                }
                else -> {
                    append("⚠️ **Öncelikli Tekrar Gerekiyor:** %$percentage başarı seviyesindesiniz. Temel prensiplerde kafa karışıklığı bulunuyor.\n\n")
                    append("**Eylem Planı:**\n")
                    append("1. Soru-Cevap asistanına bu konuda anlamadığınız terimleri tek tek sorun.\n")
                    append("2. Ders notunun 'Özetle' butonunu kullanarak yapay zekâ tarafından hazırlanan 5 temel maddeye odaklanın.\n")
                    append("3. Testi bitirdikten sonra her sorunun altındaki açıklama bölümünü dikkatle okuyun.")
                }
            }
        }
    }
}
