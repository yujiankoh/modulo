package com.example.modulo.pages

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.modulo.AppViewModel
import com.example.modulo.SyncState
import com.example.modulo.Task

@Composable
fun HomePage(
    viewModel: AppViewModel,
) {
    // Collect info from the model
    val appData by viewModel.appData.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val isDriveSyncEnabled by viewModel.isDriveSyncEnabled.collectAsState()

    val statusText = when (syncState) {
        SyncState.UNSYNCED -> "Unsynced changes..."
        SyncState.SYNCING -> "Syncing to Drive..."
        SyncState.SYNCED -> "All changes synced"
    }

    var deletedTask by remember { mutableStateOf<Task?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    deletedTask = null;
                })
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text("HomePage")
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Drive Sync is ${if (isDriveSyncEnabled) "ON" else "OFF"}")
        Spacer(modifier = Modifier.height(16.dp))

        if (isDriveSyncEnabled) {
            Button(
                onClick ={ viewModel.downloadFromDrive() }
            ) {
                Text("Download from Drive")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("My Tasks:")
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            items(appData.tasks) { task ->
                TaskCard(
                    task = task,
                    showDelete = deletedTask == task,
                    onLongPress = { deletedTask = task },
                    onNormalPress = { deletedTask = null },
                    onToggle = { clickedTask ->
                        viewModel.completeTask(clickedTask)
                    },
                    onDelete = {
                        viewModel.deleteTask(task)
                        deletedTask = null
                    }
                )
            }
        }

        if (isDriveSyncEnabled) {
            Text(text = statusText)
        }

        Spacer(modifier = Modifier.height(48.dp))

    }
}