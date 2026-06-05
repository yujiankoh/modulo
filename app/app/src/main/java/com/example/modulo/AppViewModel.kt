package com.example.modulo

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.modulo.helpers.AuthenticationHelper
import com.example.modulo.helpers.LocalSaveHelper
import com.example.modulo.helpers.SyncingHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import java.time.Instant

// Flag in device hard drive
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
val HAS_SEEN_TUTORIAL = booleanPreferencesKey("has_seen_tutorial")
val IS_DRIVE_SYNC_ENABLED = booleanPreferencesKey("is_drive_sync_enabled")

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

    // Stores the current startup state
    private val _startupState = MutableStateFlow(StartupState.LOADING)
    val startupState = _startupState.asStateFlow()

    // Only sync after a moment of inactivity
    private var delaySync: Job? = null

    // Functions for startup
    init {
        startUpChecks()
    }

    private fun startUpChecks() {
        viewModelScope.launch {
            val prefs = getApplication<Application>().dataStore.data.first()
            val hasSeenTutorial = prefs[HAS_SEEN_TUTORIAL] ?: false
            val isSyncEnabled = prefs[IS_DRIVE_SYNC_ENABLED]

            // Check for first time users
            if (!hasSeenTutorial) {
                _startupState.value = StartupState.TUTORIAL
                return@launch
            }

            when (isSyncEnabled) {
                true -> {
                    // User selected sync, go to Silent Sign-in
                    _isDriveSyncEnabled.value = true

                    val credentialManager = CredentialManager.create(getApplication())

                    AuthenticationHelper.silentSignIn(
                        context = getApplication(),
                        credentialManager = credentialManager,
                        onSuccess = { email ->
                            setUserEmail(email)
                            syncingHelper = SyncingHelper.getSyncService(getApplication(), email)
                            _startupState.value = StartupState.READY
                        },
                        onFailure = {
                            _startupState.value = StartupState.AUTHENTICATE
                        }
                    )
                }
                false -> {
                    // User selected local save, go to Home
                    _isDriveSyncEnabled.value = false
                    _startupState.value = StartupState.READY
                }
                null -> {
                    // Completed tutorial but user did not select, go to Sign-in
                    _startupState.value = StartupState.SIGN_IN
                }
            }
        }
    }


    fun completeTutorial() {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { settings ->
                settings[HAS_SEEN_TUTORIAL] = true
            }
            _startupState.value = StartupState.SIGN_IN
        }
    }

    fun saveSyncPreference(enabled: Boolean) {
        viewModelScope.launch {
            getApplication<Application>().dataStore.edit { settings ->
                settings[IS_DRIVE_SYNC_ENABLED] = enabled
            }
            _isDriveSyncEnabled.value = enabled

            if (!enabled) {
                _startupState.value = StartupState.READY
            }
        }
    }

    fun navigateToHome() {
        _startupState.value = StartupState.READY
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
                    Log.e(TAG, "Failed to parse downloaded data", e)
                }
            }
        }
    }
}