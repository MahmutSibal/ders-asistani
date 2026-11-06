package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "study_notes")
data class StudyNoteEntity(
    @PrimaryKey val id: String,
    val courseId: String,
    val title: String,
    val content: String,
    val summary: String,
    val keyPointsJson: String, // Stored as comma-separated or JSON
    val createdAt: Long = System.currentTimeMillis()
)
