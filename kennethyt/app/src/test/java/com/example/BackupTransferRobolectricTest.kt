package com.example

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.AndroidApkArchiveValidator
import com.example.data.backup.ApkArchiveValidator
import com.example.data.backup.ApkInputSource
import com.example.data.backup.ApkResolution
import com.example.data.backup.ApkResolver
import com.example.data.backup.BackupTransferManager
import com.example.data.backup.TransferManifest
import com.example.data.local.database.KennethDatabase
import com.example.data.media.MediaStorageManager
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.LocalInterviewRepository
import com.example.domain.model.MediaType
import com.example.navigation.KennethNavHost
import com.example.ui.home.HomeViewModel
import com.example.ui.home.TransferUiState
import com.example.ui.theme.KennethYTTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupTransferRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var db: KennethDatabase
    private lateinit var preferences: UserPreferencesRepository
    private lateinit var repository: LocalInterviewRepository
    private lateinit var dataStoreScope: CoroutineScope

    private var origSourceDir: String? = null
    private var origSplitSourceDirs: Array<String>? = null
    private var origSplitNames: Array<String>? = null

    private val json = Json { ignoreUnknownKeys = true }

    private val fakeValidApkValidator = object : ApkArchiveValidator {
        override fun validateStandaloneApk(context: Context, apkFile: File): Result<Unit> {
            return if (apkFile.exists() && apkFile.length() > 4L) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalArgumentException("Invalid test APK"))
            }
        }
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        origSourceDir = context.applicationInfo.sourceDir
        origSplitSourceDirs = context.applicationInfo.splitSourceDirs
        origSplitNames = context.applicationInfo.splitNames
        db = Room.inMemoryDatabaseBuilder(context, KennethDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dataStoreScope = CoroutineScope(Dispatchers.IO + Job())
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(tempFolder.root, "backup_test_prefs.preferences_pb") }
        )
        preferences = UserPreferencesRepository(testDataStore)
        repository = LocalInterviewRepository(
            dao = db.interviewDao(),
            mediaStorage = MediaStorageManager(context),
            preferences = preferences
        )
    }

    @After
    fun tearDown() {
        context.applicationInfo.sourceDir = origSourceDir
        context.applicationInfo.splitSourceDirs = origSplitSourceDirs
        context.applicationInfo.splitNames = origSplitNames
        db.close()
        dataStoreScope.cancel()
        File(context.filesDir, "interview_media").deleteRecursively()
        File(context.cacheDir, "transfer_exports").deleteRecursively()
        File(context.cacheDir, "transfer_imports").deleteRecursively()
    }

    @Test
    fun `apk resolver rejects split apks and sibling split files without exporting base apk`() = runBlocking {
        val splitDir = tempFolder.newFolder("split_install")
        val baseApk = createMockZipApk(File(splitDir, "base.apk"), includeDex = true, includeManifest = true)
        val splitApk = createMockZipApk(File(splitDir, "split_config.arm64_v8a.apk"), includeDex = false, includeManifest = true)

        // Case 1: splitSourceDirs populated
        context.applicationInfo.sourceDir = baseApk.absolutePath
        context.applicationInfo.splitSourceDirs = arrayOf(splitApk.absolutePath)
        val resolver = ApkResolver(context, fakeValidApkValidator)
        val res1 = resolver.resolveInstalledApk()
        assertTrue(res1 is ApkResolution.SplitOrUnavailable)

        // Case 2: splitSourceDirs null, but sibling split_*.apk present beside base.apk
        context.applicationInfo.splitSourceDirs = null
        context.applicationInfo.splitNames = null
        val res2 = resolver.resolveInstalledApk()
        assertTrue(res2 is ApkResolution.SplitOrUnavailable)

        // Case 3: clean single APK in isolated dir succeeds
        val singleDir = tempFolder.newFolder("single_install")
        val singleApk = createMockZipApk(File(singleDir, "single.apk"), includeDex = true, includeManifest = true)
        context.applicationInfo.sourceDir = singleApk.absolutePath
        val res3 = resolver.resolveInstalledApk()
        assertTrue(res3 is ApkResolution.SingleInstalledApk)
        assertEquals(singleApk.absolutePath, (res3 as ApkResolution.SingleInstalledApk).file.absolutePath)
    }

    @Test
    fun `android apk validator rejects non-apk and incomplete split module zips`() {
        val nonZip = File(tempFolder.root, "not_an_apk.apk").apply {
            writeText("plain text content")
        }
        assertTrue(AndroidApkArchiveValidator.validateStandaloneApk(context, nonZip).isFailure)

        val splitModuleZip = createMockZipApk(
            File(tempFolder.root, "split_missing_dex.apk"),
            includeDex = false,
            includeManifest = true
        )
        assertTrue(AndroidApkArchiveValidator.validateStandaloneApk(context, splitModuleZip).isFailure)
    }

    @Test
    fun `full transfer package export and import restores interviews questions order and media`() = runBlocking {
        // Seed two interviews with custom order, questions, and image + video files
        val id1 = repository.createInterview("Entrevista Uno").getOrThrow()
        val id2 = repository.createInterview("Entrevista Dos").getOrThrow()
        repository.addQuestion(id1, "¿Pregunta A?").getOrThrow()
        repository.addQuestion(id1, "¿Pregunta B?").getOrThrow()
        repository.addQuestion(id2, "¿Pregunta C?").getOrThrow()
        // Reorder so Entrevista Dos is first
        repository.reorderInterviews(listOf(id2, id1))

        val pngFile = File(tempFolder.root, "sample_photo.png")
        val bmp = android.graphics.Bitmap.createBitmap(120, 90, android.graphics.Bitmap.Config.ARGB_8888)
        FileOutputStream(pngFile).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        val imgBytes = pngFile.readBytes()

        repository.saveMediaFromUri(id1, Uri.fromFile(pngFile), MediaType.IMAGE).getOrThrow()

        val vidBytes = ByteArray(192 * 1024) { (it % 251).toByte() }
        val videosDir = File(context.filesDir, "interview_media/videos").apply { mkdirs() }
        val storedVidFile = File(videosDir, "vid_test_clip.mp4").apply { writeBytes(vidBytes) }
        db.interviewDao().insertOrReplaceMedia(
            com.example.data.local.entity.MediaEntity(
                interviewId = id1,
                type = MediaType.VIDEO,
                localPath = storedVidFile.absolutePath,
                mimeType = "video/mp4",
                originalName = "sample_clip.mp4",
                width = 1920,
                height = 1080,
                durationMs = 12_500L,
                createdAt = System.currentTimeMillis()
            )
        )

        val standaloneApk = createMockZipApk(
            File(tempFolder.root, "universal_app.apk"),
            includeDex = true,
            includeManifest = true
        )
        val manager = BackupTransferManager(
            context = context,
            database = db,
            preferences = preferences,
            apkResolver = ApkResolver(context, fakeValidApkValidator)
        )

        val exportSummary = manager.buildTransferPackage(
            apkSource = ApkInputSource.ManualUri(Uri.fromFile(standaloneApk))
        ).getOrThrow()

        assertEquals(2, exportSummary.interviewCount)
        assertEquals(3, exportSummary.questionCount)
        assertEquals(2, exportSummary.mediaCount)
        assertTrue(exportSummary.usedManualApk)
        assertTrue(exportSummary.packageFile.exists())

        // Verify outer ZIP structure: Gestor_Entrevistas.apk, respaldo.zip, manifest.json
        val exportedCopy = File(tempFolder.root, "saved_transfer.zip")
        manager.savePackageToUri(exportSummary.packageFile, Uri.fromFile(exportedCopy)).getOrThrow()
        assertTrue(exportedCopy.length() > 0L)

        var standaloneBackupCopy: File? = null
        ZipFile(exportedCopy).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toSet()
            assertTrue(TransferManifest.ENTRY_APK in names)
            assertTrue(TransferManifest.ENTRY_BACKUP_ZIP in names)
            assertTrue(TransferManifest.ENTRY_MANIFEST in names)

            val manifestJson = zip.getInputStream(zip.getEntry(TransferManifest.ENTRY_MANIFEST))
                .readBytes().toString(Charsets.UTF_8)
            val manifest = json.decodeFromString<TransferManifest>(manifestJson)
            assertEquals(TransferManifest.CURRENT_FORMAT_VERSION, manifest.formatVersion)
            assertEquals(TransferManifest.CURRENT_BACKUP_VERSION, manifest.backupVersion)
            assertEquals(2, manifest.interviewCount)
            assertEquals(3, manifest.questionCount)
            assertEquals(2, manifest.mediaCount)
            assertTrue(manifest.apkSha256.length == 64)
            assertTrue(manifest.backupSha256.length == 64)

            // Also extract inner respaldo.zip for the standalone test below
            standaloneBackupCopy = File(tempFolder.root, "standalone_respaldo.zip").apply {
                zip.getInputStream(zip.getEntry(TransferManifest.ENTRY_BACKUP_ZIP)).use { input ->
                    outputStream().use { output -> input.copyTo(output) }
                }
            }
        }

        // Wipe database & old media to simulate installing on a second phone
        repository.deleteInterview(id1)
        repository.deleteInterview(id2)
        assertTrue(repository.interviews.first().isEmpty())

        // 1. Import from the FULL transferable ZIP
        val importSummary = manager.importBackupFromUri(Uri.fromFile(exportedCopy)).getOrThrow()
        assertEquals(2, importSummary.interviewCount)
        assertEquals(3, importSummary.questionCount)
        assertEquals(2, importSummary.mediaCount)

        val restored = repository.interviews.first()
        assertEquals(2, restored.size)
        assertEquals("Entrevista Dos", restored[0].title)
        assertEquals(listOf("¿Pregunta C?"), restored[0].questions.map { it.text })
        assertEquals("Entrevista Uno", restored[1].title)
        assertEquals(listOf("¿Pregunta A?", "¿Pregunta B?"), restored[1].questions.map { it.text })

        val restoredImg = File(restored[1].imageMedia!!.localPath)
        val restoredVid = File(restored[1].videoMedia!!.localPath)
        assertTrue(restoredImg.exists())
        assertTrue(restoredVid.exists())
        assertArrayEquals(imgBytes, restoredImg.readBytes())
        assertArrayEquals(vidBytes, restoredVid.readBytes())
        assertEquals(12_500L, restored[1].videoMedia!!.durationMs)

        // 2. Also verify importing standalone respaldo.zip directly
        repository.deleteInterview(restored[0].id)
        repository.deleteInterview(restored[1].id)
        val standaloneImport = manager.importBackupFromUri(Uri.fromFile(standaloneBackupCopy!!)).getOrThrow()
        assertEquals(2, standaloneImport.interviewCount)
        assertEquals(listOf("Entrevista Dos", "Entrevista Uno"), repository.interviews.first().map { it.title })
    }

    @Test
    fun `import rejects zip slip and corrupted checksums while preserving existing data`() = runBlocking {
        // Current data that MUST survive failed imports
        val existingId = repository.createInterview("Entrevista Protegida").getOrThrow()
        repository.addQuestion(existingId, "¿Se conserva esta pregunta?").getOrThrow()
        val existingImg = File(tempFolder.root, "keep_me.png")
        val keepBmp = android.graphics.Bitmap.createBitmap(80, 60, android.graphics.Bitmap.Config.ARGB_8888)
        FileOutputStream(existingImg).use { keepBmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        keepBmp.recycle()
        val existingBytes = existingImg.readBytes()
        repository.saveMediaFromUri(existingId, Uri.fromFile(existingImg), MediaType.IMAGE).getOrThrow()
        val beforeImport = repository.interviews.first().single()

        val manager = BackupTransferManager(
            context = context,
            database = db,
            preferences = preferences,
            apkResolver = ApkResolver(context, fakeValidApkValidator)
        )

        // Malicious Zip Slip archive
        val zipSlipFile = File(tempFolder.root, "malicious_zip_slip.zip")
        ZipOutputStream(FileOutputStream(zipSlipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("../../evil.txt"))
            zos.write("malicious".toByteArray())
            zos.closeEntry()
        }
        val slipResult = manager.importBackupFromUri(Uri.fromFile(zipSlipFile))
        assertTrue(slipResult.isFailure)

        // Build a valid transfer ZIP, then corrupt backup_data.json / respaldo.zip checksum mismatch
        val apkFile = createMockZipApk(File(tempFolder.root, "ok.apk"), includeDex = true, includeManifest = true)
        val export = manager.buildTransferPackage(ApkInputSource.ManualUri(Uri.fromFile(apkFile))).getOrThrow()

        val corruptedOuterZip = File(tempFolder.root, "corrupted_transfer.zip")
        ZipFile(export.packageFile).use { srcZip ->
            ZipOutputStream(FileOutputStream(corruptedOuterZip)).use { zos ->
                srcZip.entries().asSequence().forEach { entry ->
                    zos.putNextEntry(ZipEntry(entry.name))
                    if (entry.name == TransferManifest.ENTRY_BACKUP_ZIP) {
                        // Replace respaldo.zip bytes with tampered bytes so manifest SHA-256 check fails
                        zos.write("corrupted_zip_payload".toByteArray())
                    } else {
                        srcZip.getInputStream(entry).use { it.copyTo(zos) }
                    }
                    zos.closeEntry()
                }
            }
        }

        val corruptResult = manager.importBackupFromUri(Uri.fromFile(corruptedOuterZip))
        assertTrue(corruptResult.isFailure)

        // Existing interviews, questions, and media file must be untouched
        val afterFailure = repository.interviews.first()
        assertEquals(1, afterFailure.size)
        assertEquals("Entrevista Protegida", afterFailure[0].title)
        assertEquals(listOf("¿Se conserva esta pregunta?"), afterFailure[0].questions.map { it.text })
        val preservedMediaFile = File(beforeImport.imageMedia!!.localPath)
        assertTrue(preservedMediaFile.exists())
        assertArrayEquals(byteArrayOf(42, 43, 44, 45), preservedMediaFile.readBytes())
        assertFalse(File(tempFolder.root.parentFile, "evil.txt").exists())
    }

    @Test
    fun `home settings bottom sheet exposes share app and data and import buttons`() {
        val app = context.applicationContext as KennethApplication
        val manager = BackupTransferManager(
            context = context,
            database = db,
            preferences = preferences,
            apkResolver = ApkResolver(context, fakeValidApkValidator)
        )
        val container = AppContainer(
            application = app,
            customRepository = repository,
            customPreferences = preferences,
            customDatabase = db
        )

        composeRule.setContent {
            KennethYTTheme {
                KennethNavHost(container = container)
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Gestiona tus").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("home_menu_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("share_app_and_data_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("import_my_data_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Compartir aplicación y datos").assertIsDisplayed()
        composeRule.onNodeWithText("Importar mis datos").assertIsDisplayed()
    }

    @Test
    fun `home viewmodel prompts for manual apk on split install and builds export when selected`() {
        val splitDir = tempFolder.newFolder("vm_split")
        val baseApk = createMockZipApk(File(splitDir, "base.apk"), includeDex = true, includeManifest = true)
        context.applicationInfo.sourceDir = baseApk.absolutePath
        context.applicationInfo.splitSourceDirs = arrayOf(File(splitDir, "split_config.apk").absolutePath)

        val manager = BackupTransferManager(
            context = context,
            database = db,
            preferences = preferences,
            apkResolver = ApkResolver(context, fakeValidApkValidator)
        )
        val vm = HomeViewModel(
            repository = repository,
            preferences = preferences,
            backupTransferManager = manager
        )

        val scope = CoroutineScope(Dispatchers.Main.immediate + Job())
        scope.launch { vm.uiState.collect {} }

        try {
            vm.startShareAppAndData()
            awaitCondition { vm.uiState.value.transferState is TransferUiState.NeedsManualApk }

            val manualApk = createMockZipApk(File(tempFolder.root, "manual_universal.apk"), includeDex = true, includeManifest = true)
            vm.onManualApkSelected(Uri.fromFile(manualApk))
            awaitCondition { vm.uiState.value.transferState is TransferUiState.ExportReady }

            val ready = vm.uiState.value.transferState as TransferUiState.ExportReady
            assertTrue(ready.summary.usedManualApk)
            assertTrue(ready.summary.packageFile.exists())
        } finally {
            scope.cancel()
        }
    }

    private fun createMockZipApk(file: File, includeDex: Boolean, includeManifest: Boolean): File {
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            if (includeManifest) {
                zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
                zos.write("mock-manifest-bytes".toByteArray())
                zos.closeEntry()
            }
            if (includeDex) {
                zos.putNextEntry(ZipEntry("classes.dex"))
                zos.write("mock-dex-bytes".toByteArray())
                zos.closeEntry()
            }
        }
        return file
    }

    private fun awaitCondition(timeoutMs: Long = 8_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate()) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Timed out waiting for condition")
            }
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(15)
        }
    }
}
