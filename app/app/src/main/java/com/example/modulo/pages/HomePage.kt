package com.example.modulo.pages

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.modulo.AppViewModel
import com.example.modulo.R
import com.example.modulo.SyncState
import com.example.modulo.Task
import com.example.modulo.TimetableState
import java.text.SimpleDateFormat
import java.time.LocalTime
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@Composable
fun HomePage(
    viewModel: AppViewModel,
    onUploadTimetable: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    // Collect info from the model
    val appData by viewModel.appData.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val timetableState by viewModel.timetableState.collectAsState()

    var deletedTask by remember { mutableStateOf<Task?>(null) }

    val dueTasks = remember(appData.tasks) {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        // Calculate the cutoff date
        val cutoff = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            add(Calendar.DAY_OF_YEAR, 7)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }

        appData.tasks.filter { task ->
            if (task.done || task.due.isBlank()) return@filter false

            try {
                val dueDate = parser.parse(task.due)
                dueDate != null && !dueDate.after(cutoff.time)
            } catch (e: Exception) {
                false
            }
        }.sortedBy { it.due }
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        deletedTask = null
                    })
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Missing timetable banner
            if (appData.timetable == null && timetableState is TimetableState.Idle) {
                MissingTimetableBanner (onUploadClicked = onUploadTimetable)
            }

            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Greeting
                Column {
                    Text(
                        text = getGreeting(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "User", // You can replace this with viewModel.userName if you save it!
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Sync Icon + Settings / Profile Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp) // Adds a nice gap between the icon and button
                ) {
                    SyncIcon(state = syncState)

                    IconButton(
                        onClick = onSettingsClick
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.circle_user_round),
                            contentDescription = "Profile and Settings",
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Upcoming Deadlines: ${dueTasks.size}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(dueTasks) { task ->
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
                        },
                        dueText = formatRelativeDate(task.due)
                    )
                }
            }
        }
    }
}

fun getGreeting(): String {
    val currentHour = LocalTime.now().hour
    return when (currentHour) {
        in 0..11 -> "Good Morning,"
        in 12..17 -> "Good Afternoon,"
        else -> "Good Evening,"
    }
}

@Composable
fun SyncIcon(state: SyncState, modifier: Modifier = Modifier) {
    val (icon, tint) = when (state) {
        SyncState.OFFLINE -> Pair(R.drawable.cloud_off, Color.Gray)
        SyncState.UNSYNCED -> Pair(R.drawable.cloud_alert, MaterialTheme.colorScheme.error)
        SyncState.SYNCING -> Pair(R.drawable.cloud_sync, MaterialTheme.colorScheme.primary)
        SyncState.SYNCED -> Pair(R.drawable.cloud_check, MaterialTheme.colorScheme.primary)
    }

    Icon(
        painter = painterResource(icon),
        contentDescription = "Sync Status: ${state.name}",
        tint = tint,
        modifier = modifier
    )
}

fun formatRelativeDate(dataDate: String): String {
    if (dataDate.isBlank()) return ""

    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val dueDate = parser.parse(dataDate) ?: return dataDate

        // Get today's date in UTC
        val today = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val due = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            time = dueDate
        }

        // Calculate days between by dividing the millisecond difference
        val diffMillis = due.timeInMillis - today.timeInMillis
        val daysBetween = diffMillis / (1000 * 60 * 60 * 24)

        when {
            daysBetween < 0 -> "Overdue"
            daysBetween == 0L -> "Today"
            daysBetween == 1L -> "Tomorrow"

            else -> {
                val displayFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                displayFormatter.format(dueDate)
            }
        }
    } catch (e: Exception) {
        dataDate
    }
}