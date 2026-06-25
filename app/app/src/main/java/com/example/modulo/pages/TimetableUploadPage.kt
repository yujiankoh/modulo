package com.example.modulo.pages

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.modulo.AppViewModel
import com.example.modulo.EducationLevel
import com.example.modulo.R
import com.example.modulo.TimetableState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun UploadTimetablePage(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onManualClick: (EducationLevel) -> Unit,
) {
    val context = LocalContext.current
    var selectedEducation by remember { mutableStateOf(EducationLevel.UNIVERSITY) }

    var failCount by remember { mutableIntStateOf(0) }
    val timetableState by viewModel.timetableState.collectAsState()

    LaunchedEffect(timetableState) {
        if (timetableState is TimetableState.Error) {
            failCount++
        }
    }

    Scaffold { paddingValues ->
        Column(
            modifier =  Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_left),
                        contentDescription = "Go Back"
                    )
                }

                Text(
                    text = "Upload Timetable",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            EducationDropDownMenu(
                selectedEdu = selectedEducation,
                onEduSelected = {selectedEducation = it}
            )

            Spacer(modifier = Modifier.height(8.dp))

            UploadTimetable(
                context = context,
                onUploadTimetable = { imageBytes ->
                    viewModel.uploadTimetable(
                        imageBytes = imageBytes,
                        mimeType = "image/jpeg",
                        educationLevel = selectedEducation.json
                    )

                    onBack()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                contentAlignment = Alignment.Center
            ) {
                if (failCount >= 0) {
                    TextButton(onClick = { onManualClick(selectedEducation) } ) {
                        Text("Having trouble? Enter timetable manually")
                    }
                }
            }
        }
    }

}

@Composable
fun UploadTimetable(
    context: Context,
    onUploadTimetable: (ByteArray) -> Unit
) {
    var imageUri by remember { mutableStateOf<Uri?>(null) }

    val coroutineScope = rememberCoroutineScope()

    // Launch the Gallery to choose photo
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        imageUri = uri
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text("Upload Timetable")
        Spacer(modifier = Modifier.height(8.dp))

        // Image preview box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable{ galleryLauncher.launch("image/*") }
                .background(color = MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            if (imageUri != null) {
                // If they picked from Gallery
                AsyncImage(
                    model = imageUri,
                    contentDescription = "Gallery Upload",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Default empty
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(R.drawable.plus),
                        contentDescription = "Upload",
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Tap to select an image")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                coroutineScope.launch(Dispatchers.IO) {
                    val finalBitmap = imageUri?.let { getBitmapFromUri(context, it) }

                    if (finalBitmap != null) {
                        val stream = ByteArrayOutputStream()
                        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                        val bytes = stream.toByteArray()

                        withContext(Dispatchers.Main) {
                            onUploadTimetable(bytes)
                        }
                    }
                }
            },
            enabled = imageUri != null
        ) {
            Text("Upload Timetable")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EducationDropDownMenu(
    selectedEdu: EducationLevel,
    onEduSelected: (EducationLevel) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = { isExpanded = !isExpanded }
    ) {
        OutlinedTextField(
            value = selectedEdu.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Education Level") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )

        ExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false }
        ) {
           EducationLevel.entries.forEach { item ->
                DropdownMenuItem(
                    text = { Text(text = item.displayName) },
                    onClick = {
                        onEduSelected(item)
                        isExpanded = false
                    }
                )
            }
        }
    }
}

fun getBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun TimetableStateOverlay(
    state: TimetableState,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    when (state) {
        is TimetableState.Processing -> {
            Card(
                modifier = modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Reading your timetable...\nYou can continue using the app.",
                    )
                }
            }
        }

        is TimetableState.ReviewData -> {
            AlertDialog(
                onDismissRequest = { viewModel.clearTimetableState() },
                title = { Text("Confirm Schedule Data") },
                text = {
                    Column {
                        Text("We detected the following modules. Please verify before saving:")
                        Spacer(modifier = Modifier.height(12.dp))

                        // Scrollable list if they have many modules
                        state.timetable.modules.forEach { module ->
                            Text(
                                text = "• ${module.code}: ${module.name}",
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { viewModel.saveTimetable(state.timetable) }) {
                        Text("Confirm Timetable")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.clearTimetableState() }) {
                        Text("Discard")
                    }
                }
            )
        }

        is TimetableState.Error -> {
            AlertDialog(
                onDismissRequest = { viewModel.clearTimetableState() },
                title = { Text("Parsing Failed") },
                text = { Text(state.message) },
                confirmButton = {
                    Button(onClick = { viewModel.clearTimetableState() }) {
                        Text("OK")
                    }
                }
            )
        }

        is TimetableState.Idle -> {}
    }
}

@Composable
fun MissingTimetableBanner(onUploadClicked: () -> Unit) {
    Card(
        onClick = onUploadClicked,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            // Uses a soft tinted background to make it stand out from normal tasks
            containerColor = MaterialTheme.colorScheme.error
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.circle_alert),
                contentDescription = "Missing Timetable",
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "No timetable detected. Tap here to upload your schedule automatically.",
            )
        }
    }
}