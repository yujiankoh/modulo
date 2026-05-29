package com.example.modulo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.lifecycleScope
import com.example.modulo.ui.theme.HomePage
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
        HomePage()
    }
}