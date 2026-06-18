package com.example.modulo

import android.app.Application
import android.content.Context
import android.util.Base64
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
import com.example.modulo.helpers.NetworkResult
import com.example.modulo.helpers.SyncingHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "ViewModel"

// Flags in device hard drive
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
val HAS_SEEN_TUTORIAL = booleanPreferencesKey("has_seen_tutorial")
val IS_DRIVE_SYNC_ENABLED = booleanPreferencesKey("is_drive_sync_enabled")

class AppViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    // Stores the helper for local save
    private val localSaveHelper = LocalSaveHelper(application)
    // Stores the helper for syncing
    var syncingHelper: SyncingHelper? = null
    // Stores the helper for parsing timetable
    private val parsingHelper = com.example.modulo.helpers.ParsingHelper()

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

    // TODO: load based on last write
    // Stores the app data, load based on local save
    private val _appData = MutableStateFlow(localSaveHelper.loadData())
    val appData = _appData.asStateFlow()

    // Stores the current syncing progress
    private val _syncState = MutableStateFlow(SyncState.SYNCED)
    val syncState = _syncState.asStateFlow()

    // Stores the current startup state
    private val _startupState = MutableStateFlow(StartupState.LOADING)
    val startupState = _startupState.asStateFlow()

    // Stores the status of timetable parsing
    private val _timetableState = MutableStateFlow<TimetableState>(TimetableState.Idle)
    val timetableState = _timetableState.asStateFlow()

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
    fun addTask(module: Module?, title: String, type: String, deadline: String, isCompleted: Boolean) {
        val currentTime = System.currentTimeMillis()
        val newTask = Task(
            id = currentTime,
            module = if (module == null) {
                    ""
                } else if (module.code != "") {
                    module.code
                } else {
                    module.name
                },
            title = title,
            due = deadline,
            type = type,
            done = isCompleted

        )

        updateData { currentData ->
            currentData.copy(tasks = currentData.tasks + newTask)
        }
    }

    fun completeTask(toggledTask: Task) {
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

    fun deleteTask(deletedTask: Task) {
        updateData { currentData ->
            currentData.copy(
                tasks = currentData.tasks.filter { it != deletedTask }
            )
        }
    }

    // function that both updates local save and Google Drive
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

        val success = syncingHelper?.uploadAppData(_appData.value) == true

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
            val downloadedData = helper.downloadAppData()

            if (downloadedData != null) {
                try {
                    _appData.value = downloadedData
                    localSaveHelper.saveData(downloadedData)

                    Log.d(TAG, "Data successfully downloaded and saved")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse downloaded data", e)
                }
            }
        }
    }

    fun clearTimetableState() {
        _timetableState.value = TimetableState.Idle
    }

    fun uploadTimetable(imageBytes: ByteArray, mimeType: String, educationLevel: String) {
        _timetableState.value = TimetableState.Processing;
        viewModelScope.launch {
            try {
                val base64String = Base64.encodeToString(imageBytes, Base64.NO_WRAP)

                val parsingData = ParsingData(
                    image = base64String,
                    mimeType = mimeType,
                    educationLevel = educationLevel
                )

                when (val result = parsingHelper.parseTimetable(parsingData)) {
                    is NetworkResult.Success -> {
                        _timetableState.value = TimetableState.ReviewData(result.data)
                    }
                    is NetworkResult.Failure -> {
                        val message = when (result.statusCode) {
                            429 -> "Parsing limit reached for today, try again tomorrow."
                            504 -> "Taking too long, please retry."
                            400 -> "Invalid image format sent to server. Please try another image."
                            else -> "Couldn't read the timetable, try another photo."
                        }
                        _timetableState.value = TimetableState.Error(message)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error during upload", e)
                _timetableState.value = TimetableState.Error("An unexpected error occurred. Please try again.")
            }
        }
    }

    fun saveTimetable(timetable: Timetable) {
        updateData { currentData ->
            currentData.copy(timetable = timetable)
        }
        _timetableState.value = TimetableState.Idle
    }
}

sealed interface TimetableState {
    object Idle : TimetableState
    object Processing : TimetableState
    data class ReviewData(val timetable: Timetable) : TimetableState
    data class Error(val message: String) : TimetableState
}