package com.example.modulo.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.modulo.AppData
import com.example.modulo.AppViewModel
import com.example.modulo.Break
import com.example.modulo.EducationLevel
import com.example.modulo.Handbook
import com.example.modulo.R
import com.example.modulo.components.WarningCard
import com.example.modulo.getModuleColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HandbookPage(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onLoad: () -> Unit,
    onEdit: () -> Unit
) {
    val appData by viewModel.appData.collectAsState()
    val oldHandbooks = appData.otherHandbooks
    var selectedHandbook by remember { mutableStateOf<Handbook?>(null) }

    var deletedHandbook by remember { mutableStateOf<Handbook?>(null) }
    var showDeleteWarning by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(onTap = { deletedHandbook = null })
        },
    ) { paddingValues ->
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
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_left),
                            contentDescription = "Go Back"
                        )
                    }

                    Text(
                        text = "Past Handbooks",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            CurrentHandbook(
                appData = appData,
                onEdit = onEdit,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.padding(16.dp))

            if (oldHandbooks.isEmpty()) {
                Text("There are no past handbooks!")
            } else {
                Text("Past handbooks")
            }

            Spacer(modifier = Modifier.padding(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(oldHandbooks) { handbook ->
                    val showDelete = deletedHandbook == handbook

                    HandbookCard(
                        handbook = handbook,
                        showDelete = showDelete,
                        onClick = { deletedHandbook = null },
                        onHold = { deletedHandbook = handbook},
                        onDelete = { showDeleteWarning = true },
                        onSwap = { selectedHandbook = handbook },
                    )
                }
            }

            selectedHandbook?.let { handbook ->
                WarningCard(
                    title = "Swap Handbook",
                    text = "Are you sure you want to swap your handbook to ${handbook.id}?",
                    confirmText = "Swap",
                    isDanger = false,
                    onConfirm = {
                        selectedHandbook = null
                        viewModel.swapHandbook(handbook)
                        onLoad()
                    },
                    onDismiss = { selectedHandbook = null }
                )
            }

            if (showDeleteWarning) {
                deletedHandbook?.let { handbook ->
                    WarningCard(
                        title = "Delete Handbook",
                        text = "Are you sure you want to delete ${handbook.id} handbook? This will remove all data and cannot be recovered.",
                        confirmText = "Delete",
                        onConfirm = {
                            showDeleteWarning = false
                            viewModel.deleteHandbook(handbook)
                            deletedHandbook = null
                        },
                        onDismiss = {
                            showDeleteWarning = false
                            deletedHandbook = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CurrentDetail(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(0.35f),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            text = value,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun CurrentHandbook(
    appData: AppData,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = getModuleColor(handbookTitle(appData))

    val breaks = appData.breaks.joinToString("\n") { formatBreak(it) }

    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.container),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = handbookTitle(appData),
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.onContainer,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 12.dp)
                )

                IconButton(onClick = onEdit) {
                    Icon(
                        painter = painterResource(R.drawable.pencil),
                        contentDescription = "Edit Handbook",
                        tint = theme.onContainer
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CurrentDetail(label = "Education level", value = EducationLevel.getDisplay(appData.educationLevel))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                CurrentDetail(label = "Term Start", value = formatDateSimple(appData.termStart ?: ""))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                CurrentDetail(label = "Term End", value = formatDateSimple(appData.termEnd ?: ""))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                CurrentDetail(label = "Breaks", value = breaks)
            }
        }
    }
}

@Composable
fun HandbookCard(
    handbook: Handbook,
    showDelete: Boolean,
    onClick: () -> Unit,
    onHold: () -> Unit,
    onDelete: () -> Unit,
    onSwap: () -> Unit
) {
    val theme = getModuleColor(handbookTitle(handbook))

    val cardColour = if (showDelete) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.surface
    }

    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardColour),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onHold
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.container),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = handbookTitle(handbook),
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.onContainer,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 12.dp)
                )

                if (showDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            painter = painterResource(R.drawable.trash_2),
                            contentDescription = "Delete Handbook",
                            tint = theme.onContainer
                        )
                    }
                } else {
                    IconButton(onClick = onSwap) {
                        Icon(
                            painter = painterResource(R.drawable.refresh),
                            contentDescription = "Swap Handbook",
                            tint = theme.onContainer
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = EducationLevel.getDisplay(handbook.educationLevel),
                    style = MaterialTheme.typography.bodyMedium,
                )

                Text(
                    text = "${handbook.tasks.size} tasks",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

fun handbookTitle(handbook: Handbook): String {
    return if (handbook.educationLevel == "university" || handbook.educationLevel == "poly") {
        "AY${handbook.academicYear} • S${handbook.semester}"
    } else {
        "${handbook.academicYear} • Sem ${handbook.semester}"
    }
}

fun handbookTitle(handbook: AppData): String {
    return if (handbook.educationLevel == "university" || handbook.educationLevel == "poly") {
        "AY${handbook.academicYear} • S${handbook.semester}"
    } else {
        "${handbook.academicYear} • Sem ${handbook.semester}"
    }
}

fun formatBreak(currBreak: Break): String {
    return "${formatDateSimple(currBreak.start)} → ${formatDateSimple(currBreak.end)}"
}

fun formatDateSimple(dataDate: String): String {
    if (dataDate.isBlank()) return ""

    return try {
        val date = LocalDate.parse(dataDate) // parses "yyyy-MM-dd"
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault())
        date.format(formatter)
    } catch (e: Exception) {
        dataDate
    }
}