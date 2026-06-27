package com.example.modulo.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.modulo.R

@Composable
fun SignInPage(
    onSyncWithDriveClick: () -> Unit,
    onLocalSaveClick:() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.logo),
            contentDescription = "logo"
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onSyncWithDriveClick,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Sync with Google Drive")
        }
        Button(
            onClick = onLocalSaveClick,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Continue with Local Save")
        }
    }
}