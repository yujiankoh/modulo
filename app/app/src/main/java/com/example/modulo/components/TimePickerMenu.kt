package com.example.modulo.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.modulo.ui.theme.ModuloTheme
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerMenu(
    label: String,
    selectedTime: String,
    onTimeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    // Parse the existing time string
    val parts = selectedTime.split(":")
    val initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 12
    val initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0

    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )

    Box(modifier = modifier.clickable { showDialog = true }) {
        OutlinedTextField(
            value = selectedTime,
            onValueChange = {},
            readOnly = true,
            enabled = false, // Prevents keyboard from appearing
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            // Override disabled colors
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        )
    }

    if (showDialog) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = { showDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        val rawHour = timePickerState.hour
                        val rawMinute = timePickerState.minute

                        // Round to the nearest 15 minutes
                        var roundedMinute = ((rawMinute / 15.0).roundToInt() * 15)
                        var finalHour = rawHour

                        if (roundedMinute == 60) {
                            roundedMinute = 0
                            finalHour = (finalHour + 1) % 24 // Keeps it within 0-23
                        }

                        val h = finalHour.toString().padStart(2, '0')
                        val m = roundedMinute.toString().padStart(2, '0')

                        onTimeSelected("$h:$m")
                        showDialog = false
                    },
                    modifier = Modifier.padding(end = 8.dp, bottom = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Override")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            },
            text = {
                TimePicker(
                    state = timePickerState,
                    colors = TimePickerDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        clockDialColor = ModuloTheme.colors.pillBg,
                        timeSelectorUnselectedContainerColor = ModuloTheme.colors.pillBg
                    )
                )
            }
        )
    }
}