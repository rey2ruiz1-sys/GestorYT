package com.example.data.backup

import android.net.Uri
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class TransferManifest(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val backupVersion: Int = CURRENT_BACKUP_VERSION,
    val appId: String,
    val appName: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val createdAt: Long,
    val apkEntryName: String = ENTRY_APK,
    val apkSizeBytes: Long,
    val apkSha256: String,
    val apkSource: String,
    val backupEntryName: String = ENTRY_BACKUP_ZIP,
    val backupSizeBytes: Long,
    val backupSha256: String,
    val interviewCount: Int,
    val questionCount: Int,
    val mediaCount: Int
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 1
        const val CURRENT_BACKUP_VERSION = 1
        const val ENTRY_APK = "Gestor_Entrevistas.apk"
        const val ENTRY_BACKUP_ZIP = "respaldo.zip"
        const val ENTRY_MANIFEST = "manifest.json"
    }
}

@Serializable
data class BackupManifest(
    val backupVersion: Int = TransferManifest.CURRENT_BACKUP_VERSION,
    val appId: String,
    val appVersionName: String,
    val appVersionCode: Long,
    val createdAt: Long,
    val backupDataEntryName: String = ENTRY_BACKUP_DATA,
    val backupDataSizeBytes: Long,
    val backupDataSha256: String,
    val interviewCount: Int,
    val questionCount: Int,
    val mediaCount: Int
) {
    companion object {
        const val ENTRY_BACKUP_DATA = "backup_data.json"
        const val ENTRY_BACKUP_MANIFEST = "backup_manifest.json"
    }
}

@Serializable
data class BackupPayload(
    val backupVersion: Int = TransferManifest.CURRENT_BACKUP_VERSION,
    val createdAt: Long,
    val interviews: List<BackupInterview>
)

@Serializable
data class BackupInterview(
    val title: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val questions: List<BackupQuestion>,
    val mediaItems: List<BackupMediaItem>
)

@Serializable
data class BackupQuestion(
    val text: String,
    val sortOrder: Int
)

@Serializable
data class BackupMediaItem(
    val type: String,
    val archiveEntryPath: String,
    val mimeType: String,
    val originalName: String,
    val width: Int,
    val height: Int,
    val durationMs: Long? = null,
    val createdAt: Long,
    val sizeBytes: Long,
    val sha256: String
)

sealed interface ApkResolution {
    data class SingleInstalledApk(val file: File) : ApkResolution
    data class SplitOrUnavailable(val reason: String) : ApkResolution
}

sealed interface ApkInputSource {
    data class InstalledFile(val file: File) : ApkInputSource
    data class ManualUri(val uri: Uri) : ApkInputSource
}

data class TransferProgress(
    val message: String,
    val fraction: Float? = null
)

data class ExportPackageSummary(
    val packageFile: File,
    val totalSizeBytes: Long,
    val interviewCount: Int,
    val questionCount: Int,
    val mediaCount: Int,
    val apkSizeBytes: Long,
    val usedManualApk: Boolean
)

data class ImportBackupSummary(
    val interviewCount: Int,
    val questionCount: Int,
    val mediaCount: Int
)
