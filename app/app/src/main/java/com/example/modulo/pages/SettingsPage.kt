package com.example.modulo.pages

import android.app.AlertDialog
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.modulo.AppViewModel
import com.example.modulo.R
import java.time.YearMonth

@Composable
fun SettingsPage(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onNavigateToTimetable: () -> Unit,
) {
    val userEmail = viewModel.getUserEmail()
    val isSignedIn = userEmail.isNotBlank()
    val context = LocalContext.current

    var showSignOutWarning by remember { mutableStateOf(false) }

    Scaffold { paddingValues ->
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_left),
                        contentDescription = "Go Back"
                    )
                }

                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            SettingsRow(
                icon = R.drawable.circle_user_round,
                title = "Google Account",
                subTitle = if (isSignedIn) userEmail else "Sign in to sync across device?",
                subTitleColor = MaterialTheme.colorScheme.primary,
                isClickable = !isSignedIn,
                onClick = { viewModel.reAuthenticate() }
            )

            SettingsRow(
                icon = R.drawable.calendar_clock,
                title = "Timetable",
                isClickable = true,
                onClick = {}
            )

            if (isSignedIn) {
                SettingsRow(
                    icon = R.drawable.log_out,
                    iconTint = MaterialTheme.colorScheme.error,
                    title = "Sign Out",
                    titleColor = MaterialTheme.colorScheme.error,
                    isClickable = true,
                    onClick = { showSignOutWarning = true }
                )
            }


            // Add Delete Information Button
        }
    }

    if (showSignOutWarning) {
        WarningCard(
            title = "Sign Out",
            text = "Are you sure you want to sign out? Your local data will no longer be synced across devices.",
            confirmText = "Sign Out",
            onConfirm = {
                showSignOutWarning = false
                viewModel.signOut(context)
                viewModel.saveSyncPreference(false)
                onBack()
            },
            onDismiss = { showSignOutWarning = false }
        )
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun SettingsRow(
    @DrawableRes icon: Int,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    title: String,
    titleColor: Color = Color.Unspecified,
    subTitle: String? = null,
    subTitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    isClickable: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clickable (enabled = isClickable) { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(icon),
                contentDescription = "Icon",
                tint = iconTint
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )

                if (!subTitle.isNullOrBlank()) {
                    Text(
                        text = subTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = subTitleColor
                    )
                }
            }
        }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
fun WarningCard(
    title: String,
    text: String,
    confirmText: String,
    dismissText: String = "Cancel",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(text) },
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(text = confirmText, color = MaterialTheme.colorScheme.onError)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(dismissText)
            }
        }
    )
}