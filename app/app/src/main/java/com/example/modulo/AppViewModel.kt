package com.example.modulo

import androidx.annotation.RestrictTo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
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
 * This data class encapsulates all the information that needs to be saved
 */
data class AppData(
    // val tasks: List<String> = emptyList(),
    // val settings...
)

class AppViewModel : ViewModel() {
    var isDriveSyncEnabled = false
        private set

    fun setDriveSync(isEnabled: Boolean) {
        isDriveSyncEnabled = isEnabled
    }

    private val _appData = MutableStateFlow(AppData())
    val appData = _appData.asStateFlow()

    private val _syncState = MutableStateFlow(SyncState.SYNCED)
    val syncState = _syncState.asStateFlow()

    // Only sync after a moment of inactivity
    private var delaySync: Job? = null

    // TODO: load data based on local save / sync save based on time

    // TODO: other functions to change appdata

    private fun updateData(updateFunction: (AppData) -> AppData) {
        _appData.value = updateFunction(_appData.value)

        // TODO: update local save

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

        // TODO: send JSON to Drive

        _syncState.value = SyncState.SYNCED
    }
}