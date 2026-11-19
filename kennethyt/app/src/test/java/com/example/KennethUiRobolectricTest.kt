package com.example

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.database.KennethDatabase
import com.example.data.media.MediaStorageManager
import com.example.data.preferences.PersistedSessionState
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.DefaultInterviewSeed
import com.example.data.repository.LocalInterviewRepository
import com.example.domain.model.MediaType
import com.example.navigation.KennethNavHost
import com.example.ui.theme.KennethYTTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
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
class KennethUiRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var db: KennethDatabase
    private lateinit var preferences: UserPreferencesRepository
    private lateinit var repository: LocalInterviewRepository
    private lateinit var container: AppContainer
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
            produceFile = { File(tempFolder.root, "ui_prefs.preferences_pb") }
        )
        preferences = UserPreferencesRepository(testDataStore)
        val mediaStorage = MediaStorageManager(context)
        repository = LocalInterviewRepository(db.interviewDao(), mediaStorage, preferences)
        runBlocking {
            db.interviewDao().seedInitialInterviews(
                specs = DefaultInterviewSeed.realInterviews,
                now = System.currentTimeMillis()
            )
        }
        val app = context.applicationContext as KennethApplication
        container = AppContainer(
            application = app,
            customRepository = repository,
            customPreferences = preferences
        )
    }

    @After
    fun tearDown() {
        db.close()
        dataStoreScope.cancel()
        File(context.filesDir, "interview_media").deleteRecursively()
    }

    @Test
    fun `bidirectional instant toggle between viewer and interview mode preserves active interview`() {
        composeRule.setContent {
            KennethYTTheme {
                KennethNavHost(container = container)
            }
        }

        waitForText("GTA 6: significado")
        composeRule.onNodeWithText("Gestiona tus").assertIsDisplayed()
        composeRule.onNodeWithText("entrevistas").assertIsDisplayed()

        // Verify real interview titles appear in list
        composeRule.onNodeWithTag("interview_list").performScrollToNode(hasText("Apodo y cita"))
        composeRule.onNodeWithText("Apodo y cita").assertIsDisplayed()
        composeRule.onNodeWithTag("interview_list").performScrollToNode(hasText("GTA 6: significado"))

        // 1. Open Interview 01 Viewer
        composeRule.onNodeWithText("GTA 6: significado").performClick()
        waitForText("1 de 12")
        composeRule.onNodeWithTag("viewer_prev_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("viewer_next_button").assertIsEnabled()

        // 2. Viewer -> YouTube button -> Interview Mode
        composeRule.onNodeWithTag("enter_interview_mode_button").performClick()
        waitForText("¿Qué significan las siglas GTA?")
        composeRule.onNodeWithTag("interview_mode_youtube_button").assertIsDisplayed()
        composeRule.onNodeWithTag("interview_mode_header_button").assertIsDisplayed()

        // 3. Interview Mode -> Top-right YouTube button -> back to Viewer (same interview 1 de 12)
        composeRule.onNodeWithTag("interview_mode_youtube_button").performClick()
        waitForText("1 de 12")

        // 4. Viewer -> YouTube button -> Interview Mode -> tap "Modo entrevista" pill -> back to Viewer
        composeRule.onNodeWithTag("enter_interview_mode_button").performClick()
        waitForText("¿Qué significan las siglas GTA?")
        composeRule.onNodeWithTag("interview_mode_header_button").performClick()
        waitForText("1 de 12")

        // 5. Switch to Interview Mode, advance to Interview 03 ("País del juego"), return via YouTube button -> Viewer shows 3 de 12
        composeRule.onNodeWithTag("enter_interview_mode_button").performClick()
        waitForText("¿Qué significan las siglas GTA?")
        composeRule.onNodeWithTag("interview_next_button").performClick()
        waitForText("Nombre de Jason")
        composeRule.onNodeWithText("¿Cómo cree que se llama este muchacho?").assertIsDisplayed()

        composeRule.onNodeWithTag("interview_next_button").performClick()
        waitForText("País del juego")
        composeRule.onNodeWithText("¿Qué país cree que es?").assertIsDisplayed()

        composeRule.onNodeWithTag("interview_mode_youtube_button").performClick()
        waitForText("3 de 12")

        // 6. Re-enter Interview Mode and tap Grid button -> returns to Home without stack leaks
        composeRule.onNodeWithTag("enter_interview_mode_button").performClick()
        waitForText("País del juego")
        composeRule.onNodeWithTag("interview_grid_home_button").performClick()
        waitForText("Gestiona tus")
    }

    @Test
    fun `street mode switches persist preferences from home options sheet`() {
        composeRule.setContent {
            KennethYTTheme {
                KennethNavHost(container = container)
            }
        }

        waitForText("GTA 6: significado")
        composeRule.onNodeWithTag("home_menu_button").performClick()
        composeRule.waitForIdle()
        waitForText("Modo calle")
        waitForText("Brillo alto bajo el sol")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("street_brightness_switch").assertIsDisplayed()
        composeRule.onNodeWithTag("street_brightness_switch").assertIsEnabled()

        composeRule.onNodeWithTag("street_brightness_switch").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 3_000) {
            runBlocking { preferences.streetModeSettings.first().boostBrightness }
        }

        composeRule.onNodeWithTag("street_mode_switch").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 3_000) {
            runBlocking { !preferences.streetModeSettings.first().enabled }
        }
    }

    @Test
    fun `immersive photo mode hides secondary controls while keeping YouTube button accessible`() {
        runBlocking {
            val firstId = repository.interviews.first().first().id
            val imgFile = createTempPng(width = 300, height = 200, name = "immersive_test.png")
            repository.saveMediaFromUri(firstId, Uri.fromFile(imgFile), MediaType.IMAGE).getOrThrow()
        }

        composeRule.setContent {
            KennethYTTheme {
                KennethNavHost(container = container)
            }
        }

        waitForText("GTA 6: significado")
        composeRule.onNodeWithText("GTA 6: significado").performClick()
        waitForText("1 de 12")
        composeRule.onNodeWithTag("viewer_image_container").assertIsDisplayed()

        // Tap photo -> enter immersive mode
        composeRule.onNodeWithTag("viewer_image_container").performClick()
        composeRule.waitUntil(timeoutMillis = 3_000) {
            composeRule.onAllNodesWithTag("viewer_counter").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("viewer_back_button").assertDoesNotExist()
        composeRule.onNodeWithTag("viewer_next_button").assertDoesNotExist()
        // YouTube button MUST remain accessible even in immersive mode
        composeRule.onNodeWithTag("enter_interview_mode_button").assertIsDisplayed()
        composeRule.onNodeWithTag("enter_interview_mode_button").assertIsEnabled()

        // Tap photo again -> restore controls
        composeRule.onNodeWithTag("viewer_image_container").performClick()
        waitForTag("viewer_counter")
        composeRule.onNodeWithTag("viewer_back_button").assertIsDisplayed()
        composeRule.onNodeWithTag("viewer_next_button").assertIsDisplayed()

        // Enter immersive again and verify direct jump to Interview Mode via YouTube button works
        composeRule.onNodeWithTag("viewer_image_container").performClick()
        composeRule.waitUntil(timeoutMillis = 3_000) {
            composeRule.onAllNodesWithTag("viewer_counter").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("enter_interview_mode_button").performClick()
        waitForText("¿Qué significan las siglas GTA?")
    }

    @Test
    fun `viewer lock blocks accidental navigation and unlocks on long hold`() {
        composeRule.setContent {
            KennethYTTheme {
                KennethNavHost(container = container)
            }
        }

        waitForText("GTA 6: significado")
        composeRule.onNodeWithText("GTA 6: significado").performClick()
        waitForText("1 de 12")

        // Single tap activates accidental touch protection lock
        composeRule.onNodeWithTag("viewer_lock_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("viewer_locked_media_shield").assertIsDisplayed()
        composeRule.onNodeWithTag("viewer_back_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("enter_interview_mode_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("viewer_next_button").assertIsNotEnabled()

        // Short tap on lock button keeps viewer locked and shows discrete hint
        composeRule.onNodeWithTag("viewer_lock_button").performClick()
        waitForTag("viewer_status_pill")
        composeRule.onNodeWithTag("viewer_next_button").assertIsNotEnabled()

        // Hold lock button ~950 ms -> unlocks cleanly
        composeRule.onNodeWithTag("viewer_lock_button").performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(950L)
        composeRule.onNodeWithTag("viewer_lock_button").performTouchInput { up() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("viewer_locked_media_shield").assertDoesNotExist()
        composeRule.onNodeWithTag("viewer_back_button").assertIsEnabled()
        composeRule.onNodeWithTag("enter_interview_mode_button").assertIsEnabled()
        composeRule.onNodeWithTag("viewer_next_button").assertIsEnabled()
    }

    @Test
    fun `cold start restores active interview session and mode when interview exists`() {
        val targetInterview = runBlocking { repository.interviews.first()[2] } // 03 País del juego
        runBlocking {
            preferences.saveActiveSession(
                PersistedSessionState(
                    interviewId = targetInterview.id,
                    mode = "INTERVIEW",
                    mediaType = MediaType.IMAGE,
                    videoPositionMs = 4_200L,
                    questionScrollIndex = 1,
                    questionScrollOffset = 0
                )
            )
        }

        composeRule.setContent {
            KennethYTTheme {
                KennethNavHost(container = container)
            }
        }

        // Opens directly in Interview 03 ("País del juego") in Interview Mode
        waitForText("País del juego")
        composeRule.onNodeWithTag("interview_mode_header_button").assertIsDisplayed()

        // Intentionally returning to Home clears persisted session cleanly
        composeRule.onNodeWithTag("interview_grid_home_button").performClick()
        waitForText("Gestiona tus")
        composeRule.waitUntil(timeoutMillis = 3_000) {
            runBlocking { preferences.persistedSessionState.first() == null }
        }
    }

    @Test
    fun `cold start falls back cleanly to Home when persisted interview no longer exists`() {
        runBlocking {
            preferences.saveActiveSession(
                PersistedSessionState(
                    interviewId = 99_999L,
                    mode = "VIEWER",
                    mediaType = MediaType.IMAGE,
                    videoPositionMs = 0L,
                    questionScrollIndex = 0,
                    questionScrollOffset = 0
                )
            )
        }

        composeRule.setContent {
            KennethYTTheme {
                KennethNavHost(container = container)
            }
        }

        waitForText("Gestiona tus")
        runBlocking {
            assertNull(preferences.persistedSessionState.first())
        }
    }

    @Test
    fun `creating new interview via FAB updates home list`() {
        composeRule.setContent {
            KennethYTTheme {
                KennethNavHost(container = container)
            }
        }

        waitForText("GTA 6: significado")

        composeRule.onNodeWithTag("add_interview_fab").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("new_interview_title_input").performTextInput("Entrevista sorpresa")
        composeRule.onNodeWithTag("create_interview_only_button").performClick()

        composeRule.onNodeWithTag("interview_list").performScrollToNode(hasText("Entrevista sorpresa"))
        composeRule.onNodeWithText("Entrevista sorpresa").assertIsDisplayed()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
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
