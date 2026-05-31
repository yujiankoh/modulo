package com.example.modulo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

class AppViewModel(application: Application) : AndroidViewModel(application) {
    var isDriveSyncEnabled = false
        private set

    fun setDriveSync(isEnabled: Boolean) {
        isDriveSyncEnabled = isEnabled
    }

    private val localSaveHelper = LocalSaveHelper(application)

    private val _appData = MutableStateFlow(localSaveHelper.loadData())
    val appData = _appData.asStateFlow()

    private val _syncState = MutableStateFlow(SyncState.SYNCED)
    val syncState = _syncState.asStateFlow()

    // Only sync after a moment of inactivity
    private var delaySync: Job? = null

    var syncingHelper : SyncingHelper? = null

    // TODO: other functions to change appdata
    fun addTask(title: String, type: String, deadline: String, isCompleted: Boolean) {
        val newTask = Task(
            id = System.currentTimeMillis(),
            title = title,
            due = deadline,
            type = type,
            done = isCompleted)

        updateData { currentData ->
            currentData.copy(tasks = currentData.tasks + newTask)
        }
    }

    fun toggleTaskCompletion(toggledTask: Task) {
        updateData { currentData ->
            val updatedTasks = currentData.tasks.map { task ->
                if (task == toggledTask) {
                    task.copy(done = !task.done)
                } else {
                    task
                }
            }

            currentData.copy(tasks = updatedTasks)
        }
    }

    private fun updateData(updateFunction: (AppData) -> AppData) {
        _appData.value = updateFunction(_appData.value)

        localSaveHelper.saveData(_appData.value)

        if (isDriveSyncEnabled) {
            // Reset state to UNSYNCED while user is interacting
            _syncState.value = SyncState.UNSYNCED

            // Cancel previous delay and start a new one
            delaySync?.cancel()
            delaySync = viewModelScope.launch {
                delay(1000L) // Wait for 1s of inactivity
                triggerDriveSync()
            }
        }
    }

    private suspend fun triggerDriveSync() {
        _syncState.value = SyncState.SYNCING

        val jsonPayload = syncJsonParser.encodeToString(_appData.value)

        val success = syncingHelper?.uploadAppData(jsonPayload) == true

        if (success) {
            _syncState.value = SyncState.SYNCED
        } else {
            _syncState.value = SyncState.UNSYNCED
        }
    }
}