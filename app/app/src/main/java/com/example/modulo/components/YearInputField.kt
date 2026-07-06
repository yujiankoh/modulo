package com.example.modulo.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun YearInputField(
    label: String,
    selectedYear: String,
    onYearSelected: (String) -> Unit,
    modifier : Modifier = Modifier
) {
    val isError = selectedYear.isNotEmpty() && selectedYear.length < 4

    OutlinedTextField(
        value = selectedYear,
        onValueChange = { newText ->
            if (newText.all { it.isDigit() } && newText.length <= 4) {
                onYearSelected(newText)
            }
        },
        label = { Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        placeholder = { Text("YYYY") },
        singleLine = true,

        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),

        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),

        isError = isError,
    )
}