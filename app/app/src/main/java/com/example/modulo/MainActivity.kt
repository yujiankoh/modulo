package com.example.modulo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.credentials.CredentialManager
import androidx.lifecycle.lifecycleScope
import com.example.modulo.ui.theme.ModuloTheme

class MainActivity : ComponentActivity() {
    private lateinit var credentialManager: CredentialManager
    private var driveAuthorizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) {
        result -> AuthenticationHelper.onDrivePermission(this, result);
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        credentialManager = CredentialManager.create(this)

        setContent {
            ModuloTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Preview(
    showBackground = true,
    showSystemUi = false
)
@Composable
fun LayoutPreview() {
    ModuloTheme {
        HomePage(
            false,
            { newCount ->
                println("Preview Action: Counter increased to $newCount (Sync Offline)") }
        )
    }
}

/*
AuthenticatePage(
                        onSyncWithDriveClick = {
                            AuthenticationHelper.authenticateThenAuthorize(
                                activity = this@MainActivity,
                                scope = lifecycleScope,
                                credentialManager = credentialManager,
                                onLaunchIntent = { intentRequest ->
                                    driveAuthorizationLauncher.launch(intentRequest)
                                }
                            )
                        }
                    )
 */