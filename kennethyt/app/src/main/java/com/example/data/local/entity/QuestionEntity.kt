package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = InterviewEntity::class,
            parentColumns = ["id"],
            childColumns = ["interviewId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["interviewId", "sortOrder"])]
)
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val interviewId: Long,
    val text: String,
    val sortOrder: Int
)
