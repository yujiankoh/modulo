package com.example.modulo

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.example.modulo.pages.AuthenticatePage
import com.example.modulo.pages.HomePage
import com.example.modulo.pages.SignInPage
import kotlinx.serialization.Serializable

@Serializable
object SignIn
@Serializable
object Authenticate
@Serializable
data class Home(val isDriveSyncEnabled: Boolean)

@Composable
fun AppNavigation(
    navController: NavHostController,
    appViewModel: AppViewModel,
    onAuthentication: () -> Unit
) {
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
                onSyncWithDriveClick = onAuthentication
            )
        }

        composable<Home> { backStackEntry ->
            val homeArgs = backStackEntry.toRoute<Home>()

            HomePage(
                isDriveSyncEnabled = homeArgs.isDriveSyncEnabled,
                viewModel = appViewModel
            )
        }
    }
}