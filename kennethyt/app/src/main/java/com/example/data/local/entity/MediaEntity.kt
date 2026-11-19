package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.MediaType

@Entity(
    tableName = "media_items",
    foreignKeys = [
        ForeignKey(
            entity = InterviewEntity::class,
            parentColumns = ["id"],
            childColumns = ["interviewId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["interviewId", "type"], unique = true)]
)
data class MediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val interviewId: Long,
    val type: MediaType,
    val localPath: String,
    val mimeType: String,
    val originalName: String,
    val width: Int,
    val height: Int,
    val durationMs: Long?,
    val createdAt: Long
)
