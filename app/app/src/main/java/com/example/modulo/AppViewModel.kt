package com.example.modulo

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.modulo.helpers.LocalSaveHelper
import com.example.modulo.helpers.SyncingHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import java.time.Instant

class AppViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val TAG = "ViewModel"

    // Stores the helper for local save
    private val localSaveHelper = LocalSaveHelper(application)

    // Stores the helper for syncing
    var syncingHelper: SyncingHelper? = null

    // Stores user email for authentication
    fun setUserEmail(email: String) {
        savedStateHandle["email"] = email
    }
    fun getUserEmail(): String {
        return savedStateHandle["email"] ?: ""
    }

    // Stores whether user is using Google Drive Sync
    private val _isDriveSyncEnabled = MutableStateFlow(false)
    val isDriveSyncEnabled = _isDriveSyncEnabled.asStateFlow()

    // Stores the app data, load based on local save
    private val _appData = MutableStateFlow(localSaveHelper.loadData())
    val appData = _appData.asStateFlow()

    // Stores the current syncing progress
    private val _syncState = MutableStateFlow(SyncState.SYNCED)
    val syncState = _syncState.asStateFlow()

    // Only sync after a moment of inactivity
    private var delaySync: Job? = null

    fun setDriveSyncEnabled(enabled: Boolean) {
        _isDriveSyncEnabled.value = enabled
    }

    // TODO: other functions to change appdata
    fun addTask(moduleCode: String, title: String, type: String, deadline: String, isCompleted: Boolean) {
        val currentTime = System.currentTimeMillis()

        val newTask = Task(
            id = currentTime,
            moduleCode = moduleCode,
            title = title,
            due = deadline,
            type = type,
            done = isCompleted

        )

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

        if (isDriveSyncEnabled.value) {
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

    fun downloadFromDrive() {
        val helper = syncingHelper
        if (helper == null) {
            Log.e(TAG, "SyncingHelper is not initialized yet")
            return
        }

        viewModelScope.launch {
            val jsonString = helper.downloadAppData()

            if (jsonString != null) {
                try {
                    val downloadedData = syncJsonParser.decodeFromString<AppData>(jsonString)

                    _appData.value = downloadedData
                    localSaveHelper.saveData(downloadedData)

                    Log.d(TAG, "Data successfully downloaded and saved")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse downloaded data")
                }
            }
        }
    }
}