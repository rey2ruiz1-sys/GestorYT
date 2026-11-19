package com.example.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.data.local.dao.InterviewDao
import com.example.data.local.entity.InterviewEntity
import com.example.data.local.entity.MediaEntity
import com.example.data.local.entity.QuestionEntity
import com.example.domain.model.MediaType

class MediaTypeConverters {
    @TypeConverter
    fun fromMediaType(type: MediaType): String = type.name

    @TypeConverter
    fun toMediaType(value: String): MediaType = MediaType.valueOf(value)
}

@Database(
    entities = [
        InterviewEntity::class,
        QuestionEntity::class,
        MediaEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(MediaTypeConverters::class)
abstract class KennethDatabase : RoomDatabase() {
    abstract fun interviewDao(): InterviewDao

    companion object {
        private const val DATABASE_NAME = "kenneth_yt_interviews.db"

        @Volatile
        private var INSTANCE: KennethDatabase? = null

        fun getInstance(context: Context): KennethDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KennethDatabase::class.java,
                    DATABASE_NAME
                ).build().also { INSTANCE = it }
            }
        }
    }
}
