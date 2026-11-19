package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.data.backup.BackupTransferManager
import com.example.data.local.database.KennethDatabase
import com.example.data.media.MediaStorageManager
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.InterviewRepository
import com.example.data.repository.LocalInterviewRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(
    private val application: Application,
    customRepository: InterviewRepository? = null,
    customPreferences: UserPreferencesRepository? = null,
    customDatabase: KennethDatabase? = null
) {
    val database: KennethDatabase by lazy {
        customDatabase ?: KennethDatabase.getInstance(application)
    }

    val preferencesRepository: UserPreferencesRepository by lazy {
        customPreferences ?: UserPreferencesRepository(application)
    }

    val mediaStorageManager: MediaStorageManager by lazy {
        MediaStorageManager(application)
    }

    val interviewRepository: InterviewRepository by lazy {
        customRepository ?: LocalInterviewRepository(
            dao = database.interviewDao(),
            mediaStorage = mediaStorageManager,
            preferences = preferencesRepository
        )
    }

    val backupTransferManager: BackupTransferManager by lazy {
        BackupTransferManager(
            context = application,
            database = database,
            preferences = preferencesRepository
        )
    }
}

class KennethApplication : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            container.interviewRepository.ensureSeeded()
        }
    }

    override fun newImageLoader(): ImageLoader {
        // Memory-conscious Coil ImageLoader tuned for low-RAM devices (2 GB+)
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.18)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(32L * 1024L * 1024L)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
    }
}
