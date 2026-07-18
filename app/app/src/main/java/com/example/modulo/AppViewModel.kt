package com.example.modulo

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import com.example.modulo.helpers.CityLogicHelper
import com.example.modulo.helpers.LocalSaveHelper
import com.example.modulo.helpers.NetworkHelper
import com.example.modulo.helpers.NetworkResult
import com.example.modulo.helpers.NotesHelper
import com.example.modulo.helpers.ParsingHelper
import com.example.modulo.helpers.SyncingHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "ViewModel"

// Flags in device hard drive
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
val HAS_SEEN_TUTORIAL = booleanPreferencesKey("has_seen_tutorial")
val IS_DRIVE_SYNC_ENABLED = booleanPreferencesKey("is_drive_sync_enabled")

class AppViewModel @JvmOverloads constructor(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    private val parsingHelper: ParsingHelper = ParsingHelper()
) : AndroidViewModel(application) {
    // Helpers
    private val localSaveHelper = LocalSaveHelper(application)
    private var syncingHelper: SyncingHelper? = null
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
    
    private val _notesData = MutableStateFlow(NotesData())
    val notesData = _notesData.asStateFlow()

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
        // preload local city
        reconcileCity()
        startUpChecks()
        autoSync()
    }

    private fun reconcileCity() {
        val current = _appData.value
        val reconciled = CityLogicHelper.reconcile(current.city, current.studySessions)
        if (reconciled !== current.city) {
            updateData { it.copy(city = reconciled) }
        }
    }

    // Stores user email for authentication
    fun setUserEmail(email: String) {
        savedStateHandle["email"] = email
    }
    fun getUserEmail(): String {
        return savedStateHandle["email"] ?: ""
    }

    private fun checkHandbookState() {
        if (_appData.value.educationLevel == null) {
            _startupState.value = StartupState.HANDBOOK
        } else {
            _startupState.value = StartupState.READY
        }
    }

    fun onAuthenticationSuccess(context: Context, email: String) {
        setUserEmail(email)
        saveSyncPreference(true)
        syncingHelper = SyncingHelper.getSyncService(context, email)

        viewModelScope.launch {
            _syncState.value = SyncState.SYNCING
            resolveConflict(syncingHelper!!)
            checkHandbookState()
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

        // match city building scope
        reconcileCity()
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
                        checkHandbookState()
                        return@launch
                    }

                    attemptSilentSignIn(
                        onComplete = {
                            _syncState.value = SyncState.SYNCED
                            checkHandbookState()
                        }
                    )
                }
                false -> {
                    // User selected local save, go to Home
                    _isDriveSyncEnabled.value = false
                    checkHandbookState()
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

            // If local save, go straight to home check
            if (!enabled) {
                _syncState.value = SyncState.OFFLINE
                checkHandbookState()
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

    fun isHigherEducation(): Boolean {
        val currLevel = _appData.value.educationLevel
        return currLevel == "university" || currLevel == "poly"
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
                delay(1000L.milliseconds) // Wait for 1s of inactivity
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

    // Show/hide a module (by its label) on the dashboard
    fun setModuleHidden(label: String, hide: Boolean) {
        updateData { currentData ->
            val hidden = currentData.hiddenModules
            val updated = if (hide) {
                if (label in hidden) hidden else hidden + label
            } else {
                hidden.filter { it != label }
            }
            currentData.copy(hiddenModules = updated)
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

    fun upsertGrade(id: String?, module: String, credits: Double, grade: String, su: Boolean) {
        val safeCredits = if (credits.isFinite() && credits > 0) credits else 0.0
        updateData { currentData ->
            when {
                grade.isBlank() && id != null ->
                    currentData.copy(grades = currentData.grades.filter { it.id != id })

                id != null -> {
                    val updated = currentData.grades.map {
                        if (it.id == id) it.copy(credits = safeCredits, grade = grade, su = su) else it
                    }
                    currentData.copy(grades = updated)
                }

                else -> {
                    val entry = Grade(module = module, credits = safeCredits, grade = grade, su = su)
                    currentData.copy(grades = currentData.grades + entry)
                }
            }
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

    fun startOrResumeTimer() {
        if (sessionStartTime == null) {
            sessionStartTime = Instant.now().toString()
            hasSessionStarted = true
        }
        isTimerRunning = true
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L.milliseconds) // Wait 1 second
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
                // add the session and update city
                val withSession = currentData.copy(studySessions = currentData.studySessions + newSession)
                withSession.copy(city = CityLogicHelper.reconcile(withSession.city, withSession.studySessions))
            }
        }

        // Clear the timer state
        discardSession()
    }

    fun saveHandbook(newHandbook: Handbook) {
        updateData { currentData ->
            val updatedHandbooks = if (currentData.educationLevel != null) {
                val currHandbook = Handbook(
                    id = currentData.handbookId,
                    educationLevel = currentData.educationLevel,
                    academicYear = currentData.academicYear,
                    semester = currentData.semester,
                    termStart = currentData.termStart,
                    termEnd = currentData.termEnd,
                    breaks = currentData.breaks,
                    tasks = currentData.tasks,
                    grades = currentData.grades,
                    hiddenModules = currentData.hiddenModules,
                    timetable = currentData.timetable
                )
                currentData.otherHandbooks + currHandbook
            } else {
                currentData.otherHandbooks
            }

            currentData.copy(
                handbookId = newHandbook.id,
                educationLevel = newHandbook.educationLevel,
                academicYear = newHandbook.academicYear,
                semester = newHandbook.semester,
                termStart = newHandbook.termStart,
                termEnd = newHandbook.termEnd,
                breaks = newHandbook.breaks,
                tasks = emptyList(),
                grades = emptyList(),
                timetable = null,
                hiddenModules = emptyList(),
                otherHandbooks = updatedHandbooks
            )
        }


        if (_startupState.value == StartupState.HANDBOOK) {
            _startupState.value = StartupState.READY
        }
    }

    fun swapHandbook(handbook: Handbook) {
        updateData { currentData ->
            val updatedHandbooks = currentData.otherHandbooks.toMutableList()
            updatedHandbooks.remove(handbook)

            if (currentData.educationLevel != null) {
                val currHandbook = Handbook(
                    id = currentData.handbookId,
                    educationLevel = currentData.educationLevel,
                    academicYear = currentData.academicYear,
                    semester = currentData.semester,
                    termStart = currentData.termStart,
                    termEnd = currentData.termEnd,
                    breaks = currentData.breaks,
                    tasks = currentData.tasks,
                    grades = currentData.grades,
                    hiddenModules = currentData.hiddenModules,
                    timetable = currentData.timetable
                )
                updatedHandbooks.add(currHandbook)
            }

            currentData.copy(
                educationLevel = handbook.educationLevel,
                academicYear = handbook.academicYear,
                semester = handbook.semester,
                termStart = handbook.termStart,
                termEnd = handbook.termEnd,
                breaks = handbook.breaks,
                tasks = handbook.tasks,
                grades = handbook.grades,
                timetable = handbook.timetable,
                hiddenModules = handbook.hiddenModules,
                otherHandbooks = updatedHandbooks
            )
        }
    }

    fun deleteHandbook(handbook: Handbook) {
        updateData { currentData ->
            currentData.copy(
                otherHandbooks = currentData.otherHandbooks.filter { it != handbook }
            )
        }
    }

    fun updateHandbook(handbook: Handbook) {
        updateData { currentData ->
            currentData.copy(
                educationLevel = handbook.educationLevel,
                academicYear = handbook.academicYear,
                semester = handbook.semester,
                termStart = handbook.termStart,
                termEnd = handbook.termEnd,
                breaks = handbook.breaks,
            )
        }
    }
    
    fun notesGated(): Boolean = !_isDriveSyncEnabled.value

    private fun setNotesError(message: String?) {
        _notesData.value = _notesData.value.copy(loading = false, error = message)
    }

    fun clearNotesError() = setNotesError(null)

    fun loadNotes() {
        val current = _notesData.value
        if (current.notes != null || current.loading || notesGated()) return
        val helper = syncingHelper ?: run {
            setNotesError("Google sign-in expired — please reconnect and try again.")
            return
        }
        _notesData.value = current.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                _notesData.value = NotesData(notes = helper.listNotes())
            } catch (e: Exception) {
                setNotesError(e.message)
            }
        }
    }

    fun refreshNotes() {
        _notesData.value = NotesData()
        loadNotes()
    }

    fun uploadNote(uri: Uri, module: String, onResult: (String?) -> Unit = {}) {
        val helper = syncingHelper ?: run {
            onResult("Google sign-in expired — please reconnect and try again.")
            return
        }
        val resolver = getApplication<Application>().contentResolver
        val mimeType = resolver.getType(uri) ?: ""
        val (name, size) = queryNameSize(uri)

        val check = NotesHelper.validateNoteFile(NotesHelper.NoteFile(name, size, mimeType))
        if (check is NotesHelper.Validation.Invalid) {
            onResult(check.reason)
            return
        }

        viewModelScope.launch {
            try {
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw Exception("That file couldn't be read.")
                val created = helper.uploadNote(bytes, name, mimeType, module, _appData.value.handbookId)
                _notesData.value = _notesData.value.let {
                    if (it.notes != null) it.copy(notes = it.notes + created) else it
                }
                onResult(null)
            } catch (e: Exception) {
                onResult(e.message)
            }
        }
    }

    fun renameNote(id: String, newName: String, onResult: (String?) -> Unit = {}) {
        val helper = syncingHelper ?: run {
            onResult("Google sign-in expired — please reconnect and try again.")
            return
        }
        viewModelScope.launch {
            try {
                val updated = helper.renameNote(id, newName)
                _notesData.value = _notesData.value.let {
                    it.copy(notes = it.notes?.map { note -> if (note.id == id) updated else note })
                }
                onResult(null)
            } catch (e: Exception) {
                onResult(e.message)
            }
        }
    }

    fun deleteNote(id: String) {
        val helper = syncingHelper ?: run {
            setNotesError("Google sign-in expired — please reconnect and try again.")
            return
        }
        viewModelScope.launch {
            try {
                helper.deleteNote(id)
                _notesData.value = _notesData.value.let {
                    it.copy(notes = it.notes?.filter { note -> note.id != id }, error = null)
                }
            } catch (e: Exception) {
                setNotesError(e.message)
            }
        }
    }

    suspend fun downloadNote(id: String): ByteArray? {
        val helper = syncingHelper ?: return null
        return try {
            helper.downloadNote(id)
        } catch (e: Exception) {
            setNotesError(e.message)
            null
        }
    }

    private fun queryNameSize(uri: Uri): Pair<String, Long?> {
        var name = ""
        var size: Long? = null
        getApplication<Application>().contentResolver
            .query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: ""
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                }
            }
        return name to size
    }
}