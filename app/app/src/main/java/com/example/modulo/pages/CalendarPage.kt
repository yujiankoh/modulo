package com.example.modulo.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.modulo.AppViewModel
import com.example.modulo.R
import com.example.modulo.Task
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarPage(
    viewModel: AppViewModel
) {
    // Collect info from the model
    val appData by viewModel.appData.collectAsState()

    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        // Calendar header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                Icon(painter = painterResource(R.drawable.chevron_left), contentDescription = "Previous Month")
            }

            Text(
                text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                Icon(painter = painterResource(R.drawable.chevron_right), contentDescription = "Next Month")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val daysOfWeek = listOf("M", "T", "W", "T", "F", "S", "S")

        Row(modifier = Modifier.fillMaxWidth()) {
            daysOfWeek.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Calendar grid
        val daysInMonth = currentMonth.lengthOfMonth()
        val firstDayOfMonth = currentMonth.atDay(1)

        // Adjust depending on whether Sunday or Monday as the first day.
        // java.time defaults to Monday = 1, Sunday = 7.
        val offset = firstDayOfMonth.dayOfWeek.value - 1

        selectedDate?.let { date ->
            val selectedTasks = appData.tasks.filter { task ->
                try {
                    task.due.isNotEmpty() && LocalDate.parse(task.due) == date
                } catch (e: Exception) {
                    false
                }
            }

            ViewCalendarCell(
                date = date,
                tasks = selectedTasks,
                viewModel = viewModel,
                onDismiss = {selectedDate = null}
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            for (rowIndex in 0 until 6) {
                val firstDayOfRow = rowIndex * 7 - offset

                if (firstDayOfRow >= daysInMonth) {
                    Spacer(modifier = Modifier.weight(1f))
                } else {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {

                        for (colIndex in 0 until 7) {
                            val dayIndex = rowIndex * 7 + colIndex - offset

                            if (dayIndex in 0 until daysInMonth) {
                                val date = currentMonth.atDay(dayIndex + 1)

                                val dayTasks = appData.tasks.filter { task ->
                                    try {
                                        task.due.isNotEmpty() && LocalDate.parse(task.due) == date
                                    } catch (e: Exception) {
                                        false
                                    }
                                }

                                DayCell(
                                    date = date,
                                    tasks = dayTasks,
                                    modifier = Modifier.weight(1f).clickable { selectedDate = date }
                                )
                            } else {
                                // Empty invisible box for days before the 1st or after the end of month
                                Spacer(modifier = Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DayCell(
    date: LocalDate,
    tasks: List<Task>,
    modifier: Modifier = Modifier,
    emoji: String = "",
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(2.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            if (emoji.isNotEmpty()) {
                Text(text = emoji, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        tasks.take(3).forEach { task ->
            Text(
                text = task.title,
                fontSize = 8.sp,
                maxLines = 1,
                lineHeight = 12.sp,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 2.dp)
            )
        }

        // If there are too many tasks, show a "+X more" indicator
        if (tasks.size > 3) {
            Text(
                text = "+${tasks.size - 3} more",
                fontSize = 8.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun ViewCalendarCell(
    date: LocalDate,
    tasks: List<Task>,
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    var deletedTask by remember { mutableStateOf<Task?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Tasks for ${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (tasks.isEmpty()) {
                    Text("No tasks scheduled for this day.", color = Color.Gray)
                } else {
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(tasks) { task ->
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
                                dueText = ""
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}