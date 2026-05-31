package com.example.modulo

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomePage(
    isDriveSyncEnabled: Boolean,
    viewModel: AppViewModel,
) {
    // Collect info from the model
    val appData by viewModel.appData.collectAsState()
    val syncState by viewModel.syncState.collectAsState()

    // States from the create-task fields
    val inputTitle = rememberTextFieldState()
    var selectedTaskType by remember { mutableStateOf("assignment") }
    var selectedDeadline by remember { mutableStateOf("") }

    val statusText = when (syncState) {
        SyncState.UNSYNCED -> "Unsynced changes..."
        SyncState.SYNCING -> "Syncing to Drive..."
        SyncState.SYNCED -> "All changes synced"
    }

    // Relays information to model is user turns on/off Drive Syncing
    LaunchedEffect(isDriveSyncEnabled) {
        viewModel.setDriveSync(isDriveSyncEnabled);
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

        OutlinedTextField(
            state = inputTitle,
            label = { Text("Title") }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TaskTypeDropDownMenu(
                selectedType = selectedTaskType,
                onTypeSelected = {selectedType -> selectedTaskType = selectedType}
            )

            Spacer(modifier = Modifier.width(12.dp))

            DeadlinePicker(
                selectedDate = selectedDeadline,
                onDateSelected = {selectedDate -> selectedDeadline = selectedDate}
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (inputTitle.text.isNotBlank() && selectedDeadline.isNotBlank()) {
                    viewModel.addTask(
                        inputTitle.text.toString(),
                        selectedTaskType,
                        selectedDeadline,
                        false
                    )

                    // Clear input fields
                    inputTitle.edit { replace(0, length, "") }
                    selectedDeadline = ""
                }
            }
        ) {
            Text("Add Task")
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

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskTypeDropDownMenu(
    selectedType: String,
    onTypeSelected: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val taskTypes = arrayOf("assignment", "tutorial", "quiz", "exam")

    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = { isExpanded = !isExpanded }
    ) {
        OutlinedTextField(
            value = selectedType,
            onValueChange = {},
            readOnly = true,
            label = { Text("Task Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .width(200.dp),
        )

        ExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false }
        ) {
            taskTypes.forEach { item ->
                DropdownMenuItem(
                    text = { Text(text = item) },
                    onClick = {
                        onTypeSelected(item)
                        isExpanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun DeadlinePicker(
    selectedDate: String,
    onDateSelected: (String) -> Unit,
) {
    var showCalendar by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        if (isPressed) {
            showCalendar = true
        }
    }

    OutlinedTextField(
        value = selectedDate,
        onValueChange = { },
        label = { Text("Deadline") },
        placeholder = { Text("MM/DD/YYYY") },
        readOnly = true,
        interactionSource = interactionSource,
        modifier = Modifier.width(100.dp)
    )

    if (showCalendar) {
        DatePickerDialog(
            onDismissRequest = { showCalendar = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCalendar = false
                        datePickerState.selectedDateMillis?.let { millis ->
                            val formatter = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
                            onDateSelected(formatter.format(Date(millis)))
                        }
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCalendar = false }
                ) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
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
                    Text(text = task.type,)
                    Text(text = "Due: ${task.due}",)
                }
            }
        }
    }
}