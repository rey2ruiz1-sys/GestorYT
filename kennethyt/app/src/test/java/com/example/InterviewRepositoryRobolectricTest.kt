package com.example

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.database.KennethDatabase
import com.example.data.local.entity.InterviewEntity
import com.example.data.local.entity.QuestionEntity
import com.example.data.media.MediaStorageManager
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.DefaultInterviewSeed
import com.example.data.repository.LocalInterviewRepository
import com.example.domain.model.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InterviewRepositoryRobolectricTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var db: KennethDatabase
    private lateinit var mediaStorage: MediaStorageManager
    private lateinit var preferences: UserPreferencesRepository
    private lateinit var repository: LocalInterviewRepository
    private lateinit var dataStoreScope: CoroutineScope

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, KennethDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        mediaStorage = MediaStorageManager(context)
        dataStoreScope = CoroutineScope(Dispatchers.IO + Job())
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(tempFolder.root, "test_prefs.preferences_pb") }
        )
        preferences = UserPreferencesRepository(testDataStore)
        repository = LocalInterviewRepository(
            dao = db.interviewDao(),
            mediaStorage = mediaStorage,
            preferences = preferences
        )
    }

    @After
    fun tearDown() {
        db.close()
        dataStoreScope.cancel()
        File(context.filesDir, "interview_media").deleteRecursively()
    }

    @Test
    fun `ensureSeeded creates 12 real interviews and all their real scripts`() = runBlocking {
        repository.ensureSeeded()
        val list = repository.interviews.first()
        assertEquals(12, list.size)
        assertEquals(DefaultInterviewSeed.realInterviews.map { it.title }, list.map { it.title })

        DefaultInterviewSeed.realInterviews.forEachIndexed { index, spec ->
            assertEquals(
                "Questions mismatch at interview ${index + 1} (${spec.title})",
                spec.questions,
                list[index].questions.map { it.text }
            )
        }

        // Calling ensureSeeded again is idempotent and does not duplicate
        repository.ensureSeeded()
        assertEquals(12, repository.interviews.first().size)
    }

    @Test
    fun `v1 to v2 controlled migration updates example scripts while preserving media and user customizations`() = runBlocking {
        // Simulate an existing v1 install with sample titles, 1 imported image, 1 custom title, and 1 custom interview
        val now = 1000L
        val dao = db.interviewDao()
        val ids = dao.insertInterviews(
            DefaultInterviewSeed.v1ExampleTitles.mapIndexed { idx, title ->
                val actualTitle = if (idx == 4) "Mi entrevista personalizada" else title
                InterviewEntity(title = actualTitle, sortOrder = idx, createdAt = now, updatedAt = now)
            }
        )
        dao.insertQuestions(
            DefaultInterviewSeed.v1FirstInterviewQuestions.mapIndexed { idx, q ->
                QuestionEntity(interviewId = ids.first(), text = q, sortOrder = idx)
            }
        )
        // Custom user question on interview 5
        dao.insertQuestion(QuestionEntity(interviewId = ids[4], text = "Pregunta privada mía", sortOrder = 0))

        // Import image onto interview 3 ("Ubicación del mapa" in v1 -> "País del juego" in v2)
        val tempPng = createTempPng(width = 120, height = 120, name = "v1_kept.png")
        val keptMedia = repository.saveMediaFromUri(ids[2], Uri.fromFile(tempPng), MediaType.IMAGE).getOrThrow()

        // Mark prefs as v1
        preferences.setSeedContentVersion(1)

        // Execute migration
        repository.ensureSeeded()

        val upgraded = repository.interviews.first()
        assertEquals(12, upgraded.size)

        // Interview 01 keeps ID and updates question 1 from G-TA -> GTA
        assertEquals(ids[0], upgraded[0].id)
        assertEquals("¿Qué significan las siglas GTA?", upgraded[0].questions.first().text)

        // Interview 03 ("Ubicación del mapa" -> "País del juego") keeps ID and preserves imported image
        assertEquals(ids[2], upgraded[2].id)
        assertEquals("País del juego", upgraded[2].title)
        assertEquals(keptMedia.localPath, upgraded[2].imageMedia?.localPath)
        assertTrue(File(keptMedia.localPath).exists())
        assertEquals(5, upgraded[2].questions.size)

        // Interview 05 with custom title and custom question is NOT overwritten
        assertEquals("Mi entrevista personalizada", upgraded[4].title)
        assertEquals(listOf("Pregunta privada mía"), upgraded[4].questions.map { it.text })

        // Subsequent manual edits persist and are never overwritten on restart
        repository.updateInterviewTitle(ids[2], "País personalizado").getOrThrow()
        repository.ensureSeeded()
        assertEquals("País personalizado", repository.observeInterview(ids[2]).first()?.title)
    }

    @Test
    fun `interview crud reordering and questions persist properly`() = runBlocking {
        repository.ensureSeeded()
        val createdId = repository.createInterview("Pregunta extra").getOrThrow()
        assertEquals(13, repository.interviews.first().size)

        repository.updateInterviewTitle(createdId, "Título editado").getOrThrow()
        val edited = repository.observeInterview(createdId).first()
        assertEquals("Título editado", edited?.title)

        // Add, edit, reorder questions
        val q1 = repository.addQuestion(createdId, "Primera").getOrThrow()
        val q2 = repository.addQuestion(createdId, "Segunda").getOrThrow()
        repository.updateQuestion(createdId, q1, "Primera editada").getOrThrow()
        repository.reorderQuestions(createdId, listOf(q2, q1))

        val updatedQuestions = repository.observeInterview(createdId).first()?.questions.orEmpty()
        assertEquals(listOf("Segunda", "Primera editada"), updatedQuestions.map { it.text })

        // Reorder interviews: move newly created interview to top
        val currentIds = repository.interviews.first().map { it.id }
        val reorderedIds = listOf(createdId) + currentIds.filterNot { it == createdId }
        repository.reorderInterviews(reorderedIds)
        assertEquals(createdId, repository.interviews.first().first().id)

        // Duplicate and delete
        val dupId = repository.duplicateInterview(createdId).getOrThrow()
        val dup = repository.observeInterview(dupId).first()
        assertNotNull(dup)
        assertEquals(2, dup?.questions?.size)

        repository.deleteInterview(createdId).getOrThrow()
        assertNull(repository.observeInterview(createdId).first())
    }

    @Test
    fun `importing replacing and deleting image manages internal physical files cleanly`() = runBlocking {
        repository.ensureSeeded()
        val firstInterviewId = repository.interviews.first().first().id

        val tempSource1 = createTempPng(width = 160, height = 90, name = "source1.png")
        val saved1 = repository.saveMediaFromUri(
            interviewId = firstInterviewId,
            uri = Uri.fromFile(tempSource1),
            type = MediaType.IMAGE
        ).getOrThrow()

        assertTrue(File(saved1.localPath).exists())
        assertEquals(160, saved1.width)
        assertEquals(90, saved1.height)
        assertTrue(saved1.localPath.contains("interview_media/images"))

        // Replace with a second image; old internal file must be deleted
        val tempSource2 = createTempPng(width = 100, height = 200, name = "source2.png")
        val saved2 = repository.saveMediaFromUri(
            interviewId = firstInterviewId,
            uri = Uri.fromFile(tempSource2),
            type = MediaType.IMAGE
        ).getOrThrow()

        assertFalse(File(saved1.localPath).exists())
        assertTrue(File(saved2.localPath).exists())
        assertEquals(100, saved2.width)
        assertEquals(200, saved2.height)

        // Remove media deletes file and DB reference
        repository.removeMedia(firstInterviewId, MediaType.IMAGE)
        assertFalse(File(saved2.localPath).exists())
        assertNull(repository.observeInterview(firstInterviewId).first()?.imageMedia)
    }

    @Test
    fun `street mode preferences and active session persistence update DataStore cleanly`() = runBlocking {
        val initialStreet = preferences.streetModeSettings.first()
        assertTrue(initialStreet.enabled)
        assertFalse(initialStreet.boostBrightness)

        preferences.setStreetModeEnabled(false)
        preferences.setStreetBrightnessBoost(true)
        val updatedStreet = preferences.streetModeSettings.first()
        assertFalse(updatedStreet.enabled)
        assertTrue(updatedStreet.boostBrightness)

        assertNull(preferences.persistedSessionState.first())

        val snapshot = com.example.data.preferences.PersistedSessionState(
            interviewId = 7L,
            mode = "INTERVIEW",
            mediaType = MediaType.VIDEO,
            videoPositionMs = 12_345L,
            questionScrollIndex = 2,
            questionScrollOffset = 40
        )
        preferences.saveActiveSession(snapshot)
        assertEquals(snapshot, preferences.persistedSessionState.first())

        preferences.clearActiveSession()
        assertNull(preferences.persistedSessionState.first())
    }

    private fun createTempPng(width: Int, height: Int, name: String): File {
        val file = File(context.cacheDir, name)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        return file
    }
}
