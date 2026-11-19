package com.example.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.backup.ApkInputSource
import com.example.data.backup.ApkResolution
import com.example.data.backup.BackupTransferManager
import com.example.data.backup.ExportPackageSummary
import com.example.data.preferences.StreetModeSettings
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.InterviewRepository
import com.example.domain.model.Interview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface TransferUiState {
    data object Idle : TransferUiState
    data class NeedsManualApk(val reason: String) : TransferUiState
    data class InProgress(
        val title: String,
        val message: String,
        val progress: Float? = null
    ) : TransferUiState
    data class ExportReady(val summary: ExportPackageSummary) : TransferUiState
    data class Completed(val title: String, val message: String) : TransferUiState
    data class Error(val title: String, val message: String) : TransferUiState
}

data class HomeUiState(
    val interviews: List<Interview> = emptyList(),
    val streetMode: StreetModeSettings = StreetModeSettings(),
    val transferState: TransferUiState = TransferUiState.Idle,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class HomeViewModel(
    private val repository: InterviewRepository,
    private val preferences: UserPreferencesRepository,
    private val backupTransferManager: BackupTransferManager? = null
) : ViewModel() {

    private val optimisticOrder = MutableStateFlow<List<Interview>?>(null)
    private val errorState = MutableStateFlow<String?>(null)
    private val transferState = MutableStateFlow<TransferUiState>(TransferUiState.Idle)

    val uiState: StateFlow<HomeUiState> = combine(
        repository.interviews,
        optimisticOrder,
        preferences.streetModeSettings,
        transferState,
        errorState
    ) { dbInterviews, dragList, streetMode, transfer, error ->
        HomeUiState(
            interviews = dragList ?: dbInterviews,
            streetMode = streetMode,
            transferState = transfer,
            isLoading = false,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    fun moveInterview(fromIndex: Int, toIndex: Int) {
        val current = (optimisticOrder.value ?: uiState.value.interviews).toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices || fromIndex == toIndex) return
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        optimisticOrder.value = current
    }

    fun commitReorder() {
        val reordered = optimisticOrder.value ?: return
        viewModelScope.launch {
            repository.reorderInterviews(reordered.map { it.id })
            optimisticOrder.value = null
        }
    }

    fun createInterview(title: String, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            repository.createInterview(title).onSuccess { newId ->
                errorState.value = null
                onCreated(newId)
            }.onFailure { err ->
                errorState.value = err.message ?: "No se pudo crear la entrevista."
            }
        }
    }

    fun setStreetModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setStreetModeEnabled(enabled)
        }
    }

    fun setStreetBrightnessBoost(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setStreetBrightnessBoost(enabled)
        }
    }

    fun startShareAppAndData() {
        val manager = backupTransferManager ?: return
        if (transferState.value is TransferUiState.InProgress) return

        viewModelScope.launch {
            transferState.value = TransferUiState.InProgress(
                title = "Preparando paquete",
                message = "Verificando instalador de la aplicación...",
                progress = 0.04f
            )
            when (val resolution = manager.resolveApkForExport()) {
                is ApkResolution.SingleInstalledApk -> {
                    generateTransferPackage(manager, ApkInputSource.InstalledFile(resolution.file))
                }

                is ApkResolution.SplitOrUnavailable -> {
                    transferState.value = TransferUiState.NeedsManualApk(resolution.reason)
                }
            }
        }
    }

    fun onManualApkSelected(uri: Uri?) {
        val manager = backupTransferManager ?: return
        if (uri == null) {
            if (transferState.value is TransferUiState.NeedsManualApk) {
                transferState.value = TransferUiState.Idle
            }
            return
        }
        viewModelScope.launch {
            generateTransferPackage(manager, ApkInputSource.ManualUri(uri))
        }
    }

    private suspend fun generateTransferPackage(
        manager: BackupTransferManager,
        apkSource: ApkInputSource
    ) {
        transferState.value = TransferUiState.InProgress(
            title = "Creando paquete ZIP",
            message = "Preparando archivos...",
            progress = 0.08f
        )
        manager.buildTransferPackage(apkSource) { progress ->
            transferState.value = TransferUiState.InProgress(
                title = "Creando paquete ZIP",
                message = progress.message,
                progress = progress.fraction
            )
        }.onSuccess { summary ->
            transferState.value = TransferUiState.ExportReady(summary)
        }.onFailure { error ->
            transferState.value = TransferUiState.Error(
                title = "No se pudo crear el paquete",
                message = error.message ?: "Ocurrió un error al generar el archivo ZIP."
            )
        }
    }

    fun saveExportPackageToUri(targetUri: Uri?) {
        val manager = backupTransferManager ?: return
        val current = transferState.value as? TransferUiState.ExportReady ?: return
        if (targetUri == null) return

        viewModelScope.launch {
            transferState.value = TransferUiState.InProgress(
                title = "Guardando paquete ZIP",
                message = "Copiando archivo al almacenamiento seleccionado...",
                progress = null
            )
            manager.savePackageToUri(current.summary.packageFile, targetUri)
                .onSuccess {
                    transferState.value = TransferUiState.Completed(
                        title = "Paquete guardado",
                        message = "El archivo ZIP con el instalador y tus entrevistas se guardó correctamente."
                    )
                }
                .onFailure { error ->
                    transferState.value = TransferUiState.Error(
                        title = "No se pudo guardar el paquete",
                        message = error.message ?: "Ocurrió un error al guardar el archivo ZIP."
                    )
                }
        }
    }

    fun importBackupFromUri(uri: Uri?) {
        val manager = backupTransferManager ?: return
        if (uri == null) return
        if (transferState.value is TransferUiState.InProgress) return

        viewModelScope.launch {
            transferState.value = TransferUiState.InProgress(
                title = "Importando respaldo",
                message = "Leyendo archivo seleccionado...",
                progress = 0.05f
            )
            manager.importBackupFromUri(uri) { progress ->
                transferState.value = TransferUiState.InProgress(
                    title = "Importando respaldo",
                    message = progress.message,
                    progress = progress.fraction
                )
            }.onSuccess { summary ->
                transferState.value = TransferUiState.Completed(
                    title = "Datos importados",
                    message = "Se restauraron ${summary.interviewCount} entrevistas, " +
                        "${summary.questionCount} preguntas y ${summary.mediaCount} archivos multimedia."
                )
            }.onFailure { error ->
                transferState.value = TransferUiState.Error(
                    title = "No se pudo importar",
                    message = error.message ?: "El respaldo no es válido o está dañado. Tus datos actuales se conservaron."
                )
            }
        }
    }

    fun dismissTransferDialog() {
        if (transferState.value is TransferUiState.InProgress) return
        transferState.value = TransferUiState.Idle
        viewModelScope.launch {
            backupTransferManager?.cleanupTempFiles()
        }
    }

    fun clearError() {
        errorState.value = null
    }

    companion object {
        fun provideFactory(
            repository: InterviewRepository,
            preferences: UserPreferencesRepository,
            backupTransferManager: BackupTransferManager? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(repository, preferences, backupTransferManager) as T
            }
        }
    }
}
