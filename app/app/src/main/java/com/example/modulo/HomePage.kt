package com.example.modulo

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.modulo.ui.theme.ModuloTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

        OutlinedTextField(
            state = rememberTextFieldState(),
            label = { Text("Title") }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TaskTypeDropDownMenu()

            Spacer(modifier = Modifier.width(12.dp))

            DeadlinePicker()
        }

        if (isDriveSyncEnabled) {
            Text(text = statusText)
        }

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskTypeDropDownMenu() {
    var isExpanded by remember { mutableStateOf(false) }
    val taskTypes = arrayOf("Assignment", "Tutorial", "Quiz", "Exam")
    var selectedText by remember { mutableStateOf(taskTypes[0]) }

    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = { isExpanded = !isExpanded }
    ) {
        OutlinedTextField(
            value = selectedText,
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
                        selectedText = item
                        isExpanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun DeadlinePicker(modifier: Modifier = Modifier) {
    var showCalendar by remember { mutableStateOf(false) }
    var selectedDateText by remember { mutableStateOf("") }

    val datePickerState = rememberDatePickerState()

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        if (isPressed) {
            showCalendar = true
        }
    }

    OutlinedTextField(
        value = selectedDateText,
        onValueChange = { }, // Read-only, so this stays completely empty
        label = { Text("Deadline") },
        placeholder = { Text("MM/DD/YYYY") },
        readOnly = true,
        interactionSource = interactionSource, // Attach the click listener here
        modifier = modifier.width(100.dp)
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
                            selectedDateText = formatter.format(Date(millis))
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


@Preview(
    showBackground = true,
    showSystemUi = true
)
@Composable
fun LayoutPreview() {
    ModuloTheme {
        HomePage(isDriveSyncEnabled = false)
    }
}