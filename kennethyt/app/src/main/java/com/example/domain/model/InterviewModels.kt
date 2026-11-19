package com.example.domain.model

import androidx.compose.runtime.Immutable

enum class MediaType {
    IMAGE,
    VIDEO
}

@Immutable
data class MediaItem(
    val id: Long,
    val interviewId: Long,
    val type: MediaType,
    val localPath: String,
    val mimeType: String,
    val originalName: String,
    val width: Int,
    val height: Int,
    val durationMs: Long?,
    val createdAt: Long
) {
    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 1f
}

@Immutable
data class Question(
    val id: Long,
    val interviewId: Long,
    val text: String,
    val sortOrder: Int
)

@Immutable
data class Interview(
    val id: Long,
    val title: String,
    val sortOrder: Int,
    val questions: List<Question>,
    val imageMedia: MediaItem?,
    val videoMedia: MediaItem?,
    val createdAt: Long,
    val updatedAt: Long
) {
    val hasAnyMedia: Boolean
        get() = imageMedia != null || videoMedia != null
}
