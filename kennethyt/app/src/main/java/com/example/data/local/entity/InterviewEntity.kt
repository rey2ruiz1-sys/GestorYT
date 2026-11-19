package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "interviews",
    indices = [Index(value = ["sortOrder"])]
)
data class InterviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long
)
