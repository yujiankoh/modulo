package com.example.modulo.pages

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.modulo.AppViewModel
import com.example.modulo.R
import com.example.modulo.R.drawable
import com.example.modulo.SortOption
import com.example.modulo.Task
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun AllTaskPage(
    viewModel: AppViewModel,
) {
    val appData by viewModel.appData.collectAsState()

    var isCompletedSectionExpanded by remember { mutableStateOf(false) }

    var deletedTask by remember { mutableStateOf<Task?>(null) }

    var currentSort by remember { mutableStateOf(SortOption.DUE_DATE) }
    var activeModuleFilter by remember { mutableStateOf<String?>(null) }

    val moduleCodes = remember {
        appData.tasks.map { it.module }.filter { it.isNotBlank() }.distinct()
    }

    var filteredTasks = appData.tasks
    if (activeModuleFilter != null) {
        filteredTasks = filteredTasks.filter { it.module == activeModuleFilter }
    }

    filteredTasks = when (currentSort) {
        SortOption.DUE_DATE -> filteredTasks.sortedBy { it.due }
        SortOption.MODULE_CODE -> filteredTasks.sortedBy { it.module }
        SortOption.TYPE -> filteredTasks.sortedBy { it.type }
    }

    val uncompletedTasks = filteredTasks.filter { !it.done }.sortedBy { it.due }
    val completedTasks = filteredTasks.filter { it.done }.sortedByDescending { it.due }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    deletedTask = null
                })
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Sort By:", style = MaterialTheme.typography.labelMedium)

            // Horizontally scrolling row for Sort buttons
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SortOption.entries.toTypedArray()) { option ->
                    FilterChip(
                        selected = currentSort == option,
                        onClick = { currentSort = option },
                        label = { Text(option.displayName) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (moduleCodes.isNotEmpty()) {
                Text("Filter Module:", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                    item {
                        FilterChip(
                            selected = activeModuleFilter == null,
                            onClick = { activeModuleFilter = null },
                            label = { Text("All") }
                        )
                    }

                    // Generate a button for every module code the user has tasks for
                    items(moduleCodes) { moduleCode ->
                        FilterChip(
                            selected = activeModuleFilter == moduleCode,
                            onClick = { activeModuleFilter = moduleCode },
                            label = { Text(moduleCode) }
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (uncompletedTasks.isEmpty()) {
                item {
                    Text(
                        text = "All pending tasks are completed!",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                item {
                    Text(
                        text = "${uncompletedTasks.size} Uncompleted ${ if (uncompletedTasks.size < 2) "Work" else "Works" }",
                        modifier = Modifier.padding(16.dp))
                }

                items(uncompletedTasks) { task ->
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
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isCompletedSectionExpanded = !isCompletedSectionExpanded }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${completedTasks.size} Completed ${if (completedTasks.size < 2) "Work" else "Works"}")

                    Icon(
                        painter = if (isCompletedSectionExpanded) painterResource(R.drawable.chevron) else painterResource(R.drawable.chevron_down),
                        contentDescription = if (isCompletedSectionExpanded) "Hide Completed" else "Show Completed"
                    )
                }
            }

            if (isCompletedSectionExpanded) {
                if (completedTasks.isEmpty()) {
                    item {
                        Text(
                            text = "No completed tasks yet.",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    items(completedTasks) { task ->
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
                            }
                        )
                    }
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
    dueText: String = formatDate(task.due)
) {

    val cardColour = if (showDelete) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {onNormalPress()},
                onLongClick = {onLongPress()}
            )
            .padding(vertical = 4.dp, horizontal = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = cardColour),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = task.title,
                        textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
                        modifier = Modifier.padding(start = 8.dp)
                    )

                    if (showDelete) {
                        IconButton(onClick = { onDelete() }) {
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
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row {
                        Text(
                            text = task.module,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier
                                .border(width = 1.dp, color = MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        Spacer(Modifier.padding(4.dp))
                        Text(
                            text = task.type.replaceFirstChar { it.uppercase() },
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier
                                .border(width = 1.dp, color = MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (dueText.isNotEmpty()) {
                        Text(
                            text = if (dueText == "Overdue") "$dueText!" else "Due: $dueText",
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

fun formatDate(dataDate: String): String {
    if (dataDate.isBlank()) return ""

    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        val date = parser.parse(dataDate)

        if (date != null) formatter.format(date) else dataDate
    } catch (e: Exception) {
        dataDate
    }
}