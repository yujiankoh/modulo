package com.example.modulo.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import java.nio.file.WatchEvent

@Composable
fun NavigationBottomBar(
    navController: NavHostController,
    content: @Composable (PaddingValues) -> Unit
) {
    // Track the current route to determine if we should show the bottom bar
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination?.route

    // Hide bottom bar on SignIn and Authenticate screens
    val showBottomBar = currentDestination != null &&
            !currentDestination.contains("SignIn") &&
            !currentDestination.contains("Authenticate")

    // Track if the Add Task screen is active
    var isAddTaskActive by remember { mutableStateOf(false) }

    // Reset Add Task animation if user pressed device back button
    LaunchedEffect(currentDestination) {
        if (currentDestination != null && !currentDestination.contains("AddTask")) {
            isAddTaskActive = false
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    NavigationBar {
                        // Home
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                            label = { Text("Home") },
                            selected = false,
                            onClick = { navController.navigate(Home) }
                        )

                        // Calender
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.DateRange, contentDescription = "Calendar") },
                            label = { Text("Calendar") },
                            selected = false,
                            onClick = { navController.navigate(Calendar) }
                        )

                        // Spacer for Add Task Button
                        NavigationBarItem(
                            icon = { },
                            label = { },
                            selected = false,
                            onClick = { },
                            enabled = false
                        )

                        // All Task
                        NavigationBarItem(
                            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "All Tasks") },
                            label = { Text("Tasks") },
                            selected = false,
                            onClick = { navController.navigate(AllTasks) }
                        )

                        // Study Session
                        NavigationBarItem(
                            icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Study") },
                            label = { Text("Study") },
                            selected = false,
                            onClick = { navController.navigate(StudySession) }
                        )
                    }

                    // Add Task Button
                    val rotation by animateFloatAsState(
                        targetValue = if (isAddTaskActive) 45f else 0f,
                        label = "RotatePlus"
                    )
                    val buttonColor by animateColorAsState(
                        targetValue = if (isAddTaskActive) Color.Red else LocalContentColor.current,
                        label = "ColorPlus"
                    )

                    FloatingActionButton(
                        onClick = {
                            isAddTaskActive = !isAddTaskActive

                            if (isAddTaskActive) {
                                navController.navigate(AddTask)
                            } else {
                                navController.popBackStack()
                            }
                        },
                        shape = CircleShape,
                        containerColor = buttonColor,
                        contentColor = Color.White,
                        modifier = Modifier
                            .size(72.dp)
                            .offset(y = (-36).dp)
                            .align(Alignment.TopCenter)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Task",
                            modifier = Modifier.rotate(rotation)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        content(innerPadding)
    }
}

