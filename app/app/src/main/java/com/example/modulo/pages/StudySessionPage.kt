package com.example.modulo.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.modulo.AppViewModel
import com.example.modulo.R
import com.example.modulo.components.CitySchemeStore
import com.example.modulo.components.StudyCityView
import com.example.modulo.components.cityScheme
import com.example.modulo.components.nextCityScheme
import com.example.modulo.emojis
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
fun StudySessionPage(
    viewModel: AppViewModel,
    onOpenHistory: () -> Unit
) {
    val appData by viewModel.appData.collectAsState()

    var showConfirmDialog by remember { mutableStateOf(false) }

    val (todayMins, weekMins, totalMins) = remember(appData.studySessions) {
        val sessions = appData.studySessions
        val now = Instant.now().atZone(ZoneId.systemDefault())
        val startOfDay = now.truncatedTo(ChronoUnit.DAYS).toInstant()
        val startOfWeek = now.with(java.time.DayOfWeek.MONDAY).truncatedTo(ChronoUnit.DAYS).toInstant()

        var tMins = 0
        var wMins = 0
        var allMins = 0

        sessions.forEach { session ->
            allMins += session.durationMins
            try {
                val sessionStart = Instant.parse(session.start)
                if (!sessionStart.isBefore(startOfDay)) tMins += session.durationMins
                if (!sessionStart.isBefore(startOfWeek)) wMins += session.durationMins
            } catch (e: Exception) { /* Ignore invalid dates */ }
        }
        Triple(tMins, wMins, allMins)
    }

    val elapsedSeconds = viewModel.elapsedSeconds
    val hours = elapsedSeconds / 3600
    val minutes = (elapsedSeconds % 3600) / 60
    val seconds = elapsedSeconds % 60
    val timerString = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)

    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    var schemeKey by remember { mutableStateOf(CitySchemeStore.get(context)) }
    val scheme = cityScheme(schemeKey)
    val seaColor = if (dark) scheme.night.sea else scheme.day.sea
    val backgroundBrush = Brush.verticalGradient(
        colorStops = arrayOf(
            0f to MaterialTheme.colorScheme.background, // soft fade from the app background…
            0.35f to seaColor,                          // …into the sea, which then fills to the bottom
            1f to seaColor
        )
    )

    Scaffold(containerColor = Color.Transparent) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().background(backgroundBrush)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Study Numbers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            // Stats Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                StatCard(title = "Today", mins = todayMins, shape = groupedRowShape(0, 3), modifier = Modifier.weight(1f))
                StatCard(title = "This Week", mins = weekMins, shape = groupedRowShape(1, 3), modifier = Modifier.weight(1f))
                StatCard(title = "Overall", mins = totalMins, shape = groupedRowShape(2, 3), modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onOpenHistory,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(painter = painterResource(R.drawable.file_clock), contentDescription = "History")
                Spacer(modifier = Modifier.padding(6.dp))
                Text("View History")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Game View
            StudyCityView(
                city = appData.city,
                totalMins = totalMins,
                scheme = scheme,
                onCycleScheme = {
                    schemeKey = nextCityScheme(schemeKey)
                    CitySchemeStore.set(context, schemeKey)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            // Timer
            Text(
                text = timerString,
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!viewModel.isTimerRunning) {
                    IconButton(
                        onClick = { viewModel.startOrResumeTimer() },
                        modifier = Modifier.size(60.dp),
                        shape = RoundedCornerShape(30.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(painter = painterResource(R.drawable.play), contentDescription = "Start")
                    }
                } else {
                    IconButton(
                        onClick = { viewModel.pauseTimer() },
                        modifier = Modifier.size(60.dp),
                        shape = RoundedCornerShape(30.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(painter = painterResource(R.drawable.pause), contentDescription = "Start")
                    }
                }

                if (viewModel.hasSessionStarted) {
                    IconButton(
                        onClick = {
                            viewModel.stopTimer()
                            showConfirmDialog = true
                        },
                        modifier = Modifier.size(60.dp),
                        shape = RoundedCornerShape(30.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(painter = painterResource(R.drawable.square), contentDescription = "Stop")
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
        }

        if (showConfirmDialog) {
            var rating by remember { mutableIntStateOf(3) }

            AlertDialog(
                onDismissRequest = {},
                properties = DialogProperties(
                    dismissOnClickOutside = false,
                    dismissOnBackPress = false
                ),
                title = { Text("Session Ended") },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("How productive was this session?")
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.Center) {
                            for (i in 1..5) {
                                val isSelected = (i == rating)

                                IconButton(
                                    onClick = { rating = i },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Text(
                                        text = emojis[i - 1],
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.saveSession(rating)
                            showConfirmDialog = false
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Session")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.discardSession()
                        showConfirmDialog = false
                    }) {
                        Text("Discard", color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    }
}

fun groupedRowShape(index: Int, size: Int): Shape {
    val large = 20.dp
    val small = 8.dp
    val start = if (index == 0) large else small
    val end = if (index == size - 1) large else small
    return RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end)
}

@Composable
fun StatCard(
    title: String,
    mins: Int,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp)
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = shape,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text("$mins min", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}