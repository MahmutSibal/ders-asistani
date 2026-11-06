package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.QuizAttemptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuizAttemptDao {
    @Query("SELECT * FROM quiz_attempts ORDER BY date DESC")
    fun getAllAttempts(): Flow<List<QuizAttemptEntity>>

    @Query("SELECT * FROM quiz_attempts WHERE courseId = :courseId ORDER BY date DESC")
    fun getAttemptsByCourse(courseId: String): Flow<List<QuizAttemptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempt(attempt: QuizAttemptEntity)

    @Query("SELECT AVG(percentage) FROM quiz_attempts")
    fun getAverageScore(): Flow<Double?>

    @Query("SELECT COUNT(*) FROM quiz_attempts")
    fun getTotalQuizCount(): Flow<Int>
}
