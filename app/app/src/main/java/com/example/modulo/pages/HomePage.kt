package com.example.modulo.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.modulo.AppViewModel
import com.example.modulo.SyncState
import com.example.modulo.Task
import java.text.SimpleDateFormat
import java.util.Locale

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center),
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
                    onToggle = { clickedTask ->
                        viewModel.toggleTaskCompletion(clickedTask)
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

@Composable
fun TaskCard(
    task: Task,
    onToggle: (Task) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = task.title,
                        textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None
                    )

                    Checkbox(
                        checked = task.done,
                        onCheckedChange = { onToggle(task) },
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = task.type)
                    Text(text = "Due: ${formatDate(task.due)}")
                }
            }
        }
    }
}

fun formatDate(dataDate: String): String {
    if (dataDate.isBlank()) return ""

    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        val date = parser.parse(dataDate)

        if (date != null) formatter.format(date) else dataDate
    } catch (e: Exception) {
        dataDate
    }
}