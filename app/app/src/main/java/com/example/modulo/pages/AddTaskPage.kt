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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.modulo.Task
import com.example.modulo.TimetableState
import com.example.modulo.components.DatePickerMenu
import com.example.modulo.components.DropDownMenu
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun AddTaskPage(
    viewModel: AppViewModel,
    onUploadTimetable: () -> Unit,
    onAddTask: () -> Unit,
    initialModuleCode: String? = null
) {
    // Collect info from the model
    val appData by viewModel.appData.collectAsState()
    val timetableState by viewModel.timetableState.collectAsState()

    // Pre-select the module the user opened this from (its dashboard card), else the first one.
    val modules = appData.timetable?.modules
    val initialModule = remember(modules, initialModuleCode) {
        val fromCard = initialModuleCode?.let { code ->
            modules?.firstOrNull { it.code.ifBlank { it.name } == code }
        }
        fromCard ?: modules?.firstOrNull()
    }

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
                modifier = Modifier.fillMaxWidth(0.7f),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            DropDownMenu(
                label = if (viewModel.isHigherEducation()) "Modules" else "Subjects",
                selectedItem = selectedModule,
                items = appData.timetable?.modules,
                itemToText = {toDisplay(it)},
                onItemSelected = { selectedModule = it },
                modifier = Modifier.fillMaxWidth(0.7f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            DropDownMenu(
                label = "Task Type",
                selectedItem = selectedTaskType,
                items = listOf("assignment", "tutorial", "quiz", "exam"),
                itemToText = { type -> type?.replaceFirstChar { it.uppercase() } ?: "" },
                onItemSelected = { selectedTaskType = it },
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

                    val newTask = Task(
                        id = System.currentTimeMillis(),
                        module = selectedModule?.code?.ifBlank { selectedModule!!.name } ?: "",
                        title = inputTitle.text.toString(),
                        due = selectedDeadline?.toString() ?: "",
                        type = selectedTaskType,
                        done = false
                    )

                    viewModel.addTask(newTask)

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
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(painter = painterResource(R.drawable.plus), contentDescription = "Add")
                Spacer(modifier = Modifier.padding(6.dp))
                Text("Add Task")
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

