package com.example.modulo

import android.app.Application
import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.example.modulo.helpers.NetworkHelper
import com.example.modulo.helpers.NetworkResult
import com.example.modulo.helpers.ParsingHelper
import com.example.modulo.helpers.SyncingHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

private const val TAG = "ViewModel"

// Flags in device hard drive
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
val HAS_SEEN_TUTORIAL = booleanPreferencesKey("has_seen_tutorial")
val IS_DRIVE_SYNC_ENABLED = booleanPreferencesKey("is_drive_sync_enabled")

class AppViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    // Helpers
    private val localSaveHelper = LocalSaveHelper(application)
    private var syncingHelper: SyncingHelper? = null
    private val parsingHelper = ParsingHelper()
    private val networkMonitor = NetworkHelper(application)

    // State Flows
    private val _isDriveSyncEnabled = MutableStateFlow(false)
    val isDriveSyncEnabled = _isDriveSyncEnabled.asStateFlow()

    private val _appData = MutableStateFlow(localSaveHelper.loadData())
    val appData = _appData.asStateFlow()

    private val _syncState = MutableStateFlow(SyncState.OFFLINE)
    val syncState = _syncState.asStateFlow()

    private val _startupState = MutableStateFlow(StartupState.LOADING)
    val startupState = _startupState.asStateFlow()

    private val _timetableState = MutableStateFlow<TimetableState>(TimetableState.Idle)
    val timetableState = _timetableState.asStateFlow()

    private val _hasInternet = MutableStateFlow(false)

    // Keep track of time in study session
    var elapsedSeconds by mutableLongStateOf(0L)
        private set
    var isTimerRunning by mutableStateOf(false)
        private set
    var hasSessionStarted by mutableStateOf(false)
        private set
    private var sessionStartTime by mutableStateOf<String?>(null)
    private var sessionEndTime by mutableStateOf<String?>(null)

    // Delay Jobs
    private var delaySync: Job? = null
    private var timerJob: Job? = null

    // Functions for startup
    init {
        startUpChecks()
        autoSync()
    }

    // Stores user email for authentication
    fun setUserEmail(email: String) {
        savedStateHandle["email"] = email
    }
    fun getUserEmail(): String {
        return savedStateHandle["email"] ?: ""
    }

    fun onAuthenticationSuccess(context: Context, email: String) {
        setUserEmail(email)
        saveSyncPreference(true)
        syncingHelper = SyncingHelper.getSyncService(context, email)

        viewModelScope.launch {
            _syncState.value = SyncState.SYNCING
            resolveConflict(syncingHelper!!)
            _startupState.value = StartupState.READY
        }
    }

    private suspend fun attemptSilentSignIn(onComplete: () -> Unit = {}) {
        val credentialManager = CredentialManager.create(getApplication())

        AuthenticationHelper.silentSignIn(
            context = getApplication(),
            credentialManager = credentialManager,
            onSuccess = { email ->
                setUserEmail(email)
                syncingHelper = SyncingHelper.getSyncService(getApplication(), email)

                viewModelScope.launch {
                    resolveConflict(syncingHelper!!)
                    onComplete()
                }
            },
            onFailure = {
                _startupState.value = StartupState.AUTHENTICATE
            }
        )
    }

    private suspend fun resolveConflict(helper: SyncingHelper) {
        val cloudData = helper.downloadAppData()
        val localData = _appData.value

        if (cloudData != null) {
            // Parse timestamps, defaulting to the start of time if missing
            val cloudTime = cloudData.updatedAt?.let { Instant.parse(it) } ?: Instant.MIN
            val localTime = localData.updatedAt?.let { Instant.parse(it) } ?: Instant.MIN

            if (cloudTime.isAfter(localTime)) {
                _appData.value = cloudData
                localSaveHelper.saveData(cloudData)
                Log.d(TAG, "Cloud data is newer. Downloaded from Drive.")
            } else if (localTime.isAfter(cloudTime)) {
                Log.d(TAG, "Local data is newer. Uploading to Drive.")
                uploadToDrive()
            } else {
                Log.d(TAG, "Data is completely in sync.")
            }
        } else {
            // No cloud data exists yet, push local up
            uploadToDrive()
        }
        _syncState.value = SyncState.SYNCED
    }

    fun startUpChecks() {
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

                    if (!networkMonitor.isConnected()) {
                        Log.d(TAG, "App launched offline. Bypassing Google Sign-In.")
                        _syncState.value = SyncState.OFFLINE
                        _startupState.value = StartupState.READY
                        return@launch
                    }

                    attemptSilentSignIn(
                        onComplete = {
                            _syncState.value = SyncState.SYNCED
                            _startupState.value = StartupState.READY
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

    private fun autoSync() {
        viewModelScope.launch {
            networkMonitor.isConnected.collect { hasInternet ->
                _hasInternet.value = hasInternet

                if (!isDriveSyncEnabled.value) return@collect

                if (hasInternet) {
                    if (syncingHelper == null) {
                        Log.d(TAG, "Internet reconnected, silent sign in")
                        attemptSilentSignIn()
                    } else {
                        _syncState.value = SyncState.UNSYNCED
                        resolveConflict(syncingHelper!!)
                    }
                } else {
                    // Internet lost
                    _syncState.value = SyncState.OFFLINE
                    delaySync?.cancel()
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

            // If local save, go straight to home
            if (!enabled) {
                _startupState.value = StartupState.READY
                _syncState.value = SyncState.OFFLINE
            }
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            AuthenticationHelper.signOut(context)

            setUserEmail("")
            saveSyncPreference(false)
            syncingHelper = null
        }
    }

    fun reAuthenticate() {
        _startupState.value = StartupState.AUTHENTICATE
    }

    // TODO: other functions to change appdata
    // function that both updates local save and Google Drive
    private fun updateData(updateFunction: (AppData) -> AppData) {
        _appData.value = updateFunction(_appData.value).copy(updatedAt = Instant.now().toString())
        localSaveHelper.saveData(_appData.value)

        if (_isDriveSyncEnabled.value && _hasInternet.value) {
            // Reset state to UNSYNCED while user is interacting
            _syncState.value = SyncState.UNSYNCED

            // Cancel previous delay and start a new one
            delaySync?.cancel()
            delaySync = viewModelScope.launch {
                delay(1000L) // Wait for 1s of inactivity
                uploadToDrive()
            }
        }
    }

    private suspend fun uploadToDrive() {
        if (!_hasInternet.value || !_isDriveSyncEnabled.value) return

        _syncState.value = SyncState.SYNCING
        val success = syncingHelper?.uploadAppData(_appData.value) == true
        if (success) _syncState.value = SyncState.SYNCED else _syncState.value = SyncState.UNSYNCED
    }

    fun addTask(newTask: Task) {
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

    fun clearTimetableState() {
        _timetableState.value = TimetableState.Idle
    }

    fun uploadTimetable(imageBytes: ByteArray, mimeType: String, educationLevel: String) {
        _timetableState.value = TimetableState.Processing
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
            currentData.copy(educationLevel = timetable.educationLevel, timetable = timetable)
        }
        _timetableState.value = TimetableState.Idle
    }

    fun saveTermStart(date: LocalDate?) {
        updateData { currentData ->
            currentData.copy(termStart = date.toString())
        }
    }

    fun saveTermEnd(date: LocalDate?) {
        updateData { currentData ->
            currentData.copy(termEnd = date.toString())
        }
    }

    fun startOrResumeTimer() {
        if (sessionStartTime == null) {
            sessionStartTime = Instant.now().toString()
            hasSessionStarted = true
        }
        isTimerRunning = true
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L) // Wait 1 second
                elapsedSeconds += 1
            }
        }
    }

    fun pauseTimer() {
        isTimerRunning = false
        timerJob?.cancel()
    }

    fun stopTimer() {
        pauseTimer()
        sessionEndTime = Instant.now().toString()
        hasSessionStarted = false
    }

    fun discardSession() {
        elapsedSeconds = 0L
        sessionStartTime = null
    }

    fun saveSession(rating: Int) {
        val start = sessionStartTime ?: Instant.now().toString()
        val end = sessionEndTime ?: Instant.now().toString()

        // Round to minutes
        val durationMins = (elapsedSeconds / 60.0).roundToInt()

        if (durationMins > 0) {
            val newSession = StudySession(
                start = start,
                end = end,
                durationMins = durationMins,
                rating = rating,
                createdAt = Instant.now().toString()
            )

            updateData { currentData ->
                currentData.copy(studySessions = currentData.studySessions + newSession)
            }
        }

        // Clear the timer state
        discardSession()
    }
}