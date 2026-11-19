package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.defaultDataStore: DataStore<Preferences> by preferencesDataStore(name = "kenneth_preferences")

data class StreetModeSettings(
    val enabled: Boolean = true,
    val boostBrightness: Boolean = false
)

data class PersistedSessionState(
    val interviewId: Long,
    val mode: String,
    val mediaType: MediaType,
    val videoPositionMs: Long,
    val questionScrollIndex: Int,
    val questionScrollOffset: Int
)

class UserPreferencesRepository(
    private val dataStore: DataStore<Preferences>
) {
    constructor(context: Context) : this(context.defaultDataStore)

    private object Keys {
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val STREET_MODE_BRIGHTNESS = booleanPreferencesKey("street_mode_brightness")
        val HAS_SEEDED_INITIAL_DATA = booleanPreferencesKey("has_seeded_initial_data")
        val SEED_CONTENT_VERSION = intPreferencesKey("seed_content_version")

        val ACTIVE_INTERVIEW_ID = longPreferencesKey("active_interview_id")
        val ACTIVE_MODE = stringPreferencesKey("active_mode")
        val ACTIVE_MEDIA_TYPE = stringPreferencesKey("active_media_type")
        val ACTIVE_VIDEO_POS_MS = longPreferencesKey("active_video_pos_ms")
        val ACTIVE_QUESTION_INDEX = intPreferencesKey("active_question_index")
        val ACTIVE_QUESTION_OFFSET = intPreferencesKey("active_question_offset")
    }

    val keepScreenOn: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.KEEP_SCREEN_ON] ?: true
    }

    val streetBrightnessBoost: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.STREET_MODE_BRIGHTNESS] ?: false
    }

    val streetModeSettings: Flow<StreetModeSettings> = combine(
        keepScreenOn,
        streetBrightnessBoost
    ) { keepOn, boost ->
        StreetModeSettings(enabled = keepOn, boostBrightness = boost)
    }

    val persistedSessionState: Flow<PersistedSessionState?> = dataStore.data.map { prefs ->
        val id = prefs[Keys.ACTIVE_INTERVIEW_ID] ?: return@map null
        if (id <= 0L) return@map null
        val mode = prefs[Keys.ACTIVE_MODE] ?: "VIEWER"
        val mediaType = runCatching {
            MediaType.valueOf(prefs[Keys.ACTIVE_MEDIA_TYPE] ?: MediaType.IMAGE.name)
        }.getOrDefault(MediaType.IMAGE)
        PersistedSessionState(
            interviewId = id,
            mode = mode,
            mediaType = mediaType,
            videoPositionMs = prefs[Keys.ACTIVE_VIDEO_POS_MS] ?: 0L,
            questionScrollIndex = prefs[Keys.ACTIVE_QUESTION_INDEX] ?: 0,
            questionScrollOffset = prefs[Keys.ACTIVE_QUESTION_OFFSET] ?: 0
        )
    }

    suspend fun setStreetModeEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.KEEP_SCREEN_ON] = enabled
        }
    }

    suspend fun setStreetBrightnessBoost(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.STREET_MODE_BRIGHTNESS] = enabled
        }
    }

    suspend fun saveActiveSession(state: PersistedSessionState) {
        dataStore.edit { prefs ->
            prefs[Keys.ACTIVE_INTERVIEW_ID] = state.interviewId
            prefs[Keys.ACTIVE_MODE] = state.mode
            prefs[Keys.ACTIVE_MEDIA_TYPE] = state.mediaType.name
            prefs[Keys.ACTIVE_VIDEO_POS_MS] = state.videoPositionMs.coerceAtLeast(0L)
            prefs[Keys.ACTIVE_QUESTION_INDEX] = state.questionScrollIndex.coerceAtLeast(0)
            prefs[Keys.ACTIVE_QUESTION_OFFSET] = state.questionScrollOffset.coerceAtLeast(0)
        }
    }

    suspend fun clearActiveSession() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.ACTIVE_INTERVIEW_ID)
            prefs.remove(Keys.ACTIVE_MODE)
            prefs.remove(Keys.ACTIVE_MEDIA_TYPE)
            prefs.remove(Keys.ACTIVE_VIDEO_POS_MS)
            prefs.remove(Keys.ACTIVE_QUESTION_INDEX)
            prefs.remove(Keys.ACTIVE_QUESTION_OFFSET)
        }
    }

    suspend fun getSeedContentVersion(): Int {
        return dataStore.data.map { prefs ->
            val explicitVersion = prefs[Keys.SEED_CONTENT_VERSION]
            when {
                explicitVersion != null -> explicitVersion
                prefs[Keys.HAS_SEEDED_INITIAL_DATA] == true -> 1
                else -> 0
            }
        }.first()
    }

    suspend fun setSeedContentVersion(version: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.SEED_CONTENT_VERSION] = version
            prefs[Keys.HAS_SEEDED_INITIAL_DATA] = true
        }
    }
}
