package com.example.modulo.pages

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.modulo.AppViewModel
import com.example.modulo.Handbook
import com.example.modulo.R
import com.example.modulo.components.WarningCard

@Composable
fun HandbookPage(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onLoad: () -> Unit,
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

            if (oldHandbooks.isEmpty()) {
                Text("There are no past handbooks!")
            }

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
                        onClick = {
                            if (deletedHandbook == null) {
                                selectedHandbook = handbook
                            } else {
                                deletedHandbook = null
                            }
                        },
                        onHold = { deletedHandbook = handbook},
                        onDelete = { showDeleteWarning = true }
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
fun HandbookCard(
    handbook: Handbook,
    showDelete: Boolean,
    onClick: () -> Unit,
    onHold: () -> Unit,
    onDelete: () -> Unit
) {
    val cardColour = if (showDelete) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.surface
    }

    ElevatedCard(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardColour),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onHold
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .defaultMinSize(minHeight = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = handbook.id,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (showDelete) {
                IconButton(onClick = onDelete) {
                    Icon(
                        painter = painterResource(R.drawable.trash_2),
                        contentDescription = "Delete Handbook"
                    )
                }
            }
        }
    }
}