package com.example.modulo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomePage(
    isDriveSyncEnabled: Boolean,
    onCounterIncrease: (Int) -> Unit
) {
    var counter by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("HomePage")

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Drive Sync is ${if (isDriveSyncEnabled) "ON" else "OFF"}")

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Count: $counter")

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                counter++
                onCounterIncrease(counter)
            }
        ) {
            Text("Increase Counter")
        }
    }
}