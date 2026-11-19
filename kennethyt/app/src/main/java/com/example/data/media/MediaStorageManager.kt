package com.example.data.media

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.exifinterface.media.ExifInterface
import com.example.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

data class ImportedMediaMetadata(
    val localPath: String,
    val mimeType: String,
    val originalName: String,
    val width: Int,
    val height: Int,
    val durationMs: Long?
)

class MediaStorageManager(private val context: Context) {

    private val imagesDir: File
        get() = File(context.filesDir, "interview_media/images").apply { mkdirs() }

    private val videosDir: File
        get() = File(context.filesDir, "interview_media/videos").apply { mkdirs() }

    suspend fun importMedia(uri: Uri, expectedType: MediaType): Result<ImportedMediaMetadata> =
        withContext(Dispatchers.IO) {
            var tempFile: File? = null
            try {
                val resolver = context.contentResolver
                val resolvedMime = resolveMimeType(resolver, uri, expectedType)
                    ?: return@withContext Result.failure(
                        IllegalArgumentException("No se pudo verificar el formato del archivo seleccionado.")
                    )

                if (!isSupportedMime(resolvedMime, expectedType)) {
                    val expectedLabel = if (expectedType == MediaType.IMAGE) "imagen" else "video"
                    return@withContext Result.failure(
                        IllegalArgumentException("El archivo seleccionado no es un formato de $expectedLabel compatible ($resolvedMime).")
                    )
                }

                val (displayName, reportedSize) = queryFileMeta(resolver, uri)
                val targetDir = if (expectedType == MediaType.IMAGE) imagesDir else videosDir

                val availableBytes = targetDir.usableSpace
                val requiredBytes = if (reportedSize > 0L) reportedSize + SAFETY_FREE_BYTES else SAFETY_FREE_BYTES
                if (availableBytes < requiredBytes) {
                    return@withContext Result.failure(
                        IOException("Espacio insuficiente en el almacenamiento interno del teléfono.")
                    )
                }

                val extension = extensionFromMimeOrName(resolvedMime, displayName, expectedType)
                tempFile = File(targetDir, "tmp_${UUID.randomUUID()}.tmp")

                resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(STREAM_BUFFER_SIZE)
                        var bytesRead: Int
                        var totalBytes = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytes += bytesRead
                        }
                        output.fd.sync()
                        if (totalBytes <= 0L) {
                            return@withContext Result.failure(
                                IOException("El archivo seleccionado está vacío.")
                            )
                        }
                    }
                } ?: return@withContext Result.failure(
                    IOException("No se pudo leer el archivo seleccionado.")
                )

                val validatedMeta = when (expectedType) {
                    MediaType.IMAGE -> extractAndValidateImage(tempFile)
                    MediaType.VIDEO -> extractAndValidateVideo(tempFile)
                }.getOrElse { error ->
                    tempFile.delete()
                    return@withContext Result.failure(error)
                }

                val prefix = if (expectedType == MediaType.IMAGE) "img" else "vid"
                val finalFile = File(targetDir, "${prefix}_${UUID.randomUUID()}.$extension")
                if (!tempFile.renameTo(finalFile)) {
                    tempFile.copyTo(finalFile, overwrite = true)
                    tempFile.delete()
                }

                Result.success(
                    ImportedMediaMetadata(
                        localPath = finalFile.absolutePath,
                        mimeType = resolvedMime,
                        originalName = displayName,
                        width = validatedMeta.width,
                        height = validatedMeta.height,
                        durationMs = validatedMeta.durationMs
                    )
                )
            } catch (e: SecurityException) {
                tempFile?.delete()
                Result.failure(
                    SecurityException("Sin permiso temporal para leer el archivo seleccionado.", e)
                )
            } catch (e: Exception) {
                tempFile?.delete()
                Result.failure(e)
            }
        }

    suspend fun copyMediaFile(sourcePath: String, type: MediaType): String? =
        withContext(Dispatchers.IO) {
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists() || !sourceFile.isFile) return@withContext null
            val targetDir = if (type == MediaType.IMAGE) imagesDir else videosDir
            if (targetDir.usableSpace < sourceFile.length() + SAFETY_FREE_BYTES) return@withContext null

            val ext = sourceFile.extension.ifBlank {
                if (type == MediaType.IMAGE) "jpg" else "mp4"
            }
            val prefix = if (type == MediaType.IMAGE) "img" else "vid"
            val destFile = File(targetDir, "${prefix}_${UUID.randomUUID()}.$ext")
            return@withContext try {
                sourceFile.inputStream().use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output, STREAM_BUFFER_SIZE)
                        output.fd.sync()
                    }
                }
                destFile.absolutePath
            } catch (_: Exception) {
                destFile.delete()
                null
            }
        }

    suspend fun deleteFileSafely(path: String?) {
        withContext(Dispatchers.IO) {
            if (path.isNullOrBlank()) return@withContext
            runCatching {
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }

    suspend fun cleanupOrphanTempFiles() = withContext(Dispatchers.IO) {
        listOf(imagesDir, videosDir).forEach { dir ->
            dir.listFiles()?.filter { it.name.startsWith("tmp_") && it.name.endsWith(".tmp") }
                ?.forEach { it.delete() }
        }
    }

    private fun resolveMimeType(
        resolver: ContentResolver,
        uri: Uri,
        expectedType: MediaType
    ): String? {
        val fromResolver = resolver.getType(uri)?.lowercase()
        if (!fromResolver.isNullOrBlank()) return fromResolver

        val ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString())?.lowercase()
        if (!ext.isNullOrBlank()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it.lowercase() }
        }
        return if (expectedType == MediaType.IMAGE) "image/jpeg" else "video/mp4"
    }

    private fun isSupportedMime(mime: String, expectedType: MediaType): Boolean {
        return when (expectedType) {
            MediaType.IMAGE -> mime.startsWith("image/")
            MediaType.VIDEO -> mime.startsWith("video/")
        }
    }

    private fun queryFileMeta(resolver: ContentResolver, uri: Uri): Pair<String, Long> {
        var name = "archivo"
        var size = -1L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0) {
                            cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { name = it }
                        }
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                            size = cursor.getLong(sizeIdx)
                        }
                    }
                }
        }
        return name to size
    }

    private fun extensionFromMimeOrName(
        mime: String,
        originalName: String,
        expectedType: MediaType
    ): String {
        val fromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
        if (!fromMime.isNullOrBlank()) return fromMime.lowercase()
        val fromName = originalName.substringAfterLast('.', "")
            .lowercase()
            .filter { it.isLetterOrDigit() }
        if (fromName.isNotBlank() && fromName.length <= 5) return fromName
        return if (expectedType == MediaType.IMAGE) "jpg" else "mp4"
    }

    private data class ValidatedDimensions(
        val width: Int,
        val height: Int,
        val durationMs: Long?
    )

    private fun extractAndValidateImage(file: File): Result<ValidatedDimensions> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val rawWidth = bounds.outWidth
        val rawHeight = bounds.outHeight
        if (rawWidth <= 0 || rawHeight <= 0) {
            return Result.failure(IllegalArgumentException("La imagen seleccionada está dañada o no es válida."))
        }

        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val swapDimensions = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
            orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            orientation == ExifInterface.ORIENTATION_TRANSVERSE

        val width = if (swapDimensions) rawHeight else rawWidth
        val height = if (swapDimensions) rawWidth else rawHeight
        return Result.success(ValidatedDimensions(width = width, height = height, durationMs = null))
    }

    private fun extractAndValidateVideo(file: File): Result<ValidatedDimensions> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val hasVideo = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)
            if (hasVideo != "yes") {
                return Result.failure(IllegalArgumentException("El archivo seleccionado no contiene una pista de video válida."))
            }
            val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()

            if (rawWidth <= 0 || rawHeight <= 0) {
                return Result.failure(IllegalArgumentException("No se pudieron leer las dimensiones del video."))
            }

            val swap = rotation == 90 || rotation == 270
            val width = if (swap) rawHeight else rawWidth
            val height = if (swap) rawWidth else rawHeight
            Result.success(ValidatedDimensions(width = width, height = height, durationMs = duration))
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("El archivo de video está dañado o su formato no es compatible.", e))
        } finally {
            runCatching { retriever.release() }
        }
    }

    companion object {
        private const val STREAM_BUFFER_SIZE = 64 * 1024
        private const val SAFETY_FREE_BYTES = 5L * 1024L * 1024L
    }
}
