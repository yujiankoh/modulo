package com.example.modulo.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.example.modulo.AppViewModel
import com.example.modulo.Module
import com.example.modulo.R
import com.example.modulo.SyncState
import com.example.modulo.Task
import com.example.modulo.TimetableState
import com.example.modulo.components.WarningCard
import com.example.modulo.getModuleColor
import com.example.modulo.helpers.NotesHelper
import com.example.modulo.helpers.NotesHelper.Note
import com.example.modulo.ui.theme.ModuloTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    onAddTaskForModule: (String) -> Unit = {},
) {
    // Collect info from the model
    val appData by viewModel.appData.collectAsState()
    val timetableState by viewModel.timetableState.collectAsState()

    var deletedTask by remember { mutableStateOf<Task?>(null) }

    Scaffold(
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(onTap = { deletedTask = null })
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(
                bottom = paddingValues.calculateBottomPadding() + 24.dp
            )
        ) {
            if (appData.timetable == null && timetableState is TimetableState.Idle) {
                item {
                    MissingTimetableBanner(onUploadClicked = onUploadTimetable)
                }
            }

            item {
                ProfileBar(viewModel = viewModel, onSettingsClick = onSettingsClick)
            }

            item {
                TodaySchedule(viewModel = viewModel, onTimetableClick = onTimetableClick)
            }

            item {
                Deadlines(viewModel = viewModel, deletedTask = deletedTask, onSelectDeletedTask = { deletedTask = it })
            }

            item {
                Modules(viewModel = viewModel, onAddTaskForModule = onAddTaskForModule)
            }
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp, top = 24.dp),
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
                                    modifier = Modifier
                                        .offset(y = yOffset)
                                        .width(gridTotalWidth)
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                )
                            }

                            // Vertical dividing lines
                            for (i in 0..totalHours) {
                                val xOffset = hourWidth * i

                                // Vertical Line
                                Box(
                                    modifier = Modifier
                                        .offset(x = xOffset, y = timeHeaderHeight)
                                        .width(1.dp)
                                        .height(
                                            dayHeight
                                        )
                                        .background(MaterialTheme.colorScheme.outlineVariant)
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

@Composable
fun Modules(
    viewModel: AppViewModel,
    onAddTaskForModule: (String) -> Unit = {},
) {
    val appData by viewModel.appData.collectAsState()
    val modulesList = appData.timetable?.modules ?: emptyList()

    var selectedModule by remember { mutableStateOf<Module?>(null) }
    var manageOpen by remember { mutableStateOf(false) }

    if (modulesList.isEmpty()) {
        Box(modifier = Modifier
            .fillMaxWidth()
            .height(100.dp), contentAlignment = Alignment.Center) {
            Text("No modules found in AppData!", color = MaterialTheme.colorScheme.error)
        }
        return
    }
    
    val hidden = appData.hiddenModules.toSet()
    val visibleModules = modulesList.filter { it.code.ifBlank { it.name } !in hidden }

    SectionTitle("Modules", subtext = "Manage", onSubtext = { manageOpen = true })

    if (visibleModules.isEmpty()) {
        Box(modifier = Modifier
            .fillMaxWidth()
            .height(100.dp), contentAlignment = Alignment.Center) {
            Text(
                "All modules hidden - tap Manage to show some.",
                style = MaterialTheme.typography.bodyMedium,
                color = ModuloTheme.colors.subText
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            visibleModules.chunked(3).forEach { rowModules ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    for (i in 0 until 3) {
                        if (i < rowModules.size) {
                            val module = rowModules[i]
                            val label = module.code.ifBlank { module.name }
                            val uncompletedCount = appData.tasks.count { !it.done && it.module == label }

                            ModuleCard(
                                moduleCode = module.code,
                                moduleName = module.name,
                                uncompletedTasksCount = uncompletedCount,
                                educationLevel = appData.educationLevel ?: "",
                                onClick = { selectedModule = module },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    selectedModule?.let { module ->
        ViewModuleCard(
            module = module,
            viewModel = viewModel,
            onAddTask = { label ->
                selectedModule = null
                onAddTaskForModule(label)
            },
            onDismiss = { selectedModule = null }
        )
    }

    if (manageOpen) {
        ManageModulesDialog(
            modules = modulesList,
            hidden = hidden,
            onToggle = { label, hide -> viewModel.setModuleHidden(label, hide) },
            onDismiss = { manageOpen = false }
        )
    }
}

@Composable
fun ManageModulesDialog(
    modules: List<Module>,
    hidden: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val screenHeight = with(density) { windowInfo.containerSize.height.toDp() }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * 0.7f),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Manage modules",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Untick a module to hide it from your dashboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ModuloTheme.colors.subText,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(modules) { module ->
                        val label = module.code.ifBlank { module.name }
                        val title = if (module.name.isNotBlank() && module.name != label) "$label · ${module.name}" else label
                        val shown = label !in hidden

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onToggle(label, shown) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = shown,
                                onCheckedChange = { onToggle(label, shown) }
                            )
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(getModuleColor(label).container)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
fun ModuleCard(
    moduleCode: String,
    moduleName: String,
    uncompletedTasksCount: Int,
    educationLevel: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    // Falls back seamlessly to ModuloTheme system or standard palette values
    val theme = getModuleColor(moduleCode)

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.3f)
                    .background(theme.container)
                    .padding(12.dp),
            ) {
                if (educationLevel == "poly" || educationLevel == "university") {
                    Text(
                        text = moduleCode,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = theme.onContainer
                    )
                    Text(
                        text = moduleName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.onContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = moduleName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = theme.onContainer
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.7f)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$uncompletedTasksCount " + if (uncompletedTasksCount == 1) "task" else "tasks",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Module detail dialog
@Composable
fun ViewModuleCard(
    module: Module,
    viewModel: AppViewModel,
    onAddTask: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val appData by viewModel.appData.collectAsState()
    val notesData by viewModel.notesData.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gated = viewModel.notesGated()

    // Tasks and notes are tagged by the module's label (code, or name when code is blank).
    val label = module.code.ifBlank { module.name }
    val theme = getModuleColor(label)
    val title = if (module.name.isNotBlank() && module.name != label) "$label\n${module.name}" else label

    var uploadOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Note?>(null) }
    var deleteTarget by remember { mutableStateOf<Note?>(null) }
    var deletedTask by remember { mutableStateOf<Task?>(null) }

    LaunchedEffect(gated) { if (!gated) viewModel.loadNotes() }

    val moduleTasks = appData.tasks
        .filter { it.module == label }
        .sortedWith(compareBy({ it.done }, { it.due }))

    val cache = notesData.notes
    val moduleNotes = NotesHelper.visibleNotes(
        cache ?: emptyList(),
        handbookId = appData.handbookId,
        module = label
    )

    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val screenHeight = with(density) { windowInfo.containerSize.height.toDp() }

    fun openNote(note: Note) {
        scope.launch {
            val bytes = viewModel.downloadNote(note.id) ?: return@launch
            withContext(Dispatchers.IO) { openBytes(context, bytes, note.name, note.mimeType) }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp)
                .heightIn(max = screenHeight * 0.8f),
            shape = RoundedCornerShape(36.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Coloured header band.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.container)
                        .padding(24.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = theme.onContainer
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text("My notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    when {
                        gated -> ModuleNotesHint()

                        cache == null -> Text(
                            text = if (notesData.loading) "Loading notes…" else "Couldn't load notes - open the Notes view.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ModuloTheme.colors.subText
                        )

                        moduleNotes.isEmpty() -> Text(
                            text = "No notes for this module yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ModuloTheme.colors.subText
                        )

                        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            moduleNotes.take(5).forEach { note ->
                                NoteRowCard(
                                    note = note,
                                    onOpen = { openNote(note) },
                                    onRename = { renameTarget = note },
                                    onDelete = { deleteTarget = note }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onAddTask(label) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(painter = painterResource(R.drawable.plus), contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add task")
                        }
                        if (!gated) {
                            Button(
                                onClick = { uploadOpen = true },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(painter = painterResource(R.drawable.plus), contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add note")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Tasks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (moduleTasks.isEmpty()) {
                        Text(
                            text = "No tasks for this module.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ModuloTheme.colors.subText
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = screenHeight * 0.3f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(moduleTasks) { task ->
                                TaskCard(
                                    task = task,
                                    showDelete = deletedTask == task,
                                    onLongPress = { deletedTask = task },
                                    onNormalPress = { deletedTask = null },
                                    onToggle = { clickedTask -> viewModel.completeTask(clickedTask) },
                                    onDelete = {
                                        viewModel.deleteTask(task)
                                        deletedTask = null
                                    },
                                    showModule = false
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.End),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }

    if (uploadOpen) {
        UploadNoteDialog(
            timetableLabels = timetableLabels(appData),
            lockedModule = label,
            onDismiss = { uploadOpen = false },
            onUpload = { uri, mod, onResult -> viewModel.uploadNote(uri, mod, onResult) }
        )
    }

    renameTarget?.let { note ->
        RenameNoteDialog(
            current = note.name,
            onDismiss = { renameTarget = null },
            onSave = { newName, onResult -> viewModel.renameNote(note.id, newName, onResult) }
        )
    }

    deleteTarget?.let { note ->
        WarningCard(
            title = "Delete note",
            text = "Delete \"${note.name}\"? This removes it from your Google Drive.",
            confirmText = "Delete",
            onConfirm = {
                viewModel.deleteNote(note.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
private fun ModuleNotesHint() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.cloud_off),
            contentDescription = null,
            tint = ModuloTheme.colors.subText,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Sign in with Google in Settings to keep notes for this module.",
            style = MaterialTheme.typography.bodyMedium,
            color = ModuloTheme.colors.subText
        )
    }
}