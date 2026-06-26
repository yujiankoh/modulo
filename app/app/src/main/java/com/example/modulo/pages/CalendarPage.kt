package com.example.modulo.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.modulo.AppViewModel
import com.example.modulo.R
import com.example.modulo.StudySession
import com.example.modulo.Task
import com.example.modulo.emojis
import com.example.modulo.getModuleColor
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun CalendarPage(
    viewModel: AppViewModel
) {
    // Collect info from the model
    val appData by viewModel.appData.collectAsState()

    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    val today = remember { LocalDate.now() }
    val tasks = appData.tasks.sortedBy { task -> task.done }

    val daysOfWeek = listOf("M", "T", "W", "T", "F", "S", "S")
    val daysInMonth = currentMonth.lengthOfMonth()
    val firstDayOfMonth = currentMonth.atDay(1)
    val offset = firstDayOfMonth.dayOfWeek.value - 1

    val ratings: Map<LocalDate, String> = remember(appData.studySessions) {
        val sessions = appData.studySessions

        val validDates = sessions.mapNotNull { session ->
            try {
                val localDate = Instant.parse(session.start)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                localDate to session
            } catch (e: Exception) {
                null
            }
        }

        val sessionsByDate: Map<LocalDate, List<StudySession>> = validDates
            .groupBy(
                keySelector = { it.first },
                valueTransform = { it.second }
            )

        // Average
        sessionsByDate.mapValues { (_, dateSessions) ->
            var totalPoints = 0L
            var totalDurationMins = 0L

            dateSessions.forEach { session ->
                // score = rating * duration minutes
                if (session.rating != null) {
                    totalPoints += session.rating * session.durationMins
                    totalDurationMins += session.durationMins
                }
            }

            if (totalDurationMins > 0) {
                val weightedAvg = (totalPoints.toDouble() / totalDurationMins).roundToInt()
                emojis[weightedAvg - 1]
            } else {
                ""
            }
        }
    }

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

        Row(modifier = Modifier.fillMaxWidth()) {
            daysOfWeek.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Grid
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            for (rowIndex in 0 until 6) {
                val firstDayOfRow = rowIndex * 7 - offset

                if (firstDayOfRow >= daysInMonth) {
                    Spacer(modifier = Modifier.weight(1f))
                } else {
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        for (colIndex in 0 until 7) {
                            val dayIndex = rowIndex * 7 + colIndex - offset

                            if (dayIndex in 0 until daysInMonth) {
                                val date = currentMonth.atDay(dayIndex + 1)

                                val dayTasks = tasks.filter { task ->
                                    try {
                                        task.due.isNotEmpty() && LocalDate.parse(task.due) == date
                                    } catch (e: Exception) {
                                        false
                                    }
                                }

                                DayCell(
                                    date = date,
                                    tasks = dayTasks,
                                    isToday = (date == today),
                                    modifier = Modifier.weight(1f).clickable { selectedDate = date },
                                    emoji = ratings[date] ?: ""
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

        selectedDate?.let { date ->
            val selectedTasks = tasks.filter { task ->
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
    }
}

@Composable
fun DayCell(
    date: LocalDate,
    tasks: List<Task>,
    isToday: Boolean,
    modifier: Modifier = Modifier,
    emoji: String = "",
) {
    val cellModifier = if (isToday) {
        modifier
            .fillMaxSize()
            .background(color = MaterialTheme.colorScheme.surface)
            .border(width = 2.dp, color = MaterialTheme.colorScheme.primary)
            .padding(4.dp)
    } else {
        modifier
            .fillMaxSize()
            .background(color = MaterialTheme.colorScheme.surface)
            .padding(4.dp)
    }

    Column(modifier = cellModifier) {
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
            val theme = getModuleColor(task.module.ifBlank { task.title })

            Text(
                text = task.title,
                fontSize = 10.sp,
                maxLines = 1,
                lineHeight = 15.sp,
                overflow = TextOverflow.Ellipsis,
                color = theme.onContainer,
                textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp)
                    .background(
                        theme.container,
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 2.dp)
            )
        }

        // If there are too many tasks, show a "+X more" indicator
        if (tasks.size > 3) {
            Text(
                text = "+${tasks.size - 3} more",
                fontSize = 10.sp,
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

    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current

    val screenHeight = with(density) { windowInfo.containerSize.height.toDp() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp)
                .heightIn(max = screenHeight * 0.6f),
            shape = RoundedCornerShape(36.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
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
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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