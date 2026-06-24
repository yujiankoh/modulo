package com.example.modulo.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.modulo.AppViewModel
import com.example.modulo.EducationLevel
import com.example.modulo.Module
import com.example.modulo.R
import com.example.modulo.Slot
import com.example.modulo.Timetable
import kotlin.math.roundToInt

@Composable
fun TimetableManualPage(
    currEducationLevel: EducationLevel,
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    var educationLevel by remember { mutableStateOf(currEducationLevel) }
    val modules = remember { mutableStateListOf(FormModule()) }

    var showSaveWarning by remember { mutableStateOf(false) }
    val isHigherEd = educationLevel == EducationLevel.UNIVERSITY || educationLevel == EducationLevel.POLY

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row (
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(painter = painterResource(R.drawable.arrow_left), contentDescription = "Go Back")
                    }

                    Text(
                        text = "Manual Timetable Entry",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // The Save Button
                Button(
                    onClick = { showSaveWarning = true },
                    enabled = modules.all {
                        if (isHigherEd) {
                            it.code.isNotBlank() && it.name.isNotBlank()
                        } else {
                            it.name.isNotBlank()
                        }
                    }
                ) {
                    Text("Save")
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    EducationDropDownMenu(
                        selectedEdu = educationLevel,
                        onEduSelected = { educationLevel = it }
                    )
                }

                itemsIndexed(modules) { moduleIndex, module ->
                    ModuleEntryCard(
                        module = module,
                        educationLevel = educationLevel,
                        onModuleChange = { updatedModule ->
                            modules[moduleIndex] = updatedModule
                        },
                        onDeleteClick = {
                            modules.removeAt(moduleIndex)
                        }
                    )
                }

                item {
                    OutlinedButton(
                        onClick = { modules.add(FormModule()) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(painter = painterResource(R.drawable.plus), contentDescription = "Add Module")
                        Spacer(Modifier.width(8.dp))
                        Text("Add Another Module")
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    if (showSaveWarning) {
        WarningCard(
            title = "Save Timetable",
            text = "Are you sure you want to manually save this timetable? This will override your current timetable, if any.",
            confirmText = "Save",
            onConfirm = {
                val validFormModules = modules.filter { it.code.isNotBlank() || it.name.isNotBlank()}

                val finalModules = validFormModules.map { formMod ->
                    Module(
                        code = formMod.code,
                        name = formMod.name,
                        slots = formMod.slots.map { formSlot ->
                            Slot(
                                day = formSlot.day,
                                start = formSlot.start,
                                end = formSlot.end,
                                location = formSlot.location,
                                sessionType = formSlot.sessionType,
                                classNo = formSlot.classNo,
                                week = formSlot.week
                            )
                        }
                    )
                }

                val newTimetable = Timetable(
                    educationLevel = educationLevel.json,
                    modules = finalModules
                )

                viewModel.saveTimetable(newTimetable)
                onBack()
                onBack()
            },
            onDismiss = { showSaveWarning = false }
        )
    }
}

data class FormSlot(
    var day: String = "MON",
    var start: String = "12:00",
    var end: String = "14:00",
    var location: String = "",
    var sessionType: String = "lecture",
    var classNo: String = "",
    var week: String = "all"
)

data class FormModule(
    var code: String = "",
    var name: String = "",
    var slots: MutableList<FormSlot> = mutableListOf(FormSlot())
)

@Composable
fun ModuleEntryCard(
    module: FormModule,
    educationLevel: EducationLevel,
    onModuleChange: (FormModule) -> Unit,
    onDeleteClick: () -> Unit
) {
    // Formatting based on education level
    val isHigherEd = educationLevel == EducationLevel.UNIVERSITY || educationLevel == EducationLevel.POLY

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = if (isHigherEd) "Module Details" else "Subject Details", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onDeleteClick) {
                    Icon(painter = painterResource(R.drawable.trash_2), contentDescription = "Delete Module", tint = MaterialTheme.colorScheme.error)
                }
            }

            if (isHigherEd) {
                // Module Code
                OutlinedTextField(
                    value = module.code,
                    onValueChange = { onModuleChange(module.copy(code = it.uppercase())) },
                    label = { Text("Module Code") },
                    placeholder = { Text("MA100...") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(8.dp))

            // Module Name
            OutlinedTextField(
                value = module.name,
                onValueChange = { onModuleChange(module.copy(name = it)) },
                label = { Text(if (isHigherEd) "Module Name" else "Subject") },
                placeholder = { Text(if (isHigherEd) "Introduction to..." else "Math...") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            Text("Time Slots", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            // Slots List
            module.slots.forEachIndexed { slotIndex, slot ->
                SlotEntryRow(
                    educationLevel = educationLevel,
                    slot = slot,
                    onSlotChange = { updatedSlot ->
                        val newSlots = module.slots.toMutableList()
                        newSlots[slotIndex] = updatedSlot
                        onModuleChange(module.copy(slots = newSlots))
                    },
                    onDeleteSlot = {
                        val newSlots = module.slots.toMutableList()
                        newSlots.removeAt(slotIndex)
                        onModuleChange(module.copy(slots = newSlots))
                    }
                )
                Spacer(Modifier.height(8.dp))
            }

            TextButton(
                onClick = {
                    val newSlots = module.slots.toMutableList()
                    newSlots.add(FormSlot())
                    onModuleChange(module.copy(slots = newSlots))
                }
            ) {
                Icon(painter = painterResource(R.drawable.plus), contentDescription = "Add Slot")
                Spacer(Modifier.width(4.dp))
                Text("Add Slot")
            }
        }
    }
}

@Composable
fun SlotEntryRow(
    educationLevel: EducationLevel,
    slot: FormSlot,
    onSlotChange: (FormSlot) -> Unit,
    onDeleteSlot: () -> Unit
) {
    val days = arrayOf("MON", "TUE", "WED", "THU", "FRI")
    val weeks = arrayOf("all", "even", "odd")

    val sessions = when (educationLevel) {
        EducationLevel.PRIMARY -> arrayOf("lesson")
        EducationLevel.SECONDARY -> arrayOf("lesson")
        EducationLevel.JC -> arrayOf("lesson", "tutorial")
        EducationLevel.POLY -> arrayOf("lecture", "tutorial", "lab", "practical")
        EducationLevel.UNIVERSITY -> arrayOf("lecture", "tutorial", "lab", "seminar")
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FormDropDownMenu(
                    itemOptions = days,
                    label = "Day",
                    selectedItem = slot.day,
                    onItemSelected = { onSlotChange(slot.copy(day = it)) },
                    modifier = Modifier.weight(1f) // Works perfectly here!
                )
                Spacer(Modifier.width(8.dp))
                FormDropDownMenu(
                    itemOptions = weeks,
                    label = "Week",
                    selectedItem = slot.week,
                    onItemSelected = { onSlotChange(slot.copy(week = it)) },
                    modifier = Modifier.weight(1f)
                )

                IconButton(onClick = onDeleteSlot) {
                    Icon(painter = painterResource(R.drawable.trash_2), contentDescription = "Delete Slot", tint = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                TimePickerMenu(
                    label = "Start",
                    selectedTime = slot.start,
                    onTimeSelected = { onSlotChange(slot.copy(start = it)) },
                    modifier = Modifier.weight(2f)
                )
                Spacer(Modifier.width(8.dp))
                TimePickerMenu(
                    label = "End",
                    selectedTime = slot.end,
                    onTimeSelected = { onSlotChange(slot.copy(end = it)) },
                    modifier = Modifier.weight(2f)
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = slot.classNo,
                    onValueChange = { onSlotChange(slot.copy(classNo = it.uppercase())) },
                    label = { Text("Class Code (Optional)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.weight(3f)
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = slot.location,
                    onValueChange = { onSlotChange(slot.copy(location = it)) },
                    label = { Text("Location (Optional)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                FormDropDownMenu(
                    itemOptions = sessions,
                    label = "Type",
                    selectedItem = slot.sessionType,
                    onItemSelected = { onSlotChange(slot.copy(sessionType = it)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormDropDownMenu(
    itemOptions: Array<String>,
    label: String,
    selectedItem: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = { isExpanded = !isExpanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedItem.replaceFirstChar { it.uppercase() },
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )

        ExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false }
        ) {
            itemOptions.forEach { item ->
                DropdownMenuItem(
                    text = { Text(text = item.replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        onItemSelected(item)
                        isExpanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerMenu(
    label: String,
    selectedTime: String,
    onTimeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    // Parse the existing time string
    val parts = selectedTime.split(":")
    val initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 12
    val initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0

    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )

    Box(modifier = modifier.clickable { showDialog = true }) {
        OutlinedTextField(
            value = selectedTime,
            onValueChange = {},
            readOnly = true,
            enabled = false, // Prevents keyboard from appearing
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            // Override disabled colors
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    val rawHour = timePickerState.hour
                    val rawMinute = timePickerState.minute

                    // Round to the nearest 15 minutes
                    var roundedMinute = ((rawMinute / 15.0).roundToInt() * 15)
                    var finalHour = rawHour

                    if (roundedMinute == 60) {
                        roundedMinute = 0
                        finalHour = (finalHour + 1) % 24 // Keeps it within 0-23
                    }

                    val h = finalHour.toString().padStart(2, '0')
                    val m = roundedMinute.toString().padStart(2, '0')

                    onTimeSelected("$h:$m")
                    showDialog = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            },
            text = {
                // This renders the visual clock dial!
                TimePicker(state = timePickerState)
            }
        )
    }
}