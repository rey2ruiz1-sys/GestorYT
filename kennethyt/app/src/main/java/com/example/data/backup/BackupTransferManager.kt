package com.example.data.backup

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.example.BuildConfig
import com.example.data.local.database.KennethDatabase
import com.example.data.local.entity.InterviewEntity
import com.example.data.local.entity.MediaEntity
import com.example.data.local.entity.QuestionEntity
import com.example.data.preferences.UserPreferencesRepository
import com.example.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class BackupTransferManager(
    private val context: Context,
    private val database: KennethDatabase,
    private val preferences: UserPreferencesRepository,
    private val apkResolver: ApkResolver = ApkResolver(context)
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val exportsDir: File
        get() = File(context.cacheDir, "transfer_exports").apply { mkdirs() }

    private val importsDir: File
        get() = File(context.cacheDir, "transfer_imports").apply { mkdirs() }

    private val imagesDir: File
        get() = File(context.filesDir, "interview_media/images").apply { mkdirs() }

    private val videosDir: File
        get() = File(context.filesDir, "interview_media/videos").apply { mkdirs() }

    suspend fun resolveApkForExport(): ApkResolution = apkResolver.resolveInstalledApk()

    suspend fun cleanupTempFiles() = withContext(Dispatchers.IO) {
        runCatching { exportsDir.deleteRecursively() }
        runCatching { importsDir.deleteRecursively() }
        Unit
    }

    suspend fun buildTransferPackage(
        apkSource: ApkInputSource,
        onProgress: (TransferProgress) -> Unit = {}
    ): Result<ExportPackageSummary> = withContext(Dispatchers.IO) {
        var stagedManualApk: File? = null
        var tempBackupZip: File? = null
        val outputPackageFile = File(exportsDir, TRANSFER_PACKAGE_FILENAME)

        try {
            cleanupTempFiles()
            exportsDir.mkdirs()

            onProgress(TransferProgress("Preparando instalador APK...", 0.05f))

            val apkFile = when (apkSource) {
                is ApkInputSource.InstalledFile -> {
                    val resolution = apkResolver.resolveInstalledApk()
                    if (resolution is ApkResolution.SplitOrUnavailable) {
                        return@withContext Result.failure(IllegalStateException(resolution.reason))
                    }
                    apkSource.file
                }

                is ApkInputSource.ManualUri -> {
                    val tempApk = File(exportsDir, "staged_manual_${UUID.randomUUID()}.apk")
                    stagedManualApk = tempApk
                    apkResolver.stageAndValidateManualApk(apkSource.uri, tempApk).getOrElse { err ->
                        return@withContext Result.failure(err)
                    }
                }
            }

            onProgress(TransferProgress("Leyendo entrevistas y multimedia...", 0.12f))

            val dao = database.interviewDao()
            val allInterviews = dao.getAllInterviewsWithDetailsOnce()
                .sortedWith(compareBy({ it.interview.sortOrder }, { it.interview.id }))

            val mediaEntriesToPack = mutableListOf<PendingMediaPack>()
            var totalMediaBytes = 0L

            allInterviews.forEachIndexed { iIdx, item ->
                item.mediaItems.forEach { media ->
                    val file = File(media.localPath)
                    if (file.exists() && file.isFile && file.length() > 0L) {
                        val safeExt = file.extension.ifBlank {
                            if (media.type == MediaType.IMAGE) "jpg" else "mp4"
                        }
                        val folder = if (media.type == MediaType.IMAGE) "media/images" else "media/videos"
                        val entryPath = "$folder/${media.type.name.lowercase()}_${iIdx}_${media.id}.$safeExt"
                        totalMediaBytes += file.length()
                        mediaEntriesToPack.add(
                            PendingMediaPack(
                                interviewIndex = iIdx,
                                media = media,
                                sourceFile = file,
                                entryPath = entryPath
                            )
                        )
                    }
                }
            }

            val apkBytes = apkFile.length()
            val estimatedRequired = ((totalMediaBytes + apkBytes) * 1.25).toLong() + SAFETY_FREE_BYTES
            if (exportsDir.usableSpace < estimatedRequired) {
                return@withContext Result.failure(
                    IOException("Espacio insuficiente en almacenamiento interno para crear el paquete de transferencia.")
                )
            }

            val totalWorkBytes = (totalMediaBytes + apkBytes * 2L).coerceAtLeast(1L)
            var processedBytes = 0L
            var lastReportedBytes = 0L

            // 1. Build respaldo.zip streaming each media file in 64 KB chunks
            tempBackupZip = File(exportsDir, TransferManifest.ENTRY_BACKUP_ZIP)
            val packedMetaByMediaKey = mutableMapOf<Pair<Int, Long>, Pair<Long, String>>()
            val now = System.currentTimeMillis()

            ZipOutputStream(BufferedOutputStream(FileOutputStream(tempBackupZip), STREAM_BUFFER_SIZE)).use { zos ->
                zos.setLevel(Deflater.BEST_SPEED)

                mediaEntriesToPack.forEachIndexed { idx, pending ->
                    onProgress(
                        TransferProgress(
                            message = "Empaquetando multimedia (${idx + 1}/${mediaEntriesToPack.size})...",
                            fraction = (0.15f + 0.45f * (processedBytes.toFloat() / totalWorkBytes)).coerceIn(0.15f, 0.60f)
                        )
                    )
                    val (size, sha256) = writeFileToZipWithSha256(
                        zos = zos,
                        entryName = pending.entryPath,
                        sourceFile = pending.sourceFile
                    ) { delta ->
                        processedBytes += delta
                        if (processedBytes - lastReportedBytes >= PROGRESS_BYTE_STEP) {
                            lastReportedBytes = processedBytes
                            onProgress(
                                TransferProgress(
                                    message = "Empaquetando multimedia (${idx + 1}/${mediaEntriesToPack.size})...",
                                    fraction = (0.15f + 0.45f * (processedBytes.toFloat() / totalWorkBytes)).coerceIn(0.15f, 0.60f)
                                )
                            )
                        }
                    }
                    packedMetaByMediaKey[pending.interviewIndex to pending.media.id] = size to sha256
                }

                val backupInterviews = allInterviews.mapIndexed { iIdx, item ->
                    val sortedQuestions = item.questions
                        .sortedWith(compareBy({ it.sortOrder }, { it.id }))
                        .mapIndexed { qIdx, q ->
                            BackupQuestion(text = q.text, sortOrder = qIdx)
                        }

                    val backupMedia = item.mediaItems.mapNotNull { media ->
                        val packInfo = mediaEntriesToPack.firstOrNull {
                            it.interviewIndex == iIdx && it.media.id == media.id
                        } ?: return@mapNotNull null
                        val (size, sha256) = packedMetaByMediaKey[iIdx to media.id]
                            ?: return@mapNotNull null

                        BackupMediaItem(
                            type = media.type.name,
                            archiveEntryPath = packInfo.entryPath,
                            mimeType = media.mimeType,
                            originalName = media.originalName,
                            width = media.width,
                            height = media.height,
                            durationMs = media.durationMs,
                            createdAt = media.createdAt,
                            sizeBytes = size,
                            sha256 = sha256
                        )
                    }

                    BackupInterview(
                        title = item.interview.title,
                        sortOrder = iIdx,
                        createdAt = item.interview.createdAt,
                        updatedAt = item.interview.updatedAt,
                        questions = sortedQuestions,
                        mediaItems = backupMedia
                    )
                }

                val totalQuestions = backupInterviews.sumOf { it.questions.size }
                val totalMediaCount = backupInterviews.sumOf { it.mediaItems.size }

                val payload = BackupPayload(
                    backupVersion = TransferManifest.CURRENT_BACKUP_VERSION,
                    createdAt = now,
                    interviews = backupInterviews
                )
                val payloadBytes = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
                val (dataSize, dataSha256) = writeBytesToZipWithSha256(
                    zos = zos,
                    entryName = BackupManifest.ENTRY_BACKUP_DATA,
                    bytes = payloadBytes
                )

                val backupManifest = BackupManifest(
                    backupVersion = TransferManifest.CURRENT_BACKUP_VERSION,
                    appId = BuildConfig.APPLICATION_ID,
                    appVersionName = BuildConfig.VERSION_NAME,
                    appVersionCode = BuildConfig.VERSION_CODE.toLong(),
                    createdAt = now,
                    backupDataSizeBytes = dataSize,
                    backupDataSha256 = dataSha256,
                    interviewCount = backupInterviews.size,
                    questionCount = totalQuestions,
                    mediaCount = totalMediaCount
                )
                writeBytesToZipWithSha256(
                    zos = zos,
                    entryName = BackupManifest.ENTRY_BACKUP_MANIFEST,
                    bytes = json.encodeToString(backupManifest).toByteArray(Charsets.UTF_8)
                )
            }

            onProgress(TransferProgress("Empaquetando respaldo e instalador APK...", 0.65f))

            val totalQuestions = allInterviews.sumOf { it.questions.size }
            val totalMediaCount = mediaEntriesToPack.size

            // 2. Build outer transfer ZIP containing Gestor_Entrevistas.apk, respaldo.zip, manifest.json
            ZipOutputStream(BufferedOutputStream(FileOutputStream(outputPackageFile), STREAM_BUFFER_SIZE)).use { outerZos ->
                outerZos.setLevel(Deflater.BEST_SPEED)

                val (backupZipSize, backupZipSha256) = writeFileToZipWithSha256(
                    zos = outerZos,
                    entryName = TransferManifest.ENTRY_BACKUP_ZIP,
                    sourceFile = tempBackupZip
                ) { delta ->
                    processedBytes += delta
                    if (processedBytes - lastReportedBytes >= PROGRESS_BYTE_STEP) {
                        lastReportedBytes = processedBytes
                        onProgress(
                            TransferProgress(
                                message = "Añadiendo respaldo.zip...",
                                fraction = (0.65f + 0.15f * (processedBytes.toFloat() / totalWorkBytes)).coerceIn(0.65f, 0.80f)
                            )
                        )
                    }
                }

                onProgress(TransferProgress("Añadiendo Gestor_Entrevistas.apk...", 0.82f))
                val (apkZipSize, apkZipSha256) = writeFileToZipWithSha256(
                    zos = outerZos,
                    entryName = TransferManifest.ENTRY_APK,
                    sourceFile = apkFile
                ) { delta ->
                    processedBytes += delta
                    if (processedBytes - lastReportedBytes >= PROGRESS_BYTE_STEP) {
                        lastReportedBytes = processedBytes
                        onProgress(
                            TransferProgress(
                                message = "Añadiendo Gestor_Entrevistas.apk...",
                                fraction = (0.82f + 0.14f * (processedBytes.toFloat() / totalWorkBytes)).coerceIn(0.82f, 0.96f)
                            )
                        )
                    }
                }

                val transferManifest = TransferManifest(
                    formatVersion = TransferManifest.CURRENT_FORMAT_VERSION,
                    backupVersion = TransferManifest.CURRENT_BACKUP_VERSION,
                    appId = BuildConfig.APPLICATION_ID,
                    appName = context.packageManager.getApplicationLabel(context.applicationInfo).toString(),
                    appVersionName = BuildConfig.VERSION_NAME,
                    appVersionCode = BuildConfig.VERSION_CODE.toLong(),
                    createdAt = now,
                    apkSizeBytes = apkZipSize,
                    apkSha256 = apkZipSha256,
                    apkSource = if (apkSource is ApkInputSource.ManualUri) {
                        "manual_universal_apk"
                    } else {
                        "installed_single_apk"
                    },
                    backupSizeBytes = backupZipSize,
                    backupSha256 = backupZipSha256,
                    interviewCount = allInterviews.size,
                    questionCount = totalQuestions,
                    mediaCount = totalMediaCount
                )

                writeBytesToZipWithSha256(
                    zos = outerZos,
                    entryName = TransferManifest.ENTRY_MANIFEST,
                    bytes = json.encodeToString(transferManifest).toByteArray(Charsets.UTF_8)
                )
            }

            tempBackupZip.delete()
            stagedManualApk?.delete()

            onProgress(TransferProgress("Paquete listo para compartir o guardar.", 1.0f))

            Result.success(
                ExportPackageSummary(
                    packageFile = outputPackageFile,
                    totalSizeBytes = outputPackageFile.length(),
                    interviewCount = allInterviews.size,
                    questionCount = totalQuestions,
                    mediaCount = totalMediaCount,
                    apkSizeBytes = apkBytes,
                    usedManualApk = apkSource is ApkInputSource.ManualUri
                )
            )
        } catch (e: Exception) {
            tempBackupZip?.delete()
            stagedManualApk?.delete()
            outputPackageFile.delete()
            Result.failure(e)
        }
    }

    suspend fun savePackageToUri(packageFile: File, targetUri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (!packageFile.exists() || packageFile.length() <= 0L) {
                return@withContext Result.failure(IOException("El paquete ZIP ya no está disponible."))
            }
            try {
                context.contentResolver.openOutputStream(targetUri)?.use { output ->
                    BufferedInputStream(FileInputStream(packageFile), STREAM_BUFFER_SIZE).use { input ->
                        val buffer = ByteArray(STREAM_BUFFER_SIZE)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                        output.flush()
                    }
                } ?: return@withContext Result.failure(
                    IOException("No se pudo abrir el destino para guardar el archivo ZIP.")
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun importBackupFromUri(
        uri: Uri,
        onProgress: (TransferProgress) -> Unit = {}
    ): Result<ImportBackupSummary> = withContext(Dispatchers.IO) {
        val importSessionDir = File(importsDir, "import_${UUID.randomUUID()}").apply { mkdirs() }
        val stagedMediaFiles = mutableListOf<File>()
        val promotedMediaFiles = mutableListOf<File>()

        try {
            onProgress(TransferProgress("Leyendo archivo ZIP seleccionado...", 0.06f))

            val reportedSize = queryUriSize(uri)
            if (reportedSize > 0L && importSessionDir.usableSpace < reportedSize + SAFETY_FREE_BYTES) {
                return@withContext Result.failure(
                    IOException("Espacio insuficiente en el dispositivo para importar este archivo.")
                )
            }

            val inputZipFile = File(importSessionDir, "selected_input.zip")
            streamUriToFile(uri, inputZipFile)

            onProgress(TransferProgress("Verificando estructura e integridad del ZIP...", 0.18f))

            val backupZipFile: File = ZipFile(inputZipFile).use { outerZip ->
                validateZipEntriesSafety(outerZip, importSessionDir)

                val nestedBackupEntry = outerZip.getEntry(TransferManifest.ENTRY_BACKUP_ZIP)
                val directBackupDataEntry = outerZip.getEntry(BackupManifest.ENTRY_BACKUP_DATA)

                when {
                    nestedBackupEntry != null -> {
                        val manifestEntry = outerZip.getEntry(TransferManifest.ENTRY_MANIFEST)
                        val transferManifest = if (manifestEntry != null) {
                            val manifestBytes = readSmallEntryBytes(outerZip, manifestEntry, MAX_METADATA_BYTES)
                            runCatching {
                                json.decodeFromString<TransferManifest>(manifestBytes.toString(Charsets.UTF_8))
                            }.getOrElse {
                                return@withContext Result.failure(
                                    IllegalArgumentException("El archivo manifest.json del paquete está dañado.")
                                )
                            }
                        } else {
                            null
                        }

                        if (transferManifest != null &&
                            transferManifest.backupVersion !in 1..TransferManifest.CURRENT_BACKUP_VERSION
                        ) {
                            return@withContext Result.failure(
                                IllegalArgumentException(
                                    "Versión de respaldo no compatible (${transferManifest.backupVersion}). Actualiza la aplicación."
                                )
                            )
                        }

                        if (nestedBackupEntry.size > 0L &&
                            importSessionDir.usableSpace < nestedBackupEntry.size + SAFETY_FREE_BYTES
                        ) {
                            return@withContext Result.failure(
                                IOException("Espacio insuficiente para extraer el respaldo interno.")
                            )
                        }

                        val extractedBackup = File(importSessionDir, TransferManifest.ENTRY_BACKUP_ZIP)
                        val (actualSize, actualSha256) = extractEntryToFileWithSha256(
                            zipFile = outerZip,
                            entry = nestedBackupEntry,
                            targetFile = extractedBackup
                        )

                        if (transferManifest != null) {
                            if (transferManifest.backupSizeBytes > 0L &&
                                actualSize != transferManifest.backupSizeBytes
                            ) {
                                return@withContext Result.failure(
                                    IllegalStateException("Verificación fallida: el tamaño de respaldo.zip no coincide.")
                                )
                            }
                            if (transferManifest.backupSha256.isNotBlank() &&
                                !actualSha256.equals(transferManifest.backupSha256, ignoreCase = true)
                            ) {
                                return@withContext Result.failure(
                                    IllegalStateException("Verificación fallida: respaldo.zip está dañado o alterado.")
                                )
                            }
                        }
                        extractedBackup
                    }

                    directBackupDataEntry != null -> inputZipFile

                    else -> {
                        return@withContext Result.failure(
                            IllegalArgumentException(
                                "El archivo ZIP no contiene un respaldo válido (falta respaldo.zip o backup_data.json)."
                            )
                        )
                    }
                }
            }

            if (backupZipFile != inputZipFile) {
                inputZipFile.delete()
            }

            onProgress(TransferProgress("Validando respaldo y comprobando espacio...", 0.32f))

            val stagingDir = File(importSessionDir, "staged_media").apply { mkdirs() }
            val stagedAssignments = mutableListOf<StagedMediaAssignment>()

            val payload: BackupPayload = ZipFile(backupZipFile).use { backupZip ->
                validateZipEntriesSafety(backupZip, stagingDir)

                val dataEntry = backupZip.getEntry(BackupManifest.ENTRY_BACKUP_DATA)
                    ?: return@withContext Result.failure(
                        IllegalArgumentException("El respaldo no contiene backup_data.json.")
                    )

                val dataBytes = readSmallEntryBytes(backupZip, dataEntry, MAX_METADATA_BYTES)
                val actualDataSha256 = sha256Hex(dataBytes)

                val backupManifestEntry = backupZip.getEntry(BackupManifest.ENTRY_BACKUP_MANIFEST)
                if (backupManifestEntry != null) {
                    val manifestBytes = readSmallEntryBytes(backupZip, backupManifestEntry, MAX_METADATA_BYTES)
                    val backupManifest = runCatching {
                        json.decodeFromString<BackupManifest>(manifestBytes.toString(Charsets.UTF_8))
                    }.getOrElse {
                        return@withContext Result.failure(
                            IllegalArgumentException("Los metadatos del respaldo están dañados.")
                        )
                    }
                    if (backupManifest.backupVersion !in 1..TransferManifest.CURRENT_BACKUP_VERSION) {
                        return@withContext Result.failure(
                            IllegalArgumentException("Versión de respaldo no soportada.")
                        )
                    }
                    if (backupManifest.backupDataSizeBytes > 0L &&
                        dataBytes.size.toLong() != backupManifest.backupDataSizeBytes
                    ) {
                        return@withContext Result.failure(
                            IllegalStateException("El contenido de backup_data.json no coincide con el tamaño esperado.")
                        )
                    }
                    if (backupManifest.backupDataSha256.isNotBlank() &&
                        !actualDataSha256.equals(backupManifest.backupDataSha256, ignoreCase = true)
                    ) {
                        return@withContext Result.failure(
                            IllegalStateException("Error de integridad en backup_data.json.")
                        )
                    }
                }

                val parsedPayload = runCatching {
                    json.decodeFromString<BackupPayload>(dataBytes.toString(Charsets.UTF_8))
                }.getOrElse {
                    return@withContext Result.failure(
                        IllegalArgumentException("No se pudo leer la estructura del respaldo.")
                    )
                }

                if (parsedPayload.backupVersion !in 1..TransferManifest.CURRENT_BACKUP_VERSION) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Versión de respaldo no compatible.")
                    )
                }

                val allMediaRefs = parsedPayload.interviews.flatMapIndexed { iIdx, interview ->
                    interview.mediaItems.map { media -> iIdx to media }
                }

                var requiredMediaBytes = 0L
                for ((_, mediaItem) in allMediaRefs) {
                    val entryPath = mediaItem.archiveEntryPath
                    if (!entryPath.startsWith("media/images/") && !entryPath.startsWith("media/videos/")) {
                        return@withContext Result.failure(
                            SecurityException("Ruta multimedia no permitida en el respaldo: $entryPath")
                        )
                    }
                    val entry = backupZip.getEntry(entryPath)
                        ?: return@withContext Result.failure(
                            IllegalStateException("Falta un archivo multimedia referenciado en el respaldo: $entryPath")
                        )
                    val entryBytes = if (mediaItem.sizeBytes > 0L) mediaItem.sizeBytes else entry.size.coerceAtLeast(0L)
                    requiredMediaBytes += entryBytes
                }

                if (context.filesDir.usableSpace < requiredMediaBytes + SAFETY_FREE_BYTES) {
                    return@withContext Result.failure(
                        IOException("Espacio insuficiente en almacenamiento interno para restaurar los archivos multimedia.")
                    )
                }

                allMediaRefs.forEachIndexed { idx, (iIdx, mediaItem) ->
                    val progressFraction = if (allMediaRefs.isEmpty()) {
                        0.75f
                    } else {
                        0.35f + 0.45f * (idx.toFloat() / allMediaRefs.size)
                    }
                    onProgress(
                        TransferProgress(
                            message = "Extrayendo y validando multimedia (${idx + 1}/${allMediaRefs.size})...",
                            fraction = progressFraction
                        )
                    )

                    val mediaType = runCatching { MediaType.valueOf(mediaItem.type) }.getOrElse {
                        return@withContext Result.failure(
                            IllegalArgumentException("Tipo multimedia desconocido en el respaldo: ${mediaItem.type}")
                        )
                    }
                    val entry = backupZip.getEntry(mediaItem.archiveEntryPath)
                        ?: return@withContext Result.failure(
                            IllegalStateException("Archivo faltante en el respaldo: ${mediaItem.archiveEntryPath}")
                        )

                    val safeStagedFile = resolveSafeChild(stagingDir, mediaItem.archiveEntryPath)
                    safeStagedFile.parentFile?.mkdirs()
                    stagedMediaFiles.add(safeStagedFile)

                    val (actualSize, actualSha256) = extractEntryToFileWithSha256(
                        zipFile = backupZip,
                        entry = entry,
                        targetFile = safeStagedFile
                    )

                    if (actualSize <= 0L) {
                        return@withContext Result.failure(
                            IOException("Archivo multimedia vacío en el respaldo: ${mediaItem.originalName}")
                        )
                    }
                    if (mediaItem.sizeBytes > 0L && actualSize != mediaItem.sizeBytes) {
                        return@withContext Result.failure(
                            IllegalStateException("Tamaño alterado en archivo multimedia: ${mediaItem.originalName}")
                        )
                    }
                    if (mediaItem.sha256.isNotBlank() &&
                        !actualSha256.equals(mediaItem.sha256, ignoreCase = true)
                    ) {
                        return@withContext Result.failure(
                            IllegalStateException("Archivo multimedia corrupto: ${mediaItem.originalName}")
                        )
                    }

                    stagedAssignments.add(
                        StagedMediaAssignment(
                            interviewIndex = iIdx,
                            mediaType = mediaType,
                            metadata = mediaItem,
                            stagedFile = safeStagedFile
                        )
                    )
                }

                parsedPayload
            }

            onProgress(TransferProgress("Restaurando entrevistas en la base de datos...", 0.85f))

            // Promote staged files into final internal media folders before the DB transaction
            val finalAssignmentsByInterview = mutableMapOf<Int, MutableList<Pair<BackupMediaItem, File>>>()
            stagedAssignments.forEach { assignment ->
                val targetDir = if (assignment.mediaType == MediaType.IMAGE) imagesDir else videosDir
                val prefix = if (assignment.mediaType == MediaType.IMAGE) "img" else "vid"
                val ext = assignment.stagedFile.extension.ifBlank {
                    if (assignment.mediaType == MediaType.IMAGE) "jpg" else "mp4"
                }
                val finalFile = File(targetDir, "${prefix}_${UUID.randomUUID()}.$ext")
                moveOrCopyFile(assignment.stagedFile, finalFile)
                promotedMediaFiles.add(finalFile)
                finalAssignmentsByInterview
                    .getOrPut(assignment.interviewIndex) { mutableListOf() }
                    .add(assignment.metadata to finalFile)
            }

            val dao = database.interviewDao()
            val oldMediaPaths = dao.getAllInterviewsWithDetailsOnce()
                .flatMap { it.mediaItems }
                .map { it.localPath }

            val sortedRestoredInterviews = payload.interviews.sortedBy { it.sortOrder }

            // Atomic SQLite transaction: if anything fails, Room rolls back completely and existing data stays intact.
            database.withTransaction {
                val existing = dao.getAllInterviewsWithDetailsOnce()
                existing.forEach { dao.deleteInterviewById(it.interview.id) }

                sortedRestoredInterviews.forEachIndexed { idx, restored ->
                    val newInterviewId = dao.insertInterview(
                        InterviewEntity(
                            title = restored.title,
                            sortOrder = idx,
                            createdAt = restored.createdAt,
                            updatedAt = restored.updatedAt
                        )
                    )

                    val sortedQuestions = restored.questions.sortedBy { it.sortOrder }
                    if (sortedQuestions.isNotEmpty()) {
                        dao.insertQuestions(
                            sortedQuestions.mapIndexed { qIdx, q ->
                                QuestionEntity(
                                    interviewId = newInterviewId,
                                    text = q.text,
                                    sortOrder = qIdx
                                )
                            }
                        )
                    }

                    val mediaList = finalAssignmentsByInterview[idx].orEmpty()
                    mediaList.forEach { (meta, finalFile) ->
                        dao.insertOrReplaceMedia(
                            MediaEntity(
                                interviewId = newInterviewId,
                                type = MediaType.valueOf(meta.type),
                                localPath = finalFile.absolutePath,
                                mimeType = meta.mimeType,
                                originalName = meta.originalName,
                                width = meta.width,
                                height = meta.height,
                                durationMs = meta.durationMs,
                                createdAt = meta.createdAt
                            )
                        )
                    }
                }
            }

            // Transaction committed safely: clear stale session & remove replaced old media files
            preferences.clearActiveSession()
            val promotedPaths = promotedMediaFiles.map { it.absolutePath }.toSet()
            oldMediaPaths.filterNot { it in promotedPaths }.forEach { oldPath ->
                runCatching { File(oldPath).delete() }
            }

            onProgress(TransferProgress("Restauración completada.", 1.0f))

            val totalQuestions = sortedRestoredInterviews.sumOf { it.questions.size }
            val totalMedia = promotedMediaFiles.size
            Result.success(
                ImportBackupSummary(
                    interviewCount = sortedRestoredInterviews.size,
                    questionCount = totalQuestions,
                    mediaCount = totalMedia
                )
            )
        } catch (e: Exception) {
            // Roll back any newly promoted media files so current media/state remains untouched
            promotedMediaFiles.forEach { runCatching { it.delete() } }
            stagedMediaFiles.forEach { runCatching { it.delete() } }
            Result.failure(e)
        } finally {
            runCatching { importSessionDir.deleteRecursively() }
        }
    }

    private fun streamUriToFile(uri: Uri, targetFile: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                val buffer = ByteArray(STREAM_BUFFER_SIZE)
                var bytesRead: Int
                var totalBytes = 0L
                var bytesSinceSpaceCheck = 0L
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytes += bytesRead
                    bytesSinceSpaceCheck += bytesRead
                    if (bytesSinceSpaceCheck >= SPACE_CHECK_INTERVAL_BYTES) {
                        bytesSinceSpaceCheck = 0L
                        if (targetFile.parentFile?.usableSpace ?: 0L < SAFETY_FREE_BYTES) {
                            throw IOException("Espacio insuficiente durante la lectura del archivo ZIP.")
                        }
                    }
                }
                output.fd.sync()
                if (totalBytes <= 0L) {
                    throw IOException("El archivo seleccionado está vacío.")
                }
            }
        } ?: throw IOException("No se pudo abrir el archivo ZIP seleccionado.")
    }

    private fun writeFileToZipWithSha256(
        zos: ZipOutputStream,
        entryName: String,
        sourceFile: File,
        onBytesCopied: (Long) -> Unit = {}
    ): Pair<Long, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        val entry = ZipEntry(entryName).apply { time = sourceFile.lastModified() }
        zos.putNextEntry(entry)
        var totalBytes = 0L
        BufferedInputStream(FileInputStream(sourceFile), STREAM_BUFFER_SIZE).use { input ->
            val buffer = ByteArray(STREAM_BUFFER_SIZE)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                zos.write(buffer, 0, bytesRead)
                digest.update(buffer, 0, bytesRead)
                totalBytes += bytesRead
                onBytesCopied(bytesRead.toLong())
            }
        }
        zos.closeEntry()
        return totalBytes to digest.digest().toHexString()
    }

    private fun writeBytesToZipWithSha256(
        zos: ZipOutputStream,
        entryName: String,
        bytes: ByteArray
    ): Pair<Long, String> {
        val entry = ZipEntry(entryName)
        zos.putNextEntry(entry)
        zos.write(bytes)
        zos.closeEntry()
        return bytes.size.toLong() to sha256Hex(bytes)
    }

    private fun validateZipEntriesSafety(zipFile: ZipFile, rootDir: File) {
        val entries = zipFile.entries()
        var entryCount = 0
        while (entries.hasMoreElements()) {
            entryCount++
            if (entryCount > MAX_ZIP_ENTRIES) {
                throw SecurityException("El archivo ZIP contiene demasiadas entradas.")
            }
            val entry = entries.nextElement()
            if (!entry.isDirectory) {
                resolveSafeChild(rootDir, entry.name)
            }
        }
    }

    private fun resolveSafeChild(rootDir: File, entryName: String): File {
        if (entryName.isBlank() ||
            entryName.contains('\u0000') ||
            entryName.startsWith("/") ||
            entryName.startsWith("\\") ||
            entryName.contains("..")
        ) {
            throw SecurityException("Entrada ZIP no permitida: $entryName")
        }
        val rootCanonical = rootDir.canonicalFile
        val target = File(rootCanonical, entryName).canonicalFile
        val rootPrefix = rootCanonical.path.let {
            if (it.endsWith(File.separator)) it else it + File.separator
        }
        if (!target.path.startsWith(rootPrefix)) {
            throw SecurityException("Ruta fuera de destino en ZIP: $entryName")
        }
        return target
    }

    private fun readSmallEntryBytes(zipFile: ZipFile, entry: ZipEntry, maxBytes: Long): ByteArray {
        val out = ByteArrayOutputStream()
        zipFile.getInputStream(entry).use { input ->
            val buffer = ByteArray(8 * 1024)
            var bytesRead: Int
            var total = 0L
            while (input.read(buffer).also { bytesRead = it } != -1) {
                total += bytesRead
                if (total > maxBytes) {
                    throw IOException("Los metadatos del respaldo exceden el límite permitido.")
                }
                out.write(buffer, 0, bytesRead)
            }
        }
        return out.toByteArray()
    }

    private fun extractEntryToFileWithSha256(
        zipFile: ZipFile,
        entry: ZipEntry,
        targetFile: File
    ): Pair<Long, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        var totalBytes = 0L
        var bytesSinceCheck = 0L
        zipFile.getInputStream(entry).use { input ->
            BufferedOutputStream(FileOutputStream(targetFile), STREAM_BUFFER_SIZE).use { output ->
                val buffer = ByteArray(STREAM_BUFFER_SIZE)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    digest.update(buffer, 0, bytesRead)
                    totalBytes += bytesRead
                    bytesSinceCheck += bytesRead
                    if (bytesSinceCheck >= SPACE_CHECK_INTERVAL_BYTES) {
                        bytesSinceCheck = 0L
                        if (targetFile.parentFile?.usableSpace ?: 0L < SAFETY_FREE_BYTES) {
                            throw IOException("Espacio insuficiente durante la extracción del respaldo.")
                        }
                    }
                }
                output.flush()
            }
        }
        return totalBytes to digest.digest().toHexString()
    }

    private fun moveOrCopyFile(source: File, destination: File) {
        destination.parentFile?.mkdirs()
        if (source.renameTo(destination)) return
        BufferedInputStream(FileInputStream(source), STREAM_BUFFER_SIZE).use { input ->
            BufferedOutputStream(FileOutputStream(destination), STREAM_BUFFER_SIZE).use { output ->
                val buffer = ByteArray(STREAM_BUFFER_SIZE)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
                output.flush()
            }
        }
        source.delete()
    }

    private fun queryUriSize(uri: Uri): Long {
        var size = -1L
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (idx >= 0 && !cursor.isNull(idx)) {
                            size = cursor.getLong(idx)
                        }
                    }
                }
        }
        return size
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.toHexString()
    }

    private fun ByteArray.toHexString(): String =
        joinToString(separator = "") { "%02x".format(it) }

    private data class PendingMediaPack(
        val interviewIndex: Int,
        val media: com.example.data.local.entity.MediaEntity,
        val sourceFile: File,
        val entryPath: String
    )

    private data class StagedMediaAssignment(
        val interviewIndex: Int,
        val mediaType: MediaType,
        val metadata: BackupMediaItem,
        val stagedFile: File
    )

    companion object {
        private const val STREAM_BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_BYTE_STEP = 1024L * 1024L
        private const val SAFETY_FREE_BYTES = 10L * 1024L * 1024L
        private const val SPACE_CHECK_INTERVAL_BYTES = 4L * 1024L * 1024L
        private const val MAX_METADATA_BYTES = 16L * 1024L * 1024L
        private const val MAX_ZIP_ENTRIES = 10_000
        const val TRANSFER_PACKAGE_FILENAME = "KennethYT_App_y_Respaldo.zip"
    }
}
