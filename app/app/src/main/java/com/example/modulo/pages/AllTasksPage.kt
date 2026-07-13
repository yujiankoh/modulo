package com.example.modulo.pages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.modulo.AppViewModel
import com.example.modulo.R
import com.example.modulo.SortOption
import com.example.modulo.Task
import com.example.modulo.getModuleColor
import com.example.modulo.ui.theme.ModuloTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AllTaskPage(
    viewModel: AppViewModel,
) {
    val appData by viewModel.appData.collectAsState()

    var deletedTask by remember { mutableStateOf<Task?>(null) }

    var currentSort by remember { mutableStateOf(SortOption.DUE_DATE) }
    var activeModuleFilter by remember { mutableStateOf<String?>(null) }
    var activeTaskTypeFilter by remember { mutableStateOf<String?>(null) }

    val moduleCodes = remember {
        appData.tasks.map { it.module }.filter { it.isNotBlank() }.distinct()
    }
    val taskTypes = remember {
        appData.tasks.map { it.type }.filter { it.isNotBlank() }.distinct()
    }

    var filteredTasks = appData.tasks
    if (activeModuleFilter != null) {
        filteredTasks = filteredTasks.filter { it.module == activeModuleFilter }
    }
    if (activeTaskTypeFilter != null) {
        filteredTasks = filteredTasks.filter {it.type == activeTaskTypeFilter}
    }

    filteredTasks = when (currentSort) {
        SortOption.DUE_DATE -> filteredTasks.sortedBy { it.due }
        SortOption.MODULE_CODE -> filteredTasks.sortedBy { it.module }
        SortOption.TYPE -> filteredTasks.sortedBy { it.type }
        SortOption.TITLE -> filteredTasks.sortedBy { it.title }
    }

    val uncompletedTasks = filteredTasks.filter { !it.done }.sortedBy { it.due }
    val completedTasks = filteredTasks.filter { it.done }.sortedByDescending { it.due }

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Scaffold (
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(onTap = { deletedTask = null })
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Sort By:", style = MaterialTheme.typography.labelMedium)

                // Horizontally scrolling row for Sort buttons
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SortOption.entries.toTypedArray()) { option ->
                        FilterChip(
                            selected = currentSort == option,
                            onClick = { currentSort = option },
                            label = { Text(option.displayName) },
                            shape = RoundedCornerShape(8.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (moduleCodes.isNotEmpty()) {
                    Text("Filter:", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                        item {
                            FilterChip(
                                selected = activeModuleFilter == null,
                                onClick = { activeModuleFilter = null },
                                label = { Text("All") },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        // Generate a button for every module code the user has tasks for
                        items(moduleCodes) { moduleCode ->
                            FilterChip(
                                selected = activeModuleFilter == moduleCode,
                                onClick = { activeModuleFilter = moduleCode },
                                label = { Text(moduleCode) },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }

                if (taskTypes.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                        item {
                            FilterChip(
                                selected = activeTaskTypeFilter == null,
                                onClick = { activeTaskTypeFilter = null },
                                label = { Text("All") },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        // Generate a button for every task type the user has tasks for
                        items(taskTypes) { taskType ->
                            FilterChip(
                                selected = activeTaskTypeFilter == taskType,
                                onClick = { activeTaskTypeFilter = taskType },
                                label = { Text(taskType.replaceFirstChar { it.uppercase() }) },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            }

            SecondaryTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("Pending (${uncompletedTasks.size})") }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("Completed (${completedTasks.size})") }
                )
            }

            when (selectedTabIndex) {
                0 -> {
                    TaskColumn(
                        viewModel = viewModel,
                        tasks = uncompletedTasks,
                        emptyText = "All pending tasks are completed!",
                        deletedTask = deletedTask,
                        onSelectDeletedTask = { deletedTask = it }
                    )
                }

                1 -> {
                    TaskColumn(
                        viewModel = viewModel,
                        tasks = completedTasks,
                        emptyText = "No completed tasks yet.",
                        deletedTask = deletedTask,
                        onSelectDeletedTask = { deletedTask = it }
                    )
                }
            }
        }
    }
}

@Composable
fun TaskCard(
    task: Task,
    showDelete: Boolean,
    onLongPress: () -> Unit,
    onNormalPress: () -> Unit,
    onToggle: (Task) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    dueText: String = formatDate(task.due)
) {

    val theme = getModuleColor(task.module.ifBlank { task.title })

    val cardColour = if (showDelete) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {onNormalPress()},
                onLongClick = {onLongPress()}
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(2.dp, theme.container),
        colors = CardDefaults.cardColors(containerColor = cardColour),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row {
                if (showDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            painter = painterResource(R.drawable.trash_2),
                            contentDescription = "Delete Task"
                        )
                    }
                } else {
                    Checkbox(
                        checked = task.done,
                        onCheckedChange = { onToggle(task) },
                    )
                }

                Column(
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = task.title,
                        textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Badge(containerColor = theme.container)
                        Spacer(modifier.padding(4.dp))

                        Text(
                            text = "${task.module.ifBlank { task.title }} • ${task.type.replaceFirstChar { it.uppercase() }}",
                            fontSize = 12.sp,
                            color = if (showDelete) MaterialTheme.colorScheme.onError.copy(alpha = 0.7f) else ModuloTheme.colors.subText
                        )
                    }

                }
            }
            if (dueText.isNotEmpty()) {
                Badge(containerColor = if (showDelete) MaterialTheme.colorScheme.onError.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) {
                    Text(
                        text = dueText,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
        }
    }
}

fun formatDate(dataDate: String): String {
    if (dataDate.isBlank()) return ""

    return try {
        val date = LocalDate.parse(dataDate) // parses "yyyy-MM-dd"
        val formatter = DateTimeFormatter.ofPattern("E d MMMM", Locale.getDefault())
        date.format(formatter)
    } catch (e: Exception) {
        dataDate
    }
}

@Composable
fun TaskColumn(
    viewModel: AppViewModel,
    tasks: List<Task>,
    emptyText: String,
    deletedTask: Task?,
    onSelectDeletedTask: (Task?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (tasks.isEmpty()) {
            item {
                Text(
                    text = emptyText,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        } else {
            items(tasks) { task ->
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
                    }
                )
            }
        }
    }
}