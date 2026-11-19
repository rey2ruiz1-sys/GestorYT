package com.example.ui.editor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.InterviewRepository
import com.example.domain.model.Interview
import com.example.domain.model.MediaType
import com.example.domain.model.Question
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EditorUiState(
    val interview: Interview? = null,
    val orderedQuestions: List<Question> = emptyList(),
    val isImportingImage: Boolean = false,
    val isImportingVideo: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val isLoading: Boolean = true
)

class EditorViewModel(
    private val interviewId: Long,
    private val repository: InterviewRepository
) : ViewModel() {

    private val optimisticQuestions = MutableStateFlow<List<Question>?>(null)
    private val importingImage = MutableStateFlow(false)
    private val importingVideo = MutableStateFlow(false)
    private val statusMessage = MutableStateFlow<String?>(null)
    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<EditorUiState> = combine(
        repository.observeInterview(interviewId),
        optimisticQuestions,
        importingImage,
        importingVideo,
        combine(statusMessage, errorMessage) { status, error -> status to error }
    ) { interview, dragQuestions, imgBusy, vidBusy, messages ->
        EditorUiState(
            interview = interview,
            orderedQuestions = dragQuestions ?: interview?.questions.orEmpty(),
            isImportingImage = imgBusy,
            isImportingVideo = vidBusy,
            statusMessage = messages.first,
            errorMessage = messages.second,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EditorUiState()
    )

    fun saveTitle(newTitle: String) {
        viewModelScope.launch {
            repository.updateInterviewTitle(interviewId, newTitle)
                .onSuccess {
                    errorMessage.value = null
                    statusMessage.value = "Título guardado"
                }
                .onFailure { err ->
                    errorMessage.value = err.message ?: "El título no puede estar vacío."
                }
        }
    }

    fun importImage(uri: Uri) {
        if (importingImage.value) return
        viewModelScope.launch {
            importingImage.value = true
            errorMessage.value = null
            repository.saveMediaFromUri(interviewId, uri, MediaType.IMAGE)
                .onSuccess {
                    statusMessage.value = "Fotografía guardada localmente"
                }
                .onFailure { err ->
                    errorMessage.value = err.message ?: "No se pudo importar la fotografía."
                }
            importingImage.value = false
        }
    }

    fun removeImage() {
        viewModelScope.launch {
            repository.removeMedia(interviewId, MediaType.IMAGE)
            statusMessage.value = "Fotografía eliminada"
        }
    }

    fun importVideo(uri: Uri) {
        if (importingVideo.value) return
        viewModelScope.launch {
            importingVideo.value = true
            errorMessage.value = null
            repository.saveMediaFromUri(interviewId, uri, MediaType.VIDEO)
                .onSuccess {
                    statusMessage.value = "Video guardado localmente"
                }
                .onFailure { err ->
                    errorMessage.value = err.message ?: "No se pudo importar el video."
                }
            importingVideo.value = false
        }
    }

    fun removeVideo() {
        viewModelScope.launch {
            repository.removeMedia(interviewId, MediaType.VIDEO)
            statusMessage.value = "Video eliminado"
        }
    }

    fun addQuestion(text: String, onAdded: () -> Unit = {}) {
        viewModelScope.launch {
            repository.addQuestion(interviewId, text)
                .onSuccess {
                    errorMessage.value = null
                    statusMessage.value = "Pregunta agregada"
                    onAdded()
                }
                .onFailure { err ->
                    errorMessage.value = err.message ?: "La pregunta no puede estar vacía."
                }
        }
    }

    fun updateQuestion(questionId: Long, text: String, onUpdated: () -> Unit = {}) {
        viewModelScope.launch {
            repository.updateQuestion(interviewId, questionId, text)
                .onSuccess {
                    errorMessage.value = null
                    statusMessage.value = "Pregunta actualizada"
                    onUpdated()
                }
                .onFailure { err ->
                    errorMessage.value = err.message ?: "La pregunta no puede estar vacía."
                }
        }
    }

    fun deleteQuestion(questionId: Long) {
        viewModelScope.launch {
            repository.deleteQuestion(interviewId, questionId)
            statusMessage.value = "Pregunta eliminada"
        }
    }

    fun moveQuestion(fromIndex: Int, toIndex: Int) {
        val current = (optimisticQuestions.value ?: uiState.value.orderedQuestions).toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) return
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        optimisticQuestions.value = current
    }

    fun commitQuestionOrder() {
        val list = optimisticQuestions.value ?: return
        viewModelScope.launch {
            repository.reorderQuestions(interviewId, list.map { it.id })
            optimisticQuestions.value = null
            statusMessage.value = "Orden de preguntas guardado"
        }
    }

    fun moveQuestionStep(index: Int, delta: Int) {
        val current = uiState.value.orderedQuestions.toMutableList()
        val target = index + delta
        if (index !in current.indices || target !in current.indices) return
        val item = current.removeAt(index)
        current.add(target, item)
        viewModelScope.launch {
            repository.reorderQuestions(interviewId, current.map { it.id })
            statusMessage.value = "Orden actualizado"
        }
    }

    fun duplicateInterview(onDuplicated: (Long) -> Unit) {
        viewModelScope.launch {
            repository.duplicateInterview(interviewId)
                .onSuccess { newId ->
                    statusMessage.value = "Entrevista duplicada"
                    onDuplicated(newId)
                }
                .onFailure { err ->
                    errorMessage.value = err.message ?: "No se pudo duplicar la entrevista."
                }
        }
    }

    fun deleteInterview(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteInterview(interviewId)
                .onSuccess { onDeleted() }
                .onFailure { err ->
                    errorMessage.value = err.message ?: "No se pudo eliminar la entrevista."
                }
        }
    }

    fun clearFeedback() {
        statusMessage.value = null
        errorMessage.value = null
    }

    companion object {
        fun provideFactory(
            interviewId: Long,
            repository: InterviewRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return EditorViewModel(interviewId, repository) as T
            }
        }
    }
}
