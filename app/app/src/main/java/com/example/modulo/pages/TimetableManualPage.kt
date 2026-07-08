package com.example.modulo.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDefaults
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
import androidx.compose.material3.TimePickerDefaults
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
import com.example.modulo.components.DropDownMenu
import com.example.modulo.components.TimePickerMenu
import com.example.modulo.components.WarningCard
import com.example.modulo.ui.theme.ModuloTheme
import kotlin.math.roundToInt

@Composable
fun TimetableManualPage(
    currEducationLevel: EducationLevel,
    viewModel: AppViewModel,
    isEditMode: Boolean = false,
    onBack: () -> Unit
) {
    val existingTimetable = remember { if (isEditMode) viewModel.appData.value.timetable else null }

    var educationLevel by remember { mutableStateOf(currEducationLevel) }
    val modules = remember {
        val existingModules = existingTimetable?.modules?.map { module ->
            FormModule(
                code = module.code,
                name = module.name,
                slots = module.slots.map { slot ->
                    FormSlot(
                        day = slot.day,
                        start = slot.start,
                        end = slot.end,
                        location = slot.location,
                        sessionType = slot.sessionType,
                        classNo = slot.classNo,
                        week = slot.week
                    )
                }.toMutableList()
            )
        }

        if (!existingModules.isNullOrEmpty()) {
            mutableStateListOf(*existingModules.toTypedArray())
        } else {
            mutableStateListOf(
                FormModule(
                    slots = mutableListOf(FormSlot(sessionType = getDefaultSessionType(currEducationLevel)))
                )
            )
        }
    }

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
                        text = if (isEditMode) "Edit Timetable" else "Enter Timetable",
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
                    },
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(painter = painterResource(R.drawable.save), contentDescription = "Save")
                    Spacer(modifier = Modifier.padding(6.dp))
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
                    DropDownMenu(
                        label = "Education Level",
                        selectedItem = educationLevel,
                        items = EducationLevel.entries,
                        itemToText = { it?.displayName ?: "" },
                        onItemSelected = { educationLevel = it }
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
                        onClick = {
                            modules.add(
                                FormModule(
                                    slots = mutableListOf(FormSlot(sessionType = getDefaultSessionType(educationLevel)))
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
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
            title = "Override Timetable",
            text = "Are you sure you want to manually save this timetable? This will override your current timetable, if any.",
            confirmText = "Override",
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
                if (!isEditMode) onBack()
            },
            onDismiss = { showSaveWarning = false }
        )
    }
}

fun getDefaultSessionType(level: EducationLevel): String {
    return when (level) {
        EducationLevel.PRIMARY, EducationLevel.SECONDARY, EducationLevel.JC -> "lesson"
        EducationLevel.POLY, EducationLevel.UNIVERSITY -> "lecture"
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
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
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
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
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
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
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
                    newSlots.add(FormSlot(sessionType = getDefaultSessionType(educationLevel)))
                    onModuleChange(module.copy(slots = newSlots))
                },
                shape = RoundedCornerShape(12.dp)
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
        EducationLevel.PRIMARY, EducationLevel.SECONDARY -> arrayOf("lesson")
        EducationLevel.JC -> arrayOf("lesson", "tutorial")
        EducationLevel.POLY -> arrayOf("lecture", "tutorial", "lab", "practical")
        EducationLevel.UNIVERSITY -> arrayOf("lecture", "tutorial", "lab", "seminar")
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
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
                    modifier = Modifier.weight(3f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = slot.location,
                    onValueChange = { onSlotChange(slot.copy(location = it)) },
                    label = { Text("Location (Optional)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
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
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            shape = RoundedCornerShape(12.dp)
        )

        ExposedDropdownMenu(
            expanded = isExpanded,
            containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = { isExpanded = false },
            shape = RoundedCornerShape(12.dp)
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