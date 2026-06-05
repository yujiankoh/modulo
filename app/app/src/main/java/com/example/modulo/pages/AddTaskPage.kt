package com.example.modulo.pages

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.modulo.AppData
import com.example.modulo.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun AddTaskPage(
    viewModel: AppViewModel,
    onAddTask: () -> Unit
) {
    // Collect info from the model
    val appData by viewModel.appData.collectAsState()

    // Collect the first module
    val initialModule = appData.timetable?.modules?.first()
    val initialModuleName = "${initialModule?.code}: ${initialModule?.name}"

    // States from the create-task fields
    val inputTitle = rememberTextFieldState()
    var selectedModule by remember { mutableStateOf(initialModuleName) }
    var selectedTaskType by remember { mutableStateOf("assignment") }
    var selectedDeadline by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            state = inputTitle,
            label = { Text("Title") }
        )

        Spacer(modifier = Modifier.height(8.dp))

        ModuleDropDownMenu(
            selectedModule = selectedModule,
            appData = appData,
            onModuleSelected = {selectedModule = it}
        )

        Spacer(modifier = Modifier.height(8.dp))

        TaskTypeDropDownMenu(
            selectedType = selectedTaskType,
            onTypeSelected = {selectedTaskType = it}
        )

        Spacer(modifier = Modifier.height(8.dp))

        DeadlinePicker(
            selectedDate = selectedDeadline,
            onDateSelected = {selectedDeadline = it}
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (inputTitle.text.isNotBlank() && selectedDeadline.isNotBlank()) {
                    viewModel.addTask(
                        selectedModule,
                        inputTitle.text.toString(),
                        selectedTaskType,
                        selectedDeadline,
                        false
                    )

                    // Clear input fields
                    inputTitle.edit { replace(0, length, "") }
                    selectedDeadline = ""
                }
                onAddTask()
            }
        ) {
            Text("Add Task")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleDropDownMenu(
    selectedModule: String,
    appData: AppData,
    onModuleSelected: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val modules = appData.timetable?.modules?.map { mod ->
        "${mod.code}: ${mod.name}"
    }

    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = { isExpanded = !isExpanded }
    ) {
        OutlinedTextField(
            value = selectedModule,
            onValueChange = {},
            readOnly = true,
            label = { Text("Module") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )

        ExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false }
        ) {
            modules?.forEach { item ->
                DropdownMenuItem(
                    text = { Text(text = item) },
                    onClick = {
                        onModuleSelected(item)
                        isExpanded = false
                    }
                )
            }
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
            value = selectedType.replaceFirstChar { it.uppercase() },
            onValueChange = {},
            readOnly = true,
            label = { Text("Task Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )

        ExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false }
        ) {
            taskTypes.forEach { item ->
                DropdownMenuItem(
                    text = { Text(text = item.replaceFirstChar { it.uppercase() }) },
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
        value = formatDate(selectedDate),
        onValueChange = { },
        label = { Text("Deadline") },
        placeholder = { Text("DD/MM/YYYY") },
        readOnly = true,
        interactionSource = interactionSource,
    )

    if (showCalendar) {
        DatePickerDialog(
            onDismissRequest = { showCalendar = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCalendar = false
                        datePickerState.selectedDateMillis?.let { millis ->
                            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                .apply {
                                    timeZone = TimeZone.getTimeZone("UTC")
                                }
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