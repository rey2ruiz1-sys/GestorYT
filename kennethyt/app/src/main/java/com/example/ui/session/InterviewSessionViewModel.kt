package com.example.ui.session

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.data.preferences.PersistedSessionState
import com.example.data.preferences.StreetModeSettings
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.InterviewRepository
import com.example.domain.model.Interview
import com.example.domain.model.MediaType
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SessionMode {
    VIEWER,
    INTERVIEW
}

data class InterviewSessionUiState(
    val mode: SessionMode = SessionMode.VIEWER,
    val activeInterview: Interview? = null,
    val activeIndex: Int = 0,
    val totalCount: Int = 0,
    val selectedMediaType: MediaType = MediaType.IMAGE,
    val savedVideoPositionMs: Long = 0L,
    val adjacentImagePaths: List<String> = emptyList(),
    val streetMode: StreetModeSettings = StreetModeSettings(),
    val isLocked: Boolean = false,
    val isImmersive: Boolean = false,
    val interviewNotFound: Boolean = false,
    val isLoading: Boolean = true
) {
    val hasPrevious: Boolean get() = activeIndex > 0
    val hasNext: Boolean get() = activeIndex < totalCount - 1
}

class InterviewSessionViewModel(
    private val savedStateHandle: SavedStateHandle,
    initialInterviewId: Long,
    initialMode: SessionMode = SessionMode.VIEWER,
    initialMediaType: MediaType? = null,
    initialVideoPosMs: Long = 0L,
    initialQuestionIdx: Int = 0,
    initialQuestionOffset: Int = 0,
    repository: InterviewRepository,
    private val preferences: UserPreferencesRepository
) : ViewModel() {

    private val restoredInterviewId: Long =
        savedStateHandle.get<Long>(KEY_ACTIVE_INTERVIEW_ID) ?: initialInterviewId

    private val restoredMode: SessionMode = savedStateHandle.get<String>(KEY_MODE)
        ?.let { runCatching { SessionMode.valueOf(it) }.getOrNull() }
        ?: initialMode

    private val restoredMediaType: MediaType? =
        savedStateHandle.get<String>(KEY_SELECTED_MEDIA_TYPE)
            ?.let { runCatching { MediaType.valueOf(it) }.getOrNull() }
            ?: initialMediaType

    private val restoredVideoPosMs: Long =
        (savedStateHandle.get<Long>(KEY_VIDEO_POS_MS) ?: initialVideoPosMs).coerceAtLeast(0L)

    private val restoredQuestionIndex: Int =
        (savedStateHandle.get<Int>(KEY_QUESTION_INDEX) ?: initialQuestionIdx).coerceAtLeast(0)

    private val restoredQuestionOffset: Int =
        (savedStateHandle.get<Int>(KEY_QUESTION_OFFSET) ?: initialQuestionOffset).coerceAtLeast(0)

    private val modeFlow = MutableStateFlow(restoredMode)
    private val activeInterviewIdFlow = MutableStateFlow(restoredInterviewId)
    private val selectedTabByInterviewFlow = MutableStateFlow(
        if (restoredMediaType != null) mapOf(restoredInterviewId to restoredMediaType) else emptyMap()
    )

    // Lock is deliberately in-memory only so the user is never locked out across restarts.
    private val isLockedFlow = MutableStateFlow(false)
    private val isImmersiveFlow = MutableStateFlow(false)

    // Non-reactive caches so 250ms playback ticks and question scrolling never trigger recompositions.
    private val videoPositions = mutableMapOf<Long, Long>().apply {
        if (restoredVideoPosMs > 0L) {
            put(restoredInterviewId, restoredVideoPosMs)
        }
    }
    private val questionScrollPositions = mutableMapOf<Long, Pair<Int, Int>>().apply {
        if (restoredQuestionIndex > 0 || restoredQuestionOffset > 0) {
            put(restoredInterviewId, restoredQuestionIndex to restoredQuestionOffset)
        }
    }

    @Volatile
    private var latestInterviews: List<Interview> = emptyList()

    @Volatile
    private var sessionClearedIntentionally: Boolean = false

    val uiState: StateFlow<InterviewSessionUiState> = combine(
        repository.interviews,
        modeFlow,
        activeInterviewIdFlow,
        selectedTabByInterviewFlow,
        combine(
            preferences.streetModeSettings,
            isLockedFlow,
            isImmersiveFlow
        ) { streetMode, isLocked, isImmersive ->
            Triple(streetMode, isLocked, isImmersive)
        }
    ) { interviews, mode, requestedId, tabMap, (streetMode, isLocked, isImmersive) ->
        latestInterviews = interviews
        val idx = interviews.indexOfFirst { it.id == requestedId }
        if (interviews.isEmpty() || idx < 0) {
            InterviewSessionUiState(
                mode = mode,
                streetMode = streetMode,
                isLocked = isLocked,
                isImmersive = isImmersive,
                interviewNotFound = true,
                isLoading = false
            )
        } else {
            val current = interviews[idx]
            val preferredTab = tabMap[current.id]
            val resolvedTab = when {
                preferredTab == MediaType.VIDEO && current.videoMedia != null -> MediaType.VIDEO
                preferredTab == MediaType.IMAGE && current.imageMedia != null -> MediaType.IMAGE
                current.imageMedia != null -> MediaType.IMAGE
                current.videoMedia != null -> MediaType.VIDEO
                else -> MediaType.IMAGE
            }
            val adjacentPaths = buildList {
                interviews.getOrNull(idx - 1)?.imageMedia?.localPath?.let(::add)
                interviews.getOrNull(idx + 1)?.imageMedia?.localPath?.let(::add)
            }
            InterviewSessionUiState(
                mode = mode,
                activeInterview = current,
                activeIndex = idx,
                totalCount = interviews.size,
                selectedMediaType = resolvedTab,
                savedVideoPositionMs = videoPositions[current.id] ?: 0L,
                adjacentImagePaths = adjacentPaths,
                streetMode = streetMode,
                isLocked = isLocked,
                isImmersive = isImmersive && mode == SessionMode.VIEWER && !isLocked,
                interviewNotFound = false,
                isLoading = false
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = InterviewSessionUiState()
    )

    init {
        savedStateHandle[KEY_ACTIVE_INTERVIEW_ID] = restoredInterviewId
        savedStateHandle[KEY_MODE] = restoredMode.name
        if (restoredMediaType != null) {
            savedStateHandle[KEY_SELECTED_MEDIA_TYPE] = restoredMediaType.name
        }
        persistSessionSnapshotAsync()
    }

    fun toggleMode(
        currentVideoPositionMs: Long? = null,
        bypassViewerLock: Boolean = false
    ): Boolean {
        val activeId = activeInterviewIdFlow.value
        val hasActiveInterview = uiState.value.activeInterview != null ||
            latestInterviews.any { it.id == activeId }
        if (!hasActiveInterview) return false

        return when (modeFlow.value) {
            SessionMode.VIEWER -> {
                if (isLockedFlow.value && !bypassViewerLock) return false
                if (currentVideoPositionMs != null && currentVideoPositionMs >= 0L) {
                    saveVideoPosition(activeId, currentVideoPositionMs)
                }
                isImmersiveFlow.value = false
                modeFlow.value = SessionMode.INTERVIEW
                savedStateHandle[KEY_MODE] = SessionMode.INTERVIEW.name
                persistSessionSnapshotAsync()
                true
            }
            SessionMode.INTERVIEW -> {
                modeFlow.value = SessionMode.VIEWER
                savedStateHandle[KEY_MODE] = SessionMode.VIEWER.name
                persistSessionSnapshotAsync()
                true
            }
        }
    }

    fun switchToInterviewMode(currentVideoPositionMs: Long? = null) {
        if (modeFlow.value == SessionMode.VIEWER) {
            toggleMode(currentVideoPositionMs = currentVideoPositionMs, bypassViewerLock = false)
        }
    }

    fun switchToViewer() {
        if (modeFlow.value == SessionMode.INTERVIEW) {
            toggleMode(bypassViewerLock = false)
        }
    }

    fun lockViewer() {
        isImmersiveFlow.value = false
        isLockedFlow.value = true
    }

    fun unlockViewer() {
        isLockedFlow.value = false
    }

    fun toggleImmersive() {
        if (isLockedFlow.value || modeFlow.value != SessionMode.VIEWER) return
        isImmersiveFlow.update { !it }
    }

    fun setImmersive(enabled: Boolean) {
        if (isLockedFlow.value && enabled) return
        isImmersiveFlow.value = enabled && modeFlow.value == SessionMode.VIEWER
    }

    fun saveVideoPosition(interviewId: Long, positionMs: Long) {
        val safePos = positionMs.coerceAtLeast(0L)
        videoPositions[interviewId] = safePos
        if (interviewId == activeInterviewIdFlow.value) {
            savedStateHandle[KEY_VIDEO_POS_MS] = safePos
        }
    }

    fun saveQuestionScrollPosition(interviewId: Long, firstIndex: Int, scrollOffset: Int) {
        val safeIdx = firstIndex.coerceAtLeast(0)
        val safeOffset = scrollOffset.coerceAtLeast(0)
        val nextPos = safeIdx to safeOffset
        if (questionScrollPositions[interviewId] == nextPos) return
        questionScrollPositions[interviewId] = nextPos
        if (interviewId == activeInterviewIdFlow.value) {
            savedStateHandle[KEY_QUESTION_INDEX] = safeIdx
            savedStateHandle[KEY_QUESTION_OFFSET] = safeOffset
        }
    }

    fun getQuestionScrollPosition(interviewId: Long): Pair<Int, Int> {
        return questionScrollPositions[interviewId] ?: (0 to 0)
    }

    fun selectMediaType(type: MediaType) {
        if (isLockedFlow.value) return
        val active = uiState.value.activeInterview ?: return
        val allowed = when (type) {
            MediaType.IMAGE -> active.imageMedia != null
            MediaType.VIDEO -> active.videoMedia != null
        }
        if (allowed) {
            selectedTabByInterviewFlow.update { it + (active.id to type) }
            savedStateHandle[KEY_SELECTED_MEDIA_TYPE] = type.name
            persistSessionSnapshotAsync()
        }
    }

    fun goToPrevious() {
        if (isLockedFlow.value && modeFlow.value == SessionMode.VIEWER) return
        val list = latestInterviews
        val idx = uiState.value.activeIndex
        if (idx > 0 && idx - 1 in list.indices) {
            val targetId = list[idx - 1].id
            updateActiveInterviewId(targetId)
        }
    }

    fun goToNext() {
        if (isLockedFlow.value && modeFlow.value == SessionMode.VIEWER) return
        val list = latestInterviews
        val idx = uiState.value.activeIndex
        if (idx + 1 in list.indices) {
            val targetId = list[idx + 1].id
            updateActiveInterviewId(targetId)
        }
    }

    private fun updateActiveInterviewId(newId: Long) {
        activeInterviewIdFlow.value = newId
        savedStateHandle[KEY_ACTIVE_INTERVIEW_ID] = newId
        val nextPos = videoPositions[newId] ?: 0L
        savedStateHandle[KEY_VIDEO_POS_MS] = nextPos
        val (qIdx, qOff) = questionScrollPositions[newId] ?: (0 to 0)
        savedStateHandle[KEY_QUESTION_INDEX] = qIdx
        savedStateHandle[KEY_QUESTION_OFFSET] = qOff
        val savedTab = selectedTabByInterviewFlow.value[newId]
        if (savedTab != null) {
            savedStateHandle[KEY_SELECTED_MEDIA_TYPE] = savedTab.name
        } else {
            savedStateHandle.remove<String>(KEY_SELECTED_MEDIA_TYPE)
        }
        persistSessionSnapshotAsync()
    }

    fun persistSessionSnapshot(
        overrideVideoPositionMs: Long? = null,
        overrideQuestionIndex: Int? = null,
        overrideQuestionOffset: Int? = null
    ) {
        if (sessionClearedIntentionally) return
        val activeId = activeInterviewIdFlow.value
        if (activeId <= 0L) return

        if (overrideVideoPositionMs != null) {
            saveVideoPosition(activeId, overrideVideoPositionMs)
        }
        if (overrideQuestionIndex != null && overrideQuestionOffset != null) {
            saveQuestionScrollPosition(activeId, overrideQuestionIndex, overrideQuestionOffset)
        }
        persistSessionSnapshotAsync()
    }

    private fun persistSessionSnapshotAsync() {
        if (sessionClearedIntentionally) return
        val activeId = activeInterviewIdFlow.value
        if (activeId <= 0L) return

        val mode = modeFlow.value
        val activeInterview = latestInterviews.firstOrNull { it.id == activeId }
        val preferredTab = selectedTabByInterviewFlow.value[activeId]
        val resolvedTab = when {
            activeInterview == null -> preferredTab ?: MediaType.IMAGE
            preferredTab == MediaType.VIDEO && activeInterview.videoMedia != null -> MediaType.VIDEO
            preferredTab == MediaType.IMAGE && activeInterview.imageMedia != null -> MediaType.IMAGE
            activeInterview.imageMedia != null -> MediaType.IMAGE
            activeInterview.videoMedia != null -> MediaType.VIDEO
            else -> MediaType.IMAGE
        }
        val videoPos = videoPositions[activeId] ?: 0L
        val (qIdx, qOff) = questionScrollPositions[activeId] ?: (0 to 0)

        viewModelScope.launch(NonCancellable) {
            if (!sessionClearedIntentionally) {
                preferences.saveActiveSession(
                    PersistedSessionState(
                        interviewId = activeId,
                        mode = mode.name,
                        mediaType = resolvedTab,
                        videoPositionMs = videoPos,
                        questionScrollIndex = qIdx,
                        questionScrollOffset = qOff
                    )
                )
            }
        }
    }

    fun clearPersistedSession() {
        sessionClearedIntentionally = true
        viewModelScope.launch(NonCancellable) {
            preferences.clearActiveSession()
        }
    }

    override fun onCleared() {
        if (!sessionClearedIntentionally) {
            persistSessionSnapshotAsync()
        }
        super.onCleared()
    }

    companion object {
        private const val KEY_ACTIVE_INTERVIEW_ID = "session_active_interview_id"
        private const val KEY_MODE = "session_mode"
        private const val KEY_SELECTED_MEDIA_TYPE = "session_selected_media_type"
        private const val KEY_VIDEO_POS_MS = "session_video_pos_ms"
        private const val KEY_QUESTION_INDEX = "session_question_index"
        private const val KEY_QUESTION_OFFSET = "session_question_offset"

        fun provideFactory(
            initialInterviewId: Long,
            initialMode: SessionMode = SessionMode.VIEWER,
            initialMediaType: MediaType? = null,
            initialVideoPosMs: Long = 0L,
            initialQuestionIdx: Int = 0,
            initialQuestionOffset: Int = 0,
            repository: InterviewRepository,
            preferences: UserPreferencesRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val handle = extras.createSavedStateHandle()
                return InterviewSessionViewModel(
                    savedStateHandle = handle,
                    initialInterviewId = initialInterviewId,
                    initialMode = initialMode,
                    initialMediaType = initialMediaType,
                    initialVideoPosMs = initialVideoPosMs,
                    initialQuestionIdx = initialQuestionIdx,
                    initialQuestionOffset = initialQuestionOffset,
                    repository = repository,
                    preferences = preferences
                ) as T
            }
        }
    }
}
