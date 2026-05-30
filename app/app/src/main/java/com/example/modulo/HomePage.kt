package com.example.modulo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun HomePage(
    isDriveSyncEnabled: Boolean,
    viewModel: AppViewModel = viewModel()
) {
    val appData by viewModel.appData.collectAsState()
    val syncState by viewModel.syncState.collectAsState()

    val statusText = when (syncState) {
        SyncState.UNSYNCED -> "Unsynced changes..."
        SyncState.SYNCING -> "Syncing to Drive..."
        SyncState.SYNCED -> "All changes synced"
    }

    LaunchedEffect(isDriveSyncEnabled) {
        viewModel.setDriveSync(isDriveSyncEnabled);
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("HomePage")

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Drive Sync is ${if (isDriveSyncEnabled) "ON" else "OFF"}")

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Count: ${appData.counter}")

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                viewModel.incrementCounter()
            }
        ) {
            Text("Increase Counter")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isDriveSyncEnabled) {
            Text(text = statusText)
        }

    }
}