package com.example.modulo.pages

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.modulo.AppViewModel
import com.example.modulo.Break
import com.example.modulo.EducationLevel
import com.example.modulo.Handbook
import com.example.modulo.R
import com.example.modulo.components.DatePickerMenu
import com.example.modulo.components.DropDownMenu
import com.example.modulo.components.WarningCard
import com.example.modulo.components.YearInputField
import com.example.modulo.ui.theme.ModuloTheme
import java.time.LocalDate
import java.util.UUID

@Composable
fun HandbookCreatePage(
    viewModel: AppViewModel,
    isEditMode: Boolean = false,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    val appData by viewModel.appData.collectAsState()
    val isFirstTime = appData.educationLevel == null

    var educationLevel by remember {
        mutableStateOf(
            if (isEditMode) {
                EducationLevel.fromJson(appData.educationLevel) ?: EducationLevel.UNIVERSITY
            } else EducationLevel.UNIVERSITY
        )
    }

    var academicYear by remember {
        mutableStateOf(
            if (isEditMode) {
                unparseYear(educationLevel, appData.academicYear ?: "")
            } else ""
        )
    }

    var semester by remember { mutableIntStateOf(if (isEditMode) appData.semester ?: 1 else 1) }

    var termStartDate by remember { mutableStateOf(if (isEditMode && !appData.termStart.isNullOrBlank()) LocalDate.parse(appData.termStart) else null) }

    var termEndDate by remember { mutableStateOf(if (isEditMode && !appData.termEnd.isNullOrBlank()) LocalDate.parse(appData.termEnd) else null) }

    val breaks = remember {
        mutableStateListOf<Break>().apply {
            if (isEditMode) addAll(appData.breaks)
        }
    }

    val isTermValid = termStartDate == null || termEndDate == null ||  termEndDate!!.isAfter(termStartDate)
    val areBreaksValid = breaks.all { currBreak ->
        if (currBreak.start.isEmpty() || currBreak.end.isEmpty()) {
            false
        } else {
            val start = LocalDate.parse(currBreak.start)
            val end = LocalDate.parse(currBreak.end)
            !start.isAfter(end)
        }
    }
    val isYearFilled = academicYear.isNotEmpty()

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
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
                    if (!isFirstTime) {
                        IconButton(onClick = onBack) {
                            Icon(painter = painterResource(R.drawable.arrow_left), contentDescription = "Go Back")
                        }
                    }

                    Text(
                        text = "Create Handbook",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // The Save Button
                Button(
                    onClick = {
                        val handbook = Handbook(
                            id = if (isEditMode) appData.handbookId else UUID.randomUUID().toString(),
                            educationLevel = educationLevel.json,
                            academicYear = yearFormat(educationLevel, academicYear),
                            semester = semester,
                            termStart = termStartDate?.toString(),
                            termEnd = termEndDate?.toString(),
                            breaks = breaks.toList(),
                            tasks = emptyList(),
                            timetable = null,
                        )

                        if (isEditMode) {
                            viewModel.updateHandbook(handbook)
                        } else {
                            viewModel.saveHandbook(handbook)
                        }

                        if (!isFirstTime || isEditMode) {
                            onSave()
                        }
                    },
                    enabled = isTermValid && areBreaksValid && isYearFilled,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(painter = painterResource(R.drawable.save), contentDescription = "Save")
                    Spacer(modifier = Modifier.padding(6.dp))
                    Text("Save")
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DropDownMenu(
                    label = "Education Level",
                    selectedItem = educationLevel,
                    items = EducationLevel.entries,
                    itemToText = { it?.displayName ?: "" },
                    onItemSelected = { educationLevel = it },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    YearInputField(
                        label = if (educationLevel == EducationLevel.UNIVERSITY || educationLevel == EducationLevel.POLY) "Academic Year (Starting Year)" else "Year",
                        selectedYear = academicYear,
                        onYearSelected = { academicYear = it},
                        modifier = Modifier.weight(1f)
                    )

                    DropDownMenu(
                        label = "Semester",
                        selectedItem = semester,
                        items = listOf(1, 2),
                        itemToText = { "Semester $it" },
                        onItemSelected = { semester = it },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (academicYear.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Will be shown as ${titleDisplay(educationLevel, academicYear, semester)}",
                        fontSize = 12.sp,
                        color = ModuloTheme.colors.subText
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DatePickerMenu(
                        selectedDate = termStartDate,
                        label = "Term Start",
                        onDateSelected = { termStartDate = it },
                        modifier = Modifier.weight(1f)
                    )

                    DatePickerMenu(
                        selectedDate = termEndDate,
                        label = "Term End",
                        onDateSelected = { termEndDate = it },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (termStartDate != null && termEndDate?.isBefore(termStartDate) ?: false) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Term End must be after Term Start",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    itemsIndexed(breaks) { breakIndex, currBreak ->
                        HandbookBreak(
                            currBreak = currBreak,
                            onBreakChange = { newBreak ->
                                breaks[breakIndex] = newBreak
                            },
                            onDeleteClick = {
                                breaks.removeAt(breakIndex)
                            }
                        )
                    }

                    item {
                        OutlinedButton(
                            onClick = {
                                breaks.add(Break("", ""))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(painter = painterResource(R.drawable.plus), contentDescription = "Add Break")
                            Spacer(Modifier.width(8.dp))
                            Text(if (breaks.isEmpty()) "Add a Break" else "Add another Break")
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun HandbookBreak(
    currBreak: Break,
    onBreakChange: (Break) -> Unit,
    onDeleteClick: () -> Unit
) {
    val startFilled = currBreak.start.isNotEmpty()
    val endFilled = currBreak.end.isNotEmpty()

    val isMissingOneDate = !endFilled || !startFilled
    val isStartAfterEnd = if (startFilled && endFilled) {
        LocalDate.parse(currBreak.start).isAfter(LocalDate.parse(currBreak.end))
    } else false

    ElevatedCard(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Break")
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        painter = painterResource(R.drawable.trash_2),
                        contentDescription = "Delete Break",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DatePickerMenu(
                    selectedDate = if (currBreak.start.isEmpty()) null else LocalDate.parse(
                        currBreak.start
                    ),
                    label = "Break Start",
                    onDateSelected = { newDate ->
                        onBreakChange(currBreak.copy(start = newDate.toString()))
                    },
                    modifier = Modifier.weight(1f)
                )

                DatePickerMenu(
                    selectedDate = if (currBreak.end.isEmpty()) null else LocalDate.parse(currBreak.end),
                    label = "Break End",
                    onDateSelected = { newDate ->
                        onBreakChange(currBreak.copy(end = newDate.toString()))
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            if (isMissingOneDate) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Both Start and End dates must be filled.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (isStartAfterEnd) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Break End must be after Break Start.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

fun titleDisplay(educationLevel: EducationLevel, academicYear: String, semester: Int): String {
    val year = academicYear.toIntOrNull() ?: return "";

    if (educationLevel == EducationLevel.UNIVERSITY || educationLevel == EducationLevel.POLY) {
        val startStr = academicYear.takeLast(2)
        val nextStr = ((year + 1) % 100).toString().padStart(2, '0')
        return "AY$startStr/$nextStr • S$semester"
    } else {
        return "$academicYear • Sem $semester"
    }
}

fun yearFormat(educationLevel: EducationLevel, academicYear: String): String {
    val year = academicYear.toIntOrNull() ?: return "";

    if (educationLevel == EducationLevel.UNIVERSITY || educationLevel == EducationLevel.POLY) {
        val startStr = academicYear.takeLast(2)
        val nextStr = ((year + 1) % 100).toString().padStart(2, '0')
        return "$startStr/$nextStr"
    } else {
        return academicYear
    }
}

fun unparseYear(educationLevel: EducationLevel, year: String): String {
    if (educationLevel == EducationLevel.UNIVERSITY || educationLevel == EducationLevel.POLY) {
        val prefix = year.substringBefore('/')
        return if (prefix.length == 2) "20$prefix" else year
    } else {
        return year
    }
}