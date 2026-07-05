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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.modulo.AppViewModel
import com.example.modulo.Break
import com.example.modulo.EducationLevel
import com.example.modulo.Handbook
import com.example.modulo.R
import com.example.modulo.components.DatePickerMenu
import com.example.modulo.components.DropDownMenu
import java.time.LocalDate

@Composable
fun HandbookCreatePage(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onSave: () -> Unit
) {
    val appData by viewModel.appData.collectAsState()
    val isFirstTime = appData.educationLevel == null

    var educationLevel by remember { mutableStateOf(EducationLevel.UNIVERSITY) }
    var termStartDate by remember { mutableStateOf<LocalDate?>(null) }
    var termEndDate by remember { mutableStateOf<LocalDate?>(null) }
    val breaks = remember { mutableStateListOf(Break("", ""))}

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
                            id = "test",
                            educationLevel = educationLevel.json,
                            termStart = termStartDate?.toString(),
                            termEnd = termEndDate?.toString(),
                            breaks = breaks.toList(),
                            tasks = emptyList(),
                            timetable = null,
                        )

                        viewModel.saveHandbook(handbook)

                        if (!isFirstTime) {
                            onSave()
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

            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp)
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
                    DatePickerMenu(
                        selectedDate = termStartDate,
                        label = "Term Start",
                        onDateSelected = { newDate ->
                            termStartDate = newDate
                            viewModel.saveTermStart(newDate)
                        },
                        modifier = Modifier.weight(1f)
                    )

                    DatePickerMenu(
                        selectedDate = termEndDate,
                        label = "Term End",
                        onDateSelected = { newDate ->
                            termEndDate = newDate
                            viewModel.saveTermEnd(newDate)
                        },
                        modifier = Modifier.weight(1f)
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
                            Text("Add Another Break")
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
    ElevatedCard(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Break")
                IconButton(onClick = onDeleteClick) {
                    Icon(painter = painterResource(R.drawable.trash_2), contentDescription = "Delete Break", tint = MaterialTheme.colorScheme.error)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DatePickerMenu(
                    selectedDate = if (currBreak.start.isEmpty()) null else LocalDate.parse(currBreak.start),
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
        }

    }
}