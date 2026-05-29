package com.example.modulo

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

@Serializable
object SignIn
@Serializable
object Authenticate
@Serializable
data class Home(val isDriveSyncEnabled: Boolean)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = SignIn
    ) {
        composable<SignIn> {
            SignInPage(
                onSyncWithDriveClick = { navController.navigate(Authenticate) },
                onLocalSaveClick = {
                    navController.navigate(Home(false)) {
                        popUpTo(SignIn) { inclusive = true }
                    }
                }
            )
        }

        composable<Authenticate> {
            AuthenticatePage(
                onSyncWithDriveClick = {
                    navController.navigate(Home(true)) {
                        popUpTo(SignIn) { inclusive = true }
                    }
                },
            )
        }

        composable<Home> { backStackEntry ->
            val homeArgs = backStackEntry.toRoute<Home>()

            HomePage(
                isDriveSyncEnabled = homeArgs.isDriveSyncEnabled,
                onCounterIncrease = { newCount ->
                    println("Counter is now $newCount. Sync Enabled: ${homeArgs.isDriveSyncEnabled}")
                }
            )
        }
    }
}