package com.example.data.backup

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipFile

interface ApkArchiveValidator {
    fun validateStandaloneApk(context: Context, apkFile: File): Result<Unit>
}

object AndroidApkArchiveValidator : ApkArchiveValidator {
    override fun validateStandaloneApk(context: Context, apkFile: File): Result<Unit> {
        if (!apkFile.exists() || !apkFile.isFile || !apkFile.canRead() || apkFile.length() <= 4L) {
            return Result.failure(IOException("El archivo APK no existe o está vacío."))
        }

        // Verify ZIP header magic bytes (PK\x03\x04)
        val header = ByteArray(4)
        val readCount = apkFile.inputStream().use { it.read(header) }
        if (readCount < 4 ||
            header[0] != 0x50.toByte() ||
            header[1] != 0x4B.toByte() ||
            header[2] != 0x03.toByte() ||
            header[3] != 0x04.toByte()
        ) {
            return Result.failure(IllegalArgumentException("El archivo seleccionado no es un APK válido."))
        }

        var hasManifest = false
        var hasDex = false
        try {
            ZipFile(apkFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val name = entries.nextElement().name
                    if (name == "AndroidManifest.xml") hasManifest = true
                    if (name.startsWith("classes") && name.endsWith(".dex")) hasDex = true
                    if (hasManifest && hasDex) break
                }
            }
        } catch (e: Exception) {
            return Result.failure(IllegalArgumentException("El archivo APK está dañado o no se puede leer.", e))
        }

        if (!hasManifest || !hasDex) {
            return Result.failure(
                IllegalArgumentException(
                    "El APK seleccionado está incompleto o es un módulo dividido (split APK). Selecciona un APK universal."
                )
            )
        }

        val packageInfo = getPackageArchiveInfoCompat(context.packageManager, apkFile.absolutePath)
            ?: return Result.failure(
                IllegalArgumentException("Android no pudo verificar el manifiesto del APK seleccionado.")
            )

        if (!packageInfo.splitNames.isNullOrEmpty()) {
            return Result.failure(
                IllegalArgumentException(
                    "El archivo seleccionado corresponde a un APK dividido. Selecciona un APK universal completo."
                )
            )
        }

        if (packageInfo.packageName != context.packageName) {
            return Result.failure(
                IllegalArgumentException(
                    "El APK seleccionado pertenece a otra aplicación (${packageInfo.packageName}). Selecciona el APK de esta app."
                )
            )
        }

        return Result.success(Unit)
    }

    @Suppress("DEPRECATION")
    private fun getPackageArchiveInfoCompat(pm: PackageManager, archivePath: String): PackageInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(archivePath, PackageManager.PackageInfoFlags.of(0))
        } else {
            pm.getPackageArchiveInfo(archivePath, 0)
        }
    }
}

class ApkResolver(
    private val context: Context,
    private val validator: ApkArchiveValidator = AndroidApkArchiveValidator
) {

    suspend fun resolveInstalledApk(): ApkResolution = withContext(Dispatchers.IO) {
        val appInfo = context.applicationInfo

        // Explicitly reject split APK installations so base.apk is NEVER exported as standalone.
        if (!appInfo.splitSourceDirs.isNullOrEmpty() || !appInfo.splitNames.isNullOrEmpty()) {
            return@withContext ApkResolution.SplitOrUnavailable(
                "Esta instalación utiliza APKs divididos (split APKs). Para evitar exportar un instalador incompleto, selecciona manualmente un APK universal."
            )
        }

        val sourceDir = appInfo.sourceDir
        if (sourceDir.isNullOrBlank()) {
            return@withContext ApkResolution.SplitOrUnavailable(
                "No se encontró la ruta del APK instalado. Selecciona manualmente un APK universal."
            )
        }

        val apkFile = File(sourceDir)
        if (!apkFile.exists() || !apkFile.isFile || !apkFile.canRead() || apkFile.length() <= 0L) {
            return@withContext ApkResolution.SplitOrUnavailable(
                "No se puede leer el APK instalado desde el sistema. Selecciona manualmente un APK universal."
            )
        }

        val siblingApks = apkFile.parentFile?.listFiles()
            ?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
            .orEmpty()

        val hasSplitSiblings = siblingApks.any { it.name.startsWith("split_", ignoreCase = true) } ||
            (apkFile.name.equals("base.apk", ignoreCase = true) && siblingApks.size > 1)
        if (hasSplitSiblings) {
            return@withContext ApkResolution.SplitOrUnavailable(
                "Se detectaron módulos split_*.apk junto a base.apk. Selecciona manualmente un APK universal completo."
            )
        }

        validator.validateStandaloneApk(context, apkFile).fold(
            onSuccess = { ApkResolution.SingleInstalledApk(apkFile) },
            onFailure = { error ->
                ApkResolution.SplitOrUnavailable(
                    error.message ?: "No se pudo validar un APK único independiente. Selecciona un APK universal."
                )
            }
        )
    }

    suspend fun stageAndValidateManualApk(uri: Uri, targetStagingFile: File): Result<File> =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val reportedSize = querySize(uri)
                val parentDir = targetStagingFile.parentFile?.apply { mkdirs() } ?: targetStagingFile
                if (reportedSize > 0L && parentDir.usableSpace < reportedSize + SAFETY_FREE_BYTES) {
                    return@withContext Result.failure(
                        IOException("Espacio insuficiente para procesar el APK seleccionado.")
                    )
                }

                resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetStagingFile).use { output ->
                        val buffer = ByteArray(STREAM_BUFFER_SIZE)
                        var bytesRead: Int
                        var totalBytes = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytes += bytesRead
                        }
                        output.fd.sync()
                        if (totalBytes <= 0L) {
                            targetStagingFile.delete()
                            return@withContext Result.failure(
                                IOException("El archivo APK seleccionado está vacío.")
                            )
                        }
                    }
                } ?: return@withContext Result.failure(
                    IOException("No se pudo abrir el APK seleccionado.")
                )

                validator.validateStandaloneApk(context, targetStagingFile).getOrElse { error ->
                    targetStagingFile.delete()
                    return@withContext Result.failure(error)
                }

                Result.success(targetStagingFile)
            } catch (e: SecurityException) {
                targetStagingFile.delete()
                Result.failure(SecurityException("Sin permiso para leer el APK seleccionado.", e))
            } catch (e: Exception) {
                targetStagingFile.delete()
                Result.failure(e)
            }
        }

    private fun querySize(uri: Uri): Long {
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

    companion object {
        private const val STREAM_BUFFER_SIZE = 64 * 1024
        private const val SAFETY_FREE_BYTES = 10L * 1024L * 1024L
    }
}
