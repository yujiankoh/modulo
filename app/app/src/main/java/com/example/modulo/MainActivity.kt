package com.example.modulo

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.credentials.CredentialManager
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.example.modulo.ui.theme.ModuloTheme
import kotlin.contracts.contract

class MainActivity : ComponentActivity() {
    private lateinit var credentialManager: CredentialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        credentialManager = CredentialManager.create(this)

        setContent {
            ModuloTheme {

                val navController = rememberNavController()
                val scope = rememberCoroutineScope()

                // Action for when drive authorization is successful
                val navigateToHomeAfterSync = {
                    navController.navigate(Home(true)) {
                        popUpTo(SignIn) { inclusive = true }
                    }
                }

                // Waits for user reply on authorization and passes result
                val driveAuthorizationLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartIntentSenderForResult()
                ) { result ->
                    AuthenticationHelper.onDrivePermission(this, result, navigateToHomeAfterSync)
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        navController = navController,
                        onAuthentication = {
                            AuthenticationHelper.authenticateThenAuthorize(
                                activity = this@MainActivity,
                                scope = scope,
                                credentialManager = credentialManager,
                                onLaunchIntent = { intentRequest ->
                                    driveAuthorizationLauncher.launch(intentRequest)
                                },
                                onSuccess = navigateToHomeAfterSync
                            )
                        }
                    )
                }
            }
        }
    }
}