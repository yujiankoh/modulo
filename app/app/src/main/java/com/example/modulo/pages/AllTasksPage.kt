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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.modulo.AppViewModel
import com.example.modulo.Task
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun AllTaskPage(
    viewModel: AppViewModel,
) {
    val appData by viewModel.appData.collectAsState()
    val uncompletedTasks = appData.tasks.filter { !it.done }.sortedBy { it.due }
    val completedTasks = appData.tasks.filter { it.done }.sortedByDescending { it.due }

    var isCompletedSectionExpanded by remember { mutableStateOf(false) }

    var deletedTask by remember { mutableStateOf<Task?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    deletedTask = null
                })
            }
    ) {
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
                        imageVector = if (isCompletedSectionExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
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
                                imageVector = Icons.Default.Delete,
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

                //Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row {
                        Text(
                            text = task.module,
                            modifier = Modifier
                                .border(width = 1.dp, color = MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                        Spacer(Modifier.padding(4.dp))
                        Text(
                            text = task.type,
                            modifier = Modifier
                                .border(width = 1.dp, color = MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = "Due: $dueText",
                        modifier = Modifier.padding(end = 8.dp)
                    )
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