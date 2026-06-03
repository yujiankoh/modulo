package com.example.modulo.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import com.example.modulo.AppViewModel
import com.example.modulo.pages.AuthenticatePage
import com.example.modulo.pages.HomePage
import com.example.modulo.pages.SignInPage
import kotlinx.serialization.Serializable

@Serializable
object SignIn
@Serializable
object Authenticate
@Serializable
object Home
@Serializable
object AddTask

@Serializable
object Calendar
@Serializable
object AllTasks
@Serializable
object StudySession

@Composable
fun AppNavigation(
    navController: NavHostController,
    appViewModel: AppViewModel,
    onAuthentication: () -> Unit
) {
    NavigationBottomBar(
        navController = navController
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = SignIn,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable<SignIn> {
                SignInPage(
                    onSyncWithDriveClick = { navController.navigate(Authenticate) },
                    onLocalSaveClick = {
                        appViewModel.setDriveSyncEnabled(false)
                        navController.navigate(Home) {
                            popUpTo(SignIn) { inclusive = true }
                        }
                    }
                )
            }

            composable<Authenticate> {
                AuthenticatePage(
                    onSyncWithDriveClick = {
                        appViewModel.setDriveSyncEnabled(true)
                        onAuthentication()
                    }
                )
            }

            composable<Home> {
                HomePage(
                    viewModel = appViewModel
                )
            }

            composable<AddTask> { Text("Add Task Page") }
            composable<Calendar> { Text("Calendar Page") }
            composable<AllTasks> { Text("All Tasks Page") }
            composable<StudySession> { Text("Study Session Page") }
        }

    }
}