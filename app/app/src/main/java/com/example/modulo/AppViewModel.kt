package com.example.modulo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * This enum class encapsulates the states of syncing
 */
enum class SyncState{
    UNSYNCED, SYNCING, SYNCED
}

/**
 * This data class encapsulates all the information that needs to be synced
 */
data class AppSyncData(
    val counter: Int = 0,
    // val tasks: List<String> = emptyList(),
    // val settings...
)

class AppViewModel : ViewModel() {
    private val _syncData = MutableStateFlow(AppSyncData())
    val syncData = _syncData.asStateFlow()

    private val _syncState = MutableStateFlow(SyncState.IDLE)
    val syncState = _syncState.asStateFlow()

    // Only sync after a moment of inactivity
    private var delaySync: Job? = null

    // Temporary tester
    fun incrementCounter() {
        updateData { appData -> appData.copy(counter = appData.counter + 1) }
    }

    // TODO: other functions to change appdata

    private fun updateData(updateFunction: (AppSyncData) -> AppSyncData) {
        _syncData.value = updateFunction(_syncData.value)

        // Reset state to UNSYNCED while user is interacting
        _syncState.value = SyncState.UNSYNCED

        // Cancel previous delay and start a new one
        delaySync?.cancel()
        delaySync = viewModelScope.launch {
            delay(1000L) // Wait for 1s of inactivity
            triggerDriveSync()
        }
    }

    private suspend fun triggerDriveSync() {
        _syncState.value = SyncState.SYNCING

        // TODO: send JSON to Drive

        _syncState.value = SyncState.SYNCED
    }
}