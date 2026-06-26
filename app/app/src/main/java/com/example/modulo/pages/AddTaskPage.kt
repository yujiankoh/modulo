package com.example.modulo.pages

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.modulo.AppData
import com.example.modulo.AppViewModel
import com.example.modulo.Module
import com.example.modulo.R
import com.example.modulo.TimetableState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun AddTaskPage(
    viewModel: AppViewModel,
    onUploadTimetable: () -> Unit,
    onAddTask: () -> Unit
) {
    // Collect info from the model
    val appData by viewModel.appData.collectAsState()
    val timetableState by viewModel.timetableState.collectAsState()

    // Collect the first module
    val initialModule = appData.timetable?.modules?.first()

    // States from the create-task fields
    val inputTitle = rememberTextFieldState()
    var selectedModule by remember { mutableStateOf(initialModule) }
    var selectedTaskType by remember { mutableStateOf("assignment") }
    var selectedDeadline by remember { mutableStateOf<LocalDate?>(null) }

    if (appData.timetable == null && timetableState is TimetableState.Idle) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MissingTimetableBanner(onUploadClicked = onUploadTimetable)
        }

    } else {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                state = inputTitle,
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(0.7f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            ModuleDropDownMenu(
                selectedModule = selectedModule,
                appData = appData,
                onModuleSelected = { selectedModule = it },
                modifier = Modifier.fillMaxWidth(0.7f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            TaskTypeDropDownMenu(
                selectedType = selectedTaskType,
                onTypeSelected = { selectedTaskType = it },
                modifier = Modifier.fillMaxWidth(0.7f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            DatePickerMenu(
                label = "Deadline",
                selectedDate = selectedDeadline,
                onDateSelected = { selectedDeadline = it },
                modifier = Modifier.fillMaxWidth(0.7f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    viewModel.addTask(
                        module =  selectedModule,
                        title = inputTitle.text.toString(),
                        type = selectedTaskType,
                        deadline = selectedDeadline,
                        isCompleted =  false
                    )

                    // Clear input fields
                    inputTitle.edit { replace(0, length, "") }
                    selectedDeadline = null

                    onAddTask()
                },
                enabled = inputTitle.text.isNotBlank() && selectedDeadline != null && selectedModule != null,
                contentPadding = PaddingValues(
                    start = 8.dp,
                    top = ButtonDefaults.ContentPadding.calculateTopPadding(),
                    end = ButtonDefaults.ContentPadding.calculateEndPadding(layoutDirection = androidx.compose.ui.unit.LayoutDirection.Ltr),
                    bottom = ButtonDefaults.ContentPadding.calculateBottomPadding()
                )
            ) {
                Icon(painter = painterResource(R.drawable.plus), contentDescription = "Add")
                Spacer(modifier = Modifier.padding(6.dp))
                Text("Add Task")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuleDropDownMenu(
    selectedModule: Module?,
    appData: AppData,
    onModuleSelected: (Module) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val modules = appData.timetable?.modules

    val moduleName = toDisplay(selectedModule)

    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = { isExpanded = !isExpanded },
    ) {
        OutlinedTextField(
            value = moduleName,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Module") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
            modifier = modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )

        ExposedDropdownMenu(
            expanded = isExpanded,
            containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = { isExpanded = false }
        ) {
            modules?.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(text = toDisplay(item), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    onClick = {
                        onModuleSelected(item)
                        isExpanded = false
                    }
                )
            }
        }
    }
}

fun toDisplay(module: Module?): String {
    if (module == null) {
        return "No modules detected"
    }
    return if (module.code == "") module.name else "${module.code}: ${module.name}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskTypeDropDownMenu(
    selectedType: String,
    onTypeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
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
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
            modifier = modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )

        ExposedDropdownMenu(
            expanded = isExpanded,
            containerColor = MaterialTheme.colorScheme.surface,
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
fun DatePickerMenu(
    label: String,
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCalendar by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    )

    val displayFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        if (isPressed) {
            showCalendar = true
        }
    }

    OutlinedTextField(
        value = selectedDate?.format(displayFormatter) ?: "",
        onValueChange = { },
        label = { Text(label) },
        placeholder = { Text("DD/MM/YYYY") },
        readOnly = true,
        interactionSource = interactionSource,
        modifier = modifier
    )

    if (showCalendar) {
        DatePickerDialog(
            onDismissRequest = { showCalendar = false },
            colors = DatePickerDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            confirmButton = {
                TextButton(
                    onClick = {
                        showCalendar = false
                        datePickerState.selectedDateMillis?.let { millis ->
                            // Convert milliseconds to LocalDate
                            val localDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            onDateSelected(localDate)
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
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    }
}