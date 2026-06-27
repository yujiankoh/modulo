package com.example.modulo.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AuthenticatePage(
    modifier: Modifier = Modifier,
    onSyncWithDriveClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Sync your app data to Google Drive")
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Terms and Conditions...")
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onSyncWithDriveClick() },
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Sync with Google Drive")
        }
    }
}