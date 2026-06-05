package com.example.modulo.pages

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.modulo.AppViewModel
import com.google.android.gms.common.util.AndroidUtilsLight
import kotlin.contracts.contract

@Composable
fun CalendarPage(
    viewModel: AppViewModel
) {
    // Collect info from the model
    val appData by viewModel.appData.collectAsState()

    val timetable = appData.timetable

    var selectedEducation by remember { mutableStateOf("primary") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (timetable == null) {
            Text("There is no timetable detected for this semester, please upload one")
            Spacer(modifier = Modifier.height(8.dp))

            EducationDropDownMenu(
                selectedEdu = selectedEducation,
                onEduSelected = {selectedEducation = it}
            )

            Spacer(modifier = Modifier.height(8.dp))
            UploadTimetable()
        }
    }
}

@Composable
fun UploadTimetable() {
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var imageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Launch the Gallery to choose photo
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        imageUri = uri
        imageBitmap = null
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        imageBitmap = bitmap
        imageUri = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
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
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            when {
                // If they picked from Gallery
                imageUri != null -> {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = "Gallery Upload",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // If they took a photo
                imageBitmap != null -> {
                    Image(
                        bitmap = imageBitmap!!.asImageBitmap(),
                        contentDescription = "Camera Timetable",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // Default empty
                else -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Upload",
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No image selected",)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = { galleryLauncher.launch("image/*") },
            ) {
                Text("Gallery")
            }

            Button(
                onClick = { cameraLauncher.launch() },
            ) {
                Text("Camera")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { /* TODO: Upload to Proxy Server */ },
            enabled = imageUri != null || imageBitmap != null
        ) {
            Text("Upload Timetable")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EducationDropDownMenu(
    selectedEdu: String,
    onEduSelected: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val taskTypes = arrayOf("primary", "secondary", "jc", "polytechnic", "university")

    ExposedDropdownMenuBox(
        expanded = isExpanded,
        onExpandedChange = { isExpanded = !isExpanded }
    ) {
        OutlinedTextField(
            value = selectedEdu.replaceFirstChar { it.uppercase() },
            onValueChange = {},
            readOnly = true,
            label = { Text("Task Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )

        ExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false }
        ) {
            taskTypes.forEach { item ->
                DropdownMenuItem(
                    text = { Text(text = item.replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        onEduSelected(item)
                        isExpanded = false
                    }
                )
            }
        }
    }
}