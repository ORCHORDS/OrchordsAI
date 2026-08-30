package com.orchords.orchordsai.ui.pages.extensions.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import com.orchords.orchordsai.data.db.entity.WorkspaceEntity
import com.orchords.orchordsai.data.repository.WorkspaceRepository
import com.orchords.workspace.RootfsInstallProgress
import com.orchords.workspace.RootfsInstallStage
import com.orchords.workspace.WorkspaceFileEntry
import com.orchords.workspace.WorkspaceCommandResult
import com.orchords.workspace.WorkspaceStorageArea

class WorkspaceDetailVM(
    private val id: String,
    private val repository: WorkspaceRepository,
    private val terminalSessionManager: WorkspaceTerminalSessionManager,
) : ViewModel() {
    private val _state = MutableStateFlow(WorkspaceDetailState())
    val state = _state.asStateFlow()

    private val _terminalState = MutableStateFlow(WorkspaceTerminalState())
    val terminalState = _terminalState.asStateFlow()

    private val _installProgress = MutableStateFlow<RootfsInstallProgress?>(null)
    val installProgress = _installProgress.asStateFlow()

    private val _installError = MutableStateFlow<String?>(null)
    val installError = _installError.asStateFlow()

    init {
        loadWorkspace()
        refresh()
    }

    fun selectArea(area: WorkspaceStorageArea) {
        _state.update {
            it.copy(
                area = area,
                path = "",
                entries = emptyList(),
                error = null,
            )
        }
        refresh()
    }

    fun open(entry: WorkspaceFileEntry) {
        if (!entry.isDirectory) return
        _state.update { it.copy(path = entry.path, entries = emptyList(), error = null) }
        refresh()
    }

    fun goUp() {
        val path = state.value.path
        if (path.isBlank()) return
        _state.update {
            it.copy(
                path = path.substringBeforeLast('/', missingDelimiterValue = ""),
                entries = emptyList(),
                error = null,
            )
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                repository.listFiles(
                    id = id,
                    area = state.value.area,
                    path = state.value.path,
                )
            }.onSuccess { entries ->
                _state.update { it.copy(entries = entries, loading = false) }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        entries = emptyList(),
                        loading = false,
                        error = error.message ?: "Failed to load workspace files",
                    )
                }
            }
        }
    }

    fun delete(entry: WorkspaceFileEntry) {
        viewModelScope.launch {
            runCatching {
                repository.deleteFile(
                    id = id,
                    area = state.value.area,
                    path = entry.path,
                    recursive = entry.isDirectory,
                )
            }.onSuccess {
                refresh()
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Delete failed") }
            }
        }
    }

    fun importFile(inputStream: InputStream, fileName: String) {
        viewModelScope.launch {
            runCatching {
                repository.importFile(
                    id = id,
                    area = state.value.area,
                    destinationPath = state.value.path,
                    fileName = fileName,
                    inputStream = inputStream,
                )
            }.onSuccess {
                refresh()
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Import failed") }
            }
        }
    }

    fun exportFile(entry: WorkspaceFileEntry, outputStream: OutputStream) {
        viewModelScope.launch {
            runCatching {
                repository.exportFile(
                    id = id,
                    area = state.value.area,
                    path = entry.path,
                    outputStream = outputStream,
                )
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Export failed") }
            }
        }
    }

    /**
     */
    fun exportToCacheFile(entry: WorkspaceFileEntry, cacheDir: File, onReady: (File) -> Unit) {
        viewModelScope.launch {
            runCatching {
                val dir = File(cacheDir, "workspace_share").apply { mkdirs() }
                val file = File(dir, entry.name)
                file.outputStream().use { output ->
                    repository.exportFile(
                        id = id,
                        area = state.value.area,
                        path = entry.path,
                        outputStream = output,
                    )
                }
                file
            }.onSuccess(onReady).onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Export failed") }
            }
        }
    }

    fun setToolApproval(toolName: String, needsApproval: Boolean) {
        viewModelScope.launch {
            val workspace = state.value.workspace ?: return@launch
            repository.setToolApproval(workspace.id, toolName, needsApproval)
            loadWorkspace()
        }
    }

    fun installRootfs(url: String) {
        viewModelScope.launch {
            _installError.value = null
            val workspace = state.value.workspace ?: return@launch
            _installProgress.value = RootfsInstallProgress(stage = RootfsInstallStage.DOWNLOADING)
            try {
                terminalSessionManager.closeWorkspace(workspace.root)
                repository.installRootfs(workspace.id, url) { progress ->
                    _installProgress.value = progress
                }
                loadWorkspace()
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (error: Throwable) {
                _installError.value = error.message ?: "Rootfs installation failed"
            } finally {
                _installProgress.value = null
            }
        }
    }

    fun dismissInstallError() {
        _installError.value = null
    }

    fun executeTerminalCommand(command: String) {
        val trimmed = command.trim()
        if (trimmed.isBlank()) return
        val previous = _terminalState.getAndUpdate { state ->
            if (state.running) {
                state
            } else {
                state.copy(
                    running = true,
                    input = "",
                    history = state.history + WorkspaceTerminalEntry.Command(trimmed),
                )
            }
        }
        if (previous.running) return
        viewModelScope.launch {
            runCatching {
                repository.executeCommand(id, trimmed)
            }.onSuccess { result ->
                _terminalState.update {
                    it.copy(
                        running = false,
                        history = it.history + WorkspaceTerminalEntry.Result(result),
                    )
                }
            }.onFailure { error ->
                _terminalState.update {
                    it.copy(
                        running = false,
                        history = it.history + WorkspaceTerminalEntry.Error(error.message ?: "Command execution failed"),
                    )
                }
            }
        }
    }

    fun updateTerminalInput(input: String) {
        _terminalState.update { it.copy(input = input) }
    }

    fun clearTerminal() {
        _terminalState.update { it.copy(history = emptyList()) }
    }

    private fun loadWorkspace() {
        viewModelScope.launch {
            val workspace = repository.getById(id)
            _state.update { it.copy(workspace = workspace) }
        }
    }
}

data class WorkspaceDetailState(
    val workspace: WorkspaceEntity? = null,
    val area: WorkspaceStorageArea = WorkspaceStorageArea.FILES,
    val path: String = "",
    val entries: List<WorkspaceFileEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

data class WorkspaceTerminalState(
    val input: String = "",
    val running: Boolean = false,
    val history: List<WorkspaceTerminalEntry> = emptyList(),
)

sealed interface WorkspaceTerminalEntry {
    data class Command(val command: String) : WorkspaceTerminalEntry
    data class Result(val result: WorkspaceCommandResult) : WorkspaceTerminalEntry
    data class Error(val message: String) : WorkspaceTerminalEntry
}
