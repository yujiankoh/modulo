package com.example.modulo.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.modulo.R

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

    val navColors = NavigationBarItemDefaults.colors(
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    NavigationBar (
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        // Home
                        NavigationBarItem(
                            icon = { Icon(painter = painterResource(R.drawable.dashboard), contentDescription = "Home") },
                            label = { Text("Dashboard") },
                            selected = currentDestination.contains("Home"),
                            onClick = { navController.navigateBottom(Home) },
                            colors = navColors
                        )

                        // Calender
                        NavigationBarItem(
                            icon = { Icon(painter = painterResource(R.drawable.calendar), contentDescription = "Calendar") },
                            label = { Text("Calendar") },
                            selected = currentDestination.contains("Calendar"),
                            onClick = { navController.navigateBottom(Calendar) },
                            colors = navColors
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
                            icon = { Icon(painter = painterResource(R.drawable.list_checks), contentDescription = "All Tasks") },
                            label = { Text("All Tasks") },
                            selected = currentDestination.contains("AllTasks"),
                            onClick = { navController.navigateBottom(AllTasks) },
                            colors = navColors
                        )

                        // Study Session
                        NavigationBarItem(
                            icon = { Icon(painter = painterResource(R.drawable.timer), contentDescription = "Study") },
                            label = { Text("Study") },
                            selected = currentDestination.contains("StudySession"),
                            onClick = { navController.navigateBottom(StudySession) },
                            colors = navColors
                        )
                    }

                    // Add Task Button
                    val rotation by animateFloatAsState(
                        targetValue = if (isAddTaskActive) 45f else 0f,
                        label = "RotatePlus"
                    )
                    val buttonColor by animateColorAsState(
                        targetValue = if (isAddTaskActive) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
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
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(72.dp)
                            .offset(y = (-36).dp)
                            .align(Alignment.TopCenter)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.plus),
                            contentDescription = "Add Task",
                            modifier = Modifier.rotate(rotation).size(32.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        content(innerPadding)
    }
}

fun NavHostController.navigateBottom(route: Any) {
    navigate(route) {
        // Pop up to the start destination to prevent infinite stacking
        popUpTo(graph.startDestinationId) {
            saveState = true
        }
        // Avoid multiple copies of the same destination
        launchSingleTop = true
        // Restore previous state (like scroll position)
        restoreState = true
    }
}

