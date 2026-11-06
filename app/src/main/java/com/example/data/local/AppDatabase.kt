package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.ChatMessageDao
import com.example.data.local.dao.CourseDao
import com.example.data.local.dao.QuizAttemptDao
import com.example.data.local.dao.StudyNoteDao
import com.example.data.local.entity.ChatMessageEntity
import com.example.data.local.entity.CourseEntity
import com.example.data.local.entity.QuizAttemptEntity
import com.example.data.local.entity.StudyNoteEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        CourseEntity::class,
        StudyNoteEntity::class,
        ChatMessageEntity::class,
        QuizAttemptEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun courseDao(): CourseDao
    abstract fun studyNoteDao(): StudyNoteDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun quizAttemptDao(): QuizAttemptDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "study_assistant_database"
                )
                    .addCallback(DatabaseCallback())
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateInitialCourses(database.courseDao(), database.studyNoteDao())
                    }
                }
            }
        }

        suspend fun populateInitialCourses(courseDao: CourseDao, noteDao: StudyNoteDao) {
            val defaultCourses = listOf(
                CourseEntity(
                    id = "c_cs101",
                    code = "CS101",
                    name = "Veri Yapıları",
                    description = "Bağlı listeler, ikili arama ağaçları, yığınlar, kuyruklar ve graf algoritmaları.",
                    colorHex = "#2563EB",
                    iconName = "account_tree"
                ),
                CourseEntity(
                    id = "c_cs202",
                    code = "CS202",
                    name = "İşletim Sistemleri",
                    description = "Process yönetimi, deadlock koşulları, sanal bellek, sayfalama ve dosya sistemleri.",
                    colorHex = "#8B5CF6",
                    iconName = "memory"
                ),
                CourseEntity(
                    id = "c_cs301",
                    code = "CS301",
                    name = "Algoritmalar",
                    description = "Zaman karmaşıklığı (Big-O), dinamik programlama, açgözlü algoritmalar ve böl-yönet.",
                    colorHex = "#059669",
                    iconName = "speed"
                ),
                CourseEntity(
                    id = "c_cs304",
                    code = "CS304",
                    name = "Veritabanı Sistemleri",
                    description = "İlişkisel model, SQL sorguları, normalizasyon (1NF, 2NF, 3NF, BCNF) ve ACID ilkeleri.",
                    colorHex = "#D97706",
                    iconName = "database"
                ),
                CourseEntity(
                    id = "c_cs405",
                    code = "CS405",
                    name = "Yapay Zekâ",
                    description = "Arama stratejileri, yapay sinir ağları, makine öğrenimi ve büyük dil modelleri (LLM).",
                    colorHex = "#E11D48",
                    iconName = "psychology"
                )
            )
            courseDao.insertCourses(defaultCourses)

            // Seed an initial rich study note for immediate demo capability
            val initialNote = StudyNoteEntity(
                id = "note_sample_1",
                courseId = "c_cs101",
                title = "İkili Arama Ağaçları (BST) Temelleri",
                content = """İkili Arama Ağacı (Binary Search Tree - BST), her düğümün en fazla iki alt düğüme (sol ve sağ) sahip olduğu hiyerarşik bir veri yapısıdır.

Temel Kurallar:
1. Sol alt ağaçtaki tüm düğümlerin anahtar değerleri, kök düğümün anahtar değerinden küçüktür.
2. Sağ alt ağaçtaki tüm düğümlerin anahtar değerleri, kök düğümün anahtar değerinden büyüktür.
3. Hem sol hem de sağ alt ağaçlar da kendileri birer ikili arama ağacıdır ve tekrarlayan (duplicate) anahtarlara varsayılan olarak izin verilmez.

Zaman Karmaşıklığı:
- Arama (Search): Ortalama durumda O(log n), en kötü durumda (çarpık/dengesiz ağaç) O(n).
- Ekleme (Insertion): Ortalama O(log n), en kötü O(n).
- Silme (Deletion): Ortalama O(log n), en kötü O(n).

Dolaşma (Traversal) Türleri:
- Inorder Traversal (Sol - Kök - Sağ): Elemanları küçükten büyüğe sıralı olarak verir.
- Preorder Traversal (Kök - Sol - Sağ): Ağacın kopyasını oluşturmak için uygundur.
- Postorder Traversal (Sol - Sağ - Kök): Ağacı bellekten temizlemek/silmek için uygundur.""".trimIndent(),
                summary = "İkili Arama Ağacı (BST), sol çocukları kökten küçük, sağ çocukları büyük olan hiyerarşik bir yapıdır. Ortalama arama karmaşıklığı O(log n) iken dengesiz ağaçlarda O(n)'ye düşer. Inorder dolaşma sıralı eleman listesi üretir.",
                keyPointsJson = "Sol çocuk < Kök < Sağ çocuk,Ortalama O(log n) arama süresi,Dengesiz durumda O(n) risk,Inorder dolaşma sıralı veri verir,AVL ve Red-Black dengeli varyantlardır"
            )
            noteDao.insertNote(initialNote)
        }
    }
}
