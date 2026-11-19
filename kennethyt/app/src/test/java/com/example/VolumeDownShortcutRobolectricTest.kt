package com.example

import android.graphics.Bitmap
import android.net.Uri
import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.domain.model.MediaType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VolumeDownShortcutRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `volume down shortcut end to end across home, viewer, immersive, lock, interview mode, and editor`() {
        val app = composeRule.activity.application as KennethApplication
        runBlocking {
            app.container.preferencesRepository.clearActiveSession()
            app.container.interviewRepository.ensureSeeded()
        }
        waitForText("GTA 6: significado")

        // 1. On Home: Volume Down and Volume Up pass through to system
        assertFalse(dispatchKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, repeatCount = 0))
        assertFalse(dispatchKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN, repeatCount = 0))
        assertFalse(dispatchKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, repeatCount = 0))
        assertFalse(dispatchKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP, repeatCount = 0))

        // 2. Open Interview 01 Viewer
        composeRule.onNodeWithText("GTA 6: significado").performClick()
        waitForText("1 de 12")

        // Volume Up stays untouched inside Viewer
        assertFalse(dispatchKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP, repeatCount = 0))
        assertFalse(dispatchKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP, repeatCount = 0))

        // 3. Volume Down (ACTION_DOWN, repeatCount = 0) toggles Viewer -> Interview Mode once
        assertTrue(dispatchKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, repeatCount = 0))
        waitForText("¿Qué significan las siglas GTA?")

        // Repeats and ACTION_UP are consumed without toggling again
        assertTrue(dispatchKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, repeatCount = 1))
        assertTrue(dispatchKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, repeatCount = 2))
        assertTrue(dispatchKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN, repeatCount = 0))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("¿Qué significan las siglas GTA?").assertIsDisplayed()

        // 4. Advance to Interview 03 in Interview Mode, then Volume Down returns to Viewer at 3 de 12
        composeRule.onNodeWithTag("interview_next_button").performClick()
        waitForText("Nombre de Jason")
        composeRule.onNodeWithTag("interview_next_button").performClick()
        waitForText("País del juego")

        assertTrue(dispatchKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, repeatCount = 0))
        assertTrue(dispatchKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN, repeatCount = 0))
        waitForText("3 de 12")

        // 5. YouTube buttons remain functional and reuse the same toggle logic
        composeRule.onNodeWithTag("enter_interview_mode_button").performClick()
        waitForText("País del juego")
        composeRule.onNodeWithTag("interview_mode_youtube_button").performClick()
        waitForText("3 de 12")

        // 6. Touch Lock active in Viewer: Volume Down still toggles & preserves lock on return
        composeRule.onNodeWithTag("viewer_lock_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("viewer_locked_media_shield").assertIsDisplayed()
        composeRule.onNodeWithTag("enter_interview_mode_button").assertIsNotEnabled()

        assertTrue(dispatchKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, repeatCount = 0))
        assertTrue(dispatchKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN, repeatCount = 0))
        waitForText("País del juego")

        assertTrue(dispatchKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, repeatCount = 0))
        assertTrue(dispatchKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN, repeatCount = 0))
        waitForText("3 de 12")
        composeRule.onNodeWithTag("viewer_locked_media_shield").assertIsDisplayed()

        // Unlock viewer cleanly
        composeRule.onNodeWithTag("viewer_back_button").assertIsNotEnabled()

        // 7. Navigate back to Home via Interview Mode grid button -> Volume Down passes through again
        assertTrue(dispatchKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, repeatCount = 0))
        assertTrue(dispatchKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN, repeatCount = 0))
        waitForText("País del juego")
        composeRule.onNodeWithTag("interview_grid_home_button").performClick()
        waitForText("Gestiona tus")
        assertFalse(dispatchKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, repeatCount = 0))
        assertFalse(dispatchKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN, repeatCount = 0))

        // 8. Test Immersive Viewer -> Volume Down toggle -> Editor passthrough
        val firstId = runBlocking { app.container.interviewRepository.interviews.first().first().id }
        val imgFile = File(app.cacheDir, "vol_immersive_test.png")
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        FileOutputStream(imgFile).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        bitmap.recycle()
        runBlocking {
            app.container.interviewRepository.saveMediaFromUri(firstId, Uri.fromFile(imgFile), MediaType.IMAGE)
                .getOrThrow()
        }
        imgFile.delete()

        try {
            composeRule.onNodeWithText("GTA 6: significado").performClick()
            waitForText("1 de 12")
            composeRule.onNodeWithTag("viewer_image_container").performClick()
            composeRule.waitUntil(timeoutMillis = 3_000) {
                composeRule.onAllNodesWithTag("viewer_counter").fetchSemanticsNodes().isEmpty()
            }

            assertTrue(dispatchKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, repeatCount = 0))
            assertTrue(dispatchKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN, repeatCount = 0))
            waitForText("¿Qué significan las siglas GTA?")

            assertTrue(dispatchKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, repeatCount = 0))
            assertTrue(dispatchKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN, repeatCount = 0))
            waitForText("1 de 12")

            // Return to Home and open Editor from Home list -> Volume Down passes through in Editor
            composeRule.onNodeWithTag("viewer_back_button").performClick()
            waitForText("Gestiona tus")
            composeRule.onNodeWithTag("edit_interview_$firstId").performClick()
            waitForText("Título de la entrevista")
            assertFalse(dispatchKey(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN, repeatCount = 0))
            assertFalse(dispatchKey(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN, repeatCount = 0))
        } finally {
            runBlocking {
                app.container.interviewRepository.removeMedia(firstId, MediaType.IMAGE)
                app.container.preferencesRepository.clearActiveSession()
            }
        }
    }

    private fun dispatchKey(action: Int, keyCode: Int, repeatCount: Int): Boolean {
        var handled = false
        composeRule.runOnUiThread {
            val event = KeyEvent(0L, 0L, action, keyCode, repeatCount)
            handled = composeRule.activity.dispatchKeyEvent(event)
        }
        return handled
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
