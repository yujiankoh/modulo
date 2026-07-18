package com.example.modulo.pages

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.modulo.AppViewModel
import com.example.modulo.R
import com.example.modulo.SyncConflict
import com.example.modulo.SyncSummary
import com.example.modulo.components.WarningCard
import com.example.modulo.helpers.StudyStatsHelper
import com.example.modulo.ui.theme.ModuloTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SettingsPage(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onNavigateToTimetable: () -> Unit,
    onNavigateToHandbookCreate: () -> Unit,
    onNavigateToHandbook: () -> Unit,
    onNavigateToGPA: () -> Unit,
    onNavigateToNotes: () -> Unit,
) {
    val userEmail = viewModel.getUserEmail()
    val isSignedIn = userEmail.isNotBlank()
    val userPhotoUrl by viewModel.userPhotoUrl.collectAsState()
    val isDark = ModuloTheme.isDark
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
                    Icon(painter = painterResource(R.drawable.arrow_left), contentDescription = "Go Back")
                }

                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            SettingsSectionTitle("Account")

            SettingsRow(
                icon = R.drawable.circle_user_round,
                title = "Google Account",
                subTitle = if (isSignedIn) userEmail else "Sign in to sync across device?",
                subTitleColor = MaterialTheme.colorScheme.primary,
                isClickable = !isSignedIn,
                onClick = { viewModel.reAuthenticate() },
                leadingImageUrl = if (isSignedIn) userPhotoUrl else null
            )

            SettingsSectionTitle("Settings")

            SettingsRow(
                icon = R.drawable.sun_moon,
                title = if (isDark) "Dark Mode" else "Light Mode",
                isClickable = true,
                onClick = { viewModel.setDarkMode(!isDark) },
                trailing = {
                    Switch(
                        checked = isDark,
                        onCheckedChange = { viewModel.setDarkMode(it) },
                        thumbContent = {
                            Icon(
                                painter = painterResource(if (isDark) R.drawable.moon else R.drawable.sun),
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize)
                            )
                        },
                        colors = SwitchDefaults.colors(
                            uncheckedThumbColor = MaterialTheme.colorScheme.primary,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surface,
                            uncheckedBorderColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            )

            SettingsSectionTitle("More Tools")

            SettingsRow(
                icon = R.drawable.calendar_clock,
                title = "Timetable",
                isClickable = true,
                onClick = onNavigateToTimetable
            )

            if (viewModel.isHigherEducation()) {
                SettingsRow(
                    icon = R.drawable.graduation_cap,
                    title = "Grades",
                    isClickable = true,
                    onClick = onNavigateToGPA
                )
            }

            SettingsRow(
                icon = R.drawable.file_text,
                title = "Notes",
                isClickable = true,
                onClick = onNavigateToNotes
            )

            SettingsRow(
                icon = R.drawable.notebook_pen,
                title = "New Handbook",
                isClickable = true,
                onClick = onNavigateToHandbookCreate
            )

            SettingsRow(
                icon = R.drawable.notebook,
                title = "Old Handbooks",
                isClickable = true,
                onClick = onNavigateToHandbook
            )

            SettingsSectionTitle("Danger Zone", color = MaterialTheme.colorScheme.error)

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
fun SettingsSectionTitle(title: String, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = color,
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
    onClick: () -> Unit,
    leadingImageUrl: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clickable (enabled = isClickable) { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingImageUrl != null) {
                AsyncImage(
                    model = leadingImageUrl,
                    contentDescription = "Icon",
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(icon),
                    error = painterResource(icon),
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = "Icon",
                    tint = iconTint
                )
            }

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

        if (trailing != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailing()
        }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

// Ask user to select one data
@Composable
fun SyncConflictDialog(
    conflict: SyncConflict,
    onKeepLocal: () -> Unit,
    onKeepDrive: () -> Unit,
    onCancel: () -> Unit
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Choose which data to keep",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "This device and Google Drive both have data. Pick which to keep, the other will be replaced.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ModuloTheme.colors.subText
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SyncSummaryColumn("This Device", conflict.local, Modifier.weight(1f))
                    SyncSummaryColumn("Google Drive", conflict.cloud, Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onKeepDrive,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Keep Google Drive")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onKeepLocal,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Keep Device")
                }
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun SyncSummaryColumn(title: String, summary: SyncSummary, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = formatSavedTime(summary.lastSaved),
                style = MaterialTheme.typography.labelSmall,
                color = ModuloTheme.colors.subText
            )
            Spacer(modifier = Modifier.height(8.dp))

            SyncSummaryRow("Handbooks", summary.handbooks.toString())
            SyncSummaryRow("Study", StudyStatsHelper.formatHours(summary.studyMinutes))

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Text("Current handbook", style = MaterialTheme.typography.labelSmall, color = ModuloTheme.colors.subText)
            Text(
                text = summary.currentHandbook,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            SyncSummaryRow("Tasks", summary.tasks.toString())
            SyncSummaryRow("Modules", summary.modules.toString())
        }
    }
}

// "19 Jul 2026, 14:30" in the device's local time, or "unknown" if never saved / unparseable.
private fun formatSavedTime(iso: String?): String {
    if (iso.isNullOrBlank()) return "unknown"
    return try {
        Instant.parse(iso)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.getDefault()))
    } catch (e: Exception) {
        "unknown"
    }
}

@Composable
private fun SyncSummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = ModuloTheme.colors.subText)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

