package com.example

import android.content.Context
import android.os.Looper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.database.KennethDatabase
import com.example.data.media.MediaStorageManager
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.DefaultInterviewSeed
import com.example.data.repository.LocalInterviewRepository
import com.example.domain.model.MediaType
import com.example.ui.session.InterviewSessionViewModel
import com.example.ui.session.SessionMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VolumeDownStatePreservationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var db: KennethDatabase
    private lateinit var preferences: UserPreferencesRepository
    private lateinit var repository: LocalInterviewRepository
    private lateinit var dataStoreScope: CoroutineScope

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, KennethDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dataStoreScope = CoroutineScope(Dispatchers.IO + Job())
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(tempFolder.root, "state_prefs.preferences_pb") }
        )
        preferences = UserPreferencesRepository(testDataStore)
        repository = LocalInterviewRepository(
            dao = db.interviewDao(),
            mediaStorage = MediaStorageManager(context),
            preferences = preferences
        )
        runBlocking {
            db.interviewDao().seedInitialInterviews(
                specs = DefaultInterviewSeed.realInterviews,
                now = System.currentTimeMillis()
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
        dataStoreScope.cancel()
    }

    @Test
    fun `toggleMode preserves interview, video position, question scroll, and respects viewer lock rules`() {
        val firstInterview = runBlocking { repository.interviews.first().first() }
        val vm = InterviewSessionViewModel(
            savedStateHandle = SavedStateHandle(),
            initialInterviewId = firstInterview.id,
            initialMediaType = MediaType.VIDEO,
            initialVideoPosMs = 7_500L,
            initialQuestionIdx = 2,
            initialQuestionOffset = 35,
            repository = repository,
            preferences = preferences
        )

        val collectScope = CoroutineScope(Dispatchers.Main.immediate + Job())
        collectScope.launch { vm.uiState.collect {} }

        try {
            awaitCondition { !vm.uiState.value.isLoading }

            // Lock Viewer: UI toggle (bypassViewerLock = false) is blocked, Volume Down (bypassViewerLock = true) works
            vm.lockViewer()
            awaitCondition { vm.uiState.value.isLocked }
            assertFalse(vm.toggleMode(currentVideoPositionMs = 14_200L, bypassViewerLock = false))
            assertEquals(SessionMode.VIEWER, vm.uiState.value.mode)

            assertTrue(vm.toggleMode(currentVideoPositionMs = 14_200L, bypassViewerLock = true))
            awaitCondition { vm.uiState.value.mode == SessionMode.INTERVIEW }
            val interviewState = vm.uiState.value
            assertEquals(firstInterview.id, interviewState.activeInterview?.id)
            assertEquals(14_200L, interviewState.savedVideoPositionMs)
            assertEquals(2 to 35, vm.getQuestionScrollPosition(firstInterview.id))

            // Update question scroll in Interview Mode and toggle back to Viewer
            vm.saveQuestionScrollPosition(firstInterview.id, 3, 80)
            assertTrue(vm.toggleMode(bypassViewerLock = true))
            awaitCondition { vm.uiState.value.mode == SessionMode.VIEWER }
            val viewerState = vm.uiState.value
            assertEquals(firstInterview.id, viewerState.activeInterview?.id)
            assertEquals(14_200L, viewerState.savedVideoPositionMs)
            assertEquals(3 to 80, vm.getQuestionScrollPosition(firstInterview.id))
            assertTrue(viewerState.isLocked)
        } finally {
            collectScope.cancel()
        }
    }

    private fun awaitCondition(timeoutMs: Long = 5_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate()) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("Timed out waiting for condition")
            }
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }
}
