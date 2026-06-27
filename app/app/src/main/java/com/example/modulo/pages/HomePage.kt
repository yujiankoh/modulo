package com.example.modulo.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.modulo.AppViewModel
import com.example.modulo.R
import com.example.modulo.SyncState
import com.example.modulo.Task
import com.example.modulo.TimetableState
import com.example.modulo.ui.theme.ModuloTheme
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@Composable
fun HomePage(
    viewModel: AppViewModel,
    onUploadTimetable: () -> Unit,
    onSettingsClick: () -> Unit,
    onTimetableClick: () -> Unit,
) {
    // Collect info from the model
    val appData by viewModel.appData.collectAsState()
    val timetableState by viewModel.timetableState.collectAsState()

    var deletedTask by remember { mutableStateOf<Task?>(null) }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        deletedTask = null
                    })
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Missing timetable banner
            if (appData.timetable == null && timetableState is TimetableState.Idle) {
                MissingTimetableBanner (onUploadClicked = onUploadTimetable)
            }

            ProfileBar(viewModel = viewModel, onSettingsClick = onSettingsClick)

            TodaySchedule(viewModel = viewModel, onTimetableClick = onTimetableClick)

            Deadlines(viewModel = viewModel, deletedTask = deletedTask, onSelectDeletedTask = {deletedTask = it})
        }
    }
}

fun getGreeting(): String {
    val currentHour = LocalTime.now().hour
    return when (currentHour) {
        in 0..11 -> "Good Morning,"
        in 12..17 -> "Good Afternoon,"
        else -> "Good Evening,"
    }
}

@Composable
fun SyncIcon(state: SyncState, modifier: Modifier = Modifier) {
    val (icon, tint) = when (state) {
        SyncState.OFFLINE -> Pair(R.drawable.cloud_off, Color.Gray)
        SyncState.UNSYNCED -> Pair(R.drawable.cloud_alert, MaterialTheme.colorScheme.error)
        SyncState.SYNCING -> Pair(R.drawable.cloud_sync, MaterialTheme.colorScheme.primary)
        SyncState.SYNCED -> Pair(R.drawable.cloud_check, MaterialTheme.colorScheme.primary)
    }

    Icon(
        painter = painterResource(icon),
        contentDescription = "Sync Status: ${state.name}",
        tint = tint,
        modifier = modifier
    )
}

@Composable
fun SectionTitle(
    text: String,
    subtext: String = "",
    onSubtext: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp, top = 16.dp)
            .defaultMinSize(minHeight = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
        )

        if (subtext.isNotBlank()) {
            TextButton(
                onClick = onSubtext,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(subtext, color = ModuloTheme.colors.subText)
                    Spacer(modifier = Modifier.padding(4.dp))
                    Icon(
                        painter = painterResource(R.drawable.arrow_right),
                        contentDescription = "View Timetable",
                        tint = ModuloTheme.colors.subText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileBar(
    viewModel: AppViewModel,
    onSettingsClick: () -> Unit
) {
    val syncState by viewModel.syncState.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Greeting
        Column {
            Text(
                text = getGreeting(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "User", // You can replace this with viewModel.userName if you save it!
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Sync Icon + Settings / Profile Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp) // Adds a nice gap between the icon and button
        ) {
            SyncIcon(state = syncState)

            IconButton(
                onClick = onSettingsClick
            ) {
                Icon(
                    painter = painterResource(R.drawable.circle_user_round),
                    contentDescription = "Profile and Settings",
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun TodaySchedule(
    viewModel: AppViewModel,
    onTimetableClick: () -> Unit
) {
    val appData by viewModel.appData.collectAsState()
    val timetable = appData.timetable
    val today = LocalDate.now()

    if (timetable == null) {
        Text("No timetable added yet!")
        return
    }

    val termStart = appData.termStart?.let { LocalDate.parse(it) }
    val currentWeekNum = if (termStart != null && !today.isBefore(termStart)) {
        (ChronoUnit.WEEKS.between(termStart, today)).toInt() + 1
    } else {
        1 // Default to Week 1 for now
    }

    val activeWeekType = if (currentWeekNum % 2 == 0) "even" else "odd"
    val formatter = DateTimeFormatter.ofPattern("E d MMMM", Locale.getDefault())
    val dateString = today.format(formatter)
    val dayName = today.dayOfWeek.name.take(3).uppercase()

    val slotsToday = timetable.modules.flatMap { module ->
        module.slots.filter { slot ->
            slot.day.uppercase() == dayName && (slot.week.lowercase() == "all" || slot.week.lowercase() == activeWeekType)
        }.map { slot ->
            DisplaySlot(
                moduleCode = module.code,
                moduleName = module.name,
                day = slot.day,
                start = slot.start,
                end = slot.end,
                location = slot.location,
                sessionType = slot.sessionType,
                week = slot.week,
                classNo = slot.classNo
            )
        }
    }.sortedBy { it.start }

    // Grid dimensions
    val hourWidth = 100.dp
    val hourWidthPx = with(LocalDensity.current) { hourWidth.toPx() }
    val dayHeight = 90.dp
    val timeHeaderHeight = 30.dp

    // Auto-scroll to current time
    val scrollState = rememberScrollState()
    val screenSize = LocalWindowInfo.current.containerSize
    val halfScreenWidthPx = with(LocalDensity.current) { ((screenSize.width.toDp() - 60.dp) / 2).toPx() }
    val currentTime = LocalTime.now()

    val (minTime, maxTime, totalHours) = getTimeDetails(slotsToday)

    LaunchedEffect(Unit) {
        // Center the current-time line on the screen
        val currentOffsetPx = calculateOffset(currentTime, minTime, hourWidthPx)
        val targetPx = currentOffsetPx - halfScreenWidthPx
        if (targetPx > 0) {
            scrollState.scrollTo(targetPx.toInt())
        }
    }

    SectionTitle(text = "Today's schedule", subtext = "View Timetable", onSubtext = onTimetableClick)

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(text = dateString, fontWeight = FontWeight.Bold)

            if (slotsToday.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp, top = 24.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("No schedule today!")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Timetable
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(scrollState)
                    ) {
                        val gridTotalWidth = hourWidth * totalHours

                        Box(
                            modifier = Modifier
                                .width(gridTotalWidth)
                                .height(dayHeight + timeHeaderHeight + 2.dp)
                        ) {
                            // Horizontal dividing lines
                            for (i in 0..1) {
                                val yOffset = timeHeaderHeight + (dayHeight * i)
                                Box(
                                    modifier = Modifier.offset(y = yOffset).width(gridTotalWidth)
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                )
                            }

                            // Vertical dividing lines
                            for (i in 0..totalHours) {
                                val xOffset = hourWidth * i

                                // Vertical Line
                                Box(
                                    modifier = Modifier.offset(x = xOffset, y = timeHeaderHeight)
                                        .width(1.dp).height(
                                        dayHeight
                                    ).background(MaterialTheme.colorScheme.outlineVariant)
                                )

                                // Time Header Text
                                Text(
                                    text = minTime.plusHours(i.toLong())
                                        .format(DateTimeFormatter.ofPattern("HH:mm")),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.offset(
                                        x = xOffset + 4.dp,
                                        y = timeHeaderHeight - 20.dp
                                    )
                                )
                            }
                        }

                        // Module Blocks
                        slotsToday.forEach { slot ->
                            val slotStart = parseTimeString(slot.start)
                            val slotEnd = parseTimeString(slot.end)

                            val xOffset = calculateOffset(slotStart, minTime, hourWidth.value).dp
                            val blockWidth = calculateWidth(slotStart, slotEnd, hourWidth.value).dp

                            Box(
                                modifier = Modifier
                                    .offset(x = xOffset, y = timeHeaderHeight)
                                    .width(blockWidth)
                                    .height(dayHeight)
                                    .padding(horizontal = 2.dp, vertical = 4.dp)
                            ) {
                                TimetableGridBlock(
                                    educationLevel = timetable.educationLevel,
                                    slot = slot
                                )
                            }
                        }

                        // Current-Time line
                        if (currentTime.isAfter(minTime) && currentTime.isBefore(maxTime)) {
                            val timeLineOffset =
                                calculateOffset(currentTime, minTime, hourWidth.value).dp
                            Box(
                                modifier = Modifier
                                    .offset(x = timeLineOffset, y = timeHeaderHeight)
                                    .width(2.dp)
                                    .height(dayHeight)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .zIndex(2f)
                            )
                        }
                    }
                }
            }
        }
    }
}

fun formatRelativeDate(dataDate: String): String {
    if (dataDate.isBlank()) return ""

    return try {
        val due = LocalDate.parse(dataDate)
        val today = LocalDate.now()
        val daysBetween = ChronoUnit.DAYS.between(today, due)

        when {
            daysBetween < 0 -> "Overdue"
            daysBetween == 0L -> "Today"
            daysBetween == 1L -> "Tomorrow"
            else -> formatDate(dataDate)
        }
    } catch (e: Exception) {
        dataDate
    }
}

@Composable
fun Deadlines(
    viewModel: AppViewModel,
    deletedTask: Task?,
    onSelectDeletedTask: (Task?) -> Unit
) {
    val appData by viewModel.appData.collectAsState()

    val dueTasks = remember(appData.tasks) {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        // Calculate the cutoff date
        val cutoff = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            add(Calendar.DAY_OF_YEAR, 7)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }

        appData.tasks.filter { task ->
            if (task.done || task.due.isBlank()) return@filter false

            try {
                val dueDate = parser.parse(task.due)
                dueDate != null && !dueDate.after(cutoff.time)
            } catch (e: Exception) {
                false
            }
        }.sortedBy { it.due }
    }

    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        SectionTitle("Tasks due soon")
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 240.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(dueTasks) { task ->
            TaskCard(
                task = task,
                showDelete = deletedTask == task,
                onLongPress = { onSelectDeletedTask(task) },
                onNormalPress = { onSelectDeletedTask(null) },
                onToggle = { clickedTask ->
                    viewModel.completeTask(clickedTask)
                },
                onDelete = {
                    viewModel.deleteTask(task)
                    onSelectDeletedTask(null)
                },
                dueText = formatRelativeDate(task.due)
            )
        }
    }
}