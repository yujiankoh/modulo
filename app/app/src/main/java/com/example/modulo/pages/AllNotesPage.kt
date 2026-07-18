package com.example.modulo.pages

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.modulo.AppData
import com.example.modulo.AppViewModel
import com.example.modulo.R
import com.example.modulo.components.DropDownMenu
import com.example.modulo.components.WarningCard
import com.example.modulo.getModuleColor
import com.example.modulo.helpers.NotesHelper
import com.example.modulo.helpers.NotesHelper.Note
import com.example.modulo.ui.theme.ModuloTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.text.isNotBlank

private const val OTHER = "__other__"
private var dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

@Composable
fun AllNotesPage(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val appData by viewModel.appData.collectAsState()
    val notesData by viewModel.notesData.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val gated = viewModel.notesGated()

    var thisSemesterOnly by remember { mutableStateOf(true) }
    var sort by remember { mutableStateOf(NotesHelper.NoteSort.NAME) }
    var moduleFilter by remember { mutableStateOf<String?>(null) }

    var uploadOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Note?>(null) }
    var deleteTarget by remember { mutableStateOf<Note?>(null) }

    LaunchedEffect(gated) { if (!gated) viewModel.loadNotes() }

    val cache = notesData.notes
    val scopeHandbookId = if (thisSemesterOnly) appData.handbookId else null
    val moduleOptions = remember(cache, scopeHandbookId) {
        NotesHelper.noteModules(NotesHelper.visibleNotes(cache ?: emptyList(), handbookId = scopeHandbookId))
    }
    val shown = NotesHelper.visibleNotes(
        cache ?: emptyList(),
        handbookId = scopeHandbookId,
        module = moduleFilter,
        sort = sort
    )

    fun openNote(note: Note) {
        scope.launch {
            val bytes = viewModel.downloadNote(note.id) ?: return@launch
            withContext(Dispatchers.IO) { openBytes(context, bytes, note.name, note.mimeType) }
        }
    }

    Scaffold(
        floatingActionButton = {
            if (!gated) {
                FloatingActionButton(
                    onClick = { uploadOpen = true },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        painter = painterResource(R.drawable.plus),
                        contentDescription = "Upload note"
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
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
                    IconButton(onClick = onBack) {
                        Icon(painter = painterResource(R.drawable.arrow_left), contentDescription = "Go Back")
                    }
                    Text(
                        text = "Notes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (!gated) {
                    Button(
                        onClick = { viewModel.refreshNotes() },
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(painter = painterResource(R.drawable.refresh), contentDescription = "Refresh")
                        Spacer(modifier = Modifier.padding(6.dp))
                        Text("Refresh")
                    }
                }
            }

            if (gated) {
                NotesGate()
                return@Column
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text("Sort By:", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        NoteFilterChip(
                            selected = sort == NotesHelper.NoteSort.NAME,
                            onClick = { sort = NotesHelper.NoteSort.NAME },
                            label = "A-Z"
                        )
                    }
                    item {
                        NoteFilterChip(
                            selected = sort == NotesHelper.NoteSort.NEWEST,
                            onClick = { sort = NotesHelper.NoteSort.NEWEST },
                            label = "Newest"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("Filter:", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        NoteFilterChip(
                            selected = thisSemesterOnly,
                            onClick = { thisSemesterOnly = true },
                            label = "This semester"
                        )
                    }
                    item {
                        NoteFilterChip(
                            selected = !thisSemesterOnly,
                            onClick = { thisSemesterOnly = false },
                            label = "All semesters"
                        )
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        NoteFilterChip(
                            selected = moduleFilter == null,
                            onClick = { moduleFilter = null },
                            label = "All"
                        )
                    }
                    items(moduleOptions) { module ->
                        NoteFilterChip(
                            selected = moduleFilter == module,
                            onClick = { moduleFilter = module },
                            label = module
                        )
                    }
                }

            }

            Spacer(modifier = Modifier.height(16.dp))

            notesData.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            when {
                notesData.loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Loading notes…")
                    }
                }

                shown.isEmpty() && cache != null -> NotesEmpty(anyAtAll = cache.isNotEmpty())

                else -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (note in shown) {
                        NoteRowCard(
                            note = note,
                            onOpen = { openNote(note) },
                            onRename = { renameTarget = note },
                            onDelete = { deleteTarget = note }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (uploadOpen) {
        UploadNoteDialog(
            timetableLabels = timetableLabels(appData) + moduleOptions,
            onDismiss = { uploadOpen = false },
            onUpload = { uri, module, onResult -> viewModel.uploadNote(uri, module, onResult) }
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
private fun NoteFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(8.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            selectedLabelColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
fun NoteRowCard(
    note: Note,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (note.module.isNotBlank()) getModuleColor(note.module).container
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        note.module.ifBlank { null },
                        NotesHelper.formatSize(note.size).ifBlank { null },
                        formatNoteDate(note.modifiedTime).ifBlank { null }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = ModuloTheme.colors.subText
                )
            }
            IconButton(onClick = onRename, modifier = Modifier.size(36.dp)) {
                Icon(
                    painter = painterResource(R.drawable.pencil),
                    contentDescription = "Rename ${note.name}"
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    painter = painterResource(R.drawable.trash_2),
                    contentDescription = "Delete ${note.name}",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun UploadNoteDialog(
    timetableLabels: List<String>,
    onDismiss: () -> Unit,
    onUpload: (Uri, String, (String?) -> Unit) -> Unit,
    lockedModule: String? = null
) {
    var uri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var moduleChoice by remember { mutableStateOf(lockedModule ?: "") }
    var otherModule by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var uploading by remember { mutableStateOf(false) }

    val labels = remember(timetableLabels) { timetableLabels.distinct().sorted() }
    val options = listOf("") + labels + listOf(OTHER)

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { picked ->
        uri = picked
        fileName = picked?.lastPathSegment ?: ""
    }

    AlertDialog(
        containerColor = MaterialTheme.colorScheme.background,
        onDismissRequest = onDismiss,
        title = { Text("Upload note", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Button(
                    onClick = { picker.launch(NotesHelper.NOTE_MIME_TYPES) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (uri == null) "Choose a PDF or image" else fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (lockedModule != null) {
                    // Opened from a module card, the module is fixed to that card.
                    Text(
                        text = "Module: $lockedModule",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                } else {
                    DropDownMenu(
                        label = "Module",
                        selectedItem = moduleChoice,
                        items = options,
                        itemToText = {
                            when (it) {
                                "" -> "- None -"
                                OTHER -> "+ Add other…"
                                else -> it ?: ""
                            }
                        },
                        onItemSelected = { moduleChoice = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (moduleChoice == OTHER) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = otherModule,
                        onValueChange = { otherModule = it },
                        singleLine = true,
                        placeholder = { Text("Module label") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val chosen = uri
                    if (chosen == null) { error = "Choose a file first."; return@Button }
                    val module = if (moduleChoice == OTHER) otherModule.trim() else moduleChoice
                    uploading = true
                    error = null
                    onUpload(chosen, module) { result ->
                        uploading = false
                        if (result == null) onDismiss() else error = result
                    }
                },
                enabled = !uploading,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (uploading) "Uploading…" else "Upload")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) { Text("Cancel") }
        }
    )
}

@Composable
fun RenameNoteDialog(
    current: String,
    onDismiss: () -> Unit,
    onSave: (String, (String?) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf(current) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = onDismiss,
        title = { Text("Rename note", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isBlank()) { error = "Enter a name."; return@Button }
                    if (trimmed == current) { onDismiss(); return@Button }
                    saving = true
                    error = null
                    onSave(trimmed) { result ->
                        saving = false
                        if (result == null) onDismiss() else error = result
                    }
                },
                enabled = !saving,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (saving) "Saving…" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) { Text("Cancel") }
        }
    )
}

@Composable
private fun NotesGate() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.cloud_off),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = ModuloTheme.colors.subText
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("Notes need Google Drive", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Sign in with Google in Settings to keep your notes synced across devices.",
                style = MaterialTheme.typography.bodyMedium,
                color = ModuloTheme.colors.subText
            )
        }
    }
}

@Composable
private fun NotesEmpty(anyAtAll: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.file_clock),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = ModuloTheme.colors.subText
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (anyAtAll) "Nothing here" else "No notes yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (anyAtAll) "No notes match these filters - try All semesters or All modules."
            else "Upload a PDF or a photo of your notes to keep it synced with this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = ModuloTheme.colors.subText,
            textAlign = TextAlign.Center
        )
    }
}

fun timetableLabels(appData: AppData): List<String> =
    appData.timetable?.modules.orEmpty()
        .map { it.code.ifBlank { it.name } }
        .filter { it.isNotBlank() }

private fun formatNoteDate(iso: String): String = try {
    Instant.parse(iso).atZone(ZoneId.systemDefault()).format(dateFormat)
} catch (e: Exception) {
    ""
}

// Write the bytes to a cache file and hand a viewer app a content
fun openBytes(context: Context, bytes: ByteArray, name: String, mimeType: String) {
    val dir = File(context.cacheDir, "notes").apply { mkdirs() }
    val safeName = name.ifBlank { "note" }.replace("/", "_")
    val file = File(dir, safeName).apply { writeBytes(bytes) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType.ifBlank { "*/*" })
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}