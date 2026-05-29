package com.example.modulo

import android.app.Activity
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.security.SecureRandom

object AuthenticationHelper {
    private val TAG = "GoogleDriveAuthorization"
    private val WEB_CLIENT_ID = "332114614658-87cqh1e2u8luh9b5q15sf22sb30i3nda.apps.googleusercontent.com"
    private val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

    /**
     * Main function to start the authentication of user and authorization of permission
     */
    fun authenticateThenAuthorize(
        activity: Activity,
        scope: CoroutineScope,
        credentialManager: CredentialManager,
        onLaunchIntent: (IntentSenderRequest) -> Unit,
        onSuccess: () -> Unit
    ) {
        scope.launch {
            try {
                // Attempt to log in for returning users
                val googleUser = try {
                    signIn(activity, credentialManager, true)
                } catch (e: NoCredentialException) {
                    signIn(activity, credentialManager, false)
                }

                Log.d(TAG, "Signed in: ${googleUser.email}")
                requestDriveAuthorization(activity, onLaunchIntent, onSuccess)

            } catch (e: GoogleIdTokenParsingException) {
                Log.e(TAG, "Invalid Google ID token", e)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected Error", e)
            }
        }
    }

    /**
     * Request Google Drive Authorization
     */
    fun requestDriveAuthorization(
        activity: Activity,
        onLaunchIntent: (IntentSenderRequest) -> Unit,
        onSuccess: () -> Unit
    ) {
        val requestedScopes: List<Scope> = listOf(Scope(DRIVE_SCOPE))
        val authorizationRequest = AuthorizationRequest.builder()
            .setRequestedScopes(requestedScopes)
            .build()

        Identity.getAuthorizationClient(activity)
            .authorize(authorizationRequest)
            .addOnSuccessListener { authorizationResult ->
                if (authorizationResult.hasResolution()) {
                    val pendingIntent = authorizationResult.pendingIntent

                    // Pass intent back to MainActivity to ask user for access
                    if (pendingIntent != null) {
                        onLaunchIntent(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                    }
                } else {
                    // Access was previously granted
                    handleDriveAuthorization(activity, authorizationResult, onSuccess)
                }
            }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to authorize Drive", e) }
    }

    /**
     * Implement Sign in with Google
     */
    private suspend fun signIn(
        activity: Activity,
        credentialManager: CredentialManager,
        filterByAuthorizedAccounts: Boolean
    ) : GoogleIdTokenCredential {

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
            .setServerClientId(WEB_CLIENT_ID)
            .setAutoSelectEnabled(filterByAuthorizedAccounts)
            .setNonce(generateSecureRandomNonce())
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = credentialManager.getCredential(context = activity, request = request)
        val credential = result.credential

        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return GoogleIdTokenCredential.createFrom(credential.data)
        }

        throw IllegalStateException("Credential is not a Google ID token")
    }

    /**
     * Verifies the final token
     */
    fun handleDriveAuthorization(
        activity: Activity,
        authorizationResult: AuthorizationResult,
        onSuccess: () -> Unit
    ) {
        val grantedScopes = authorizationResult.grantedScopes

        if (!grantedScopes.contains(DRIVE_SCOPE)) {
            Toast.makeText(
                activity,
                "Google Drive Permission was not granted",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        Log.d(TAG, "SUCCESS! Google Drive AppData access token granted.")
        onSuccess()
        // TODO: do something with access token
    }

    /**
     * Generate a nonce for server-side verification
     */
    fun generateSecureRandomNonce(byteLength: Int = 32): String {
        val randomBytes = ByteArray(byteLength)
        SecureRandom().nextBytes(randomBytes)
        return Base64.encodeToString(randomBytes, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }

    /**
     * Processes the result after user replies the Authorization popup
     */
    fun onDrivePermission(
        activity: Activity,
        result: ActivityResult,
        onSuccess: () -> Unit
    ) {
        // If user denies Google Drive Authorization
        if (result.resultCode != Activity.RESULT_OK) {
            Toast.makeText(
                activity,
                "Google Drive permission denied",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val data = result.data
        if (data == null) {
            Toast.makeText(
                activity,
                "Missing Google Drive authorization",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        try {
            val authorizationResult = Identity
                .getAuthorizationClient(activity)
                .getAuthorizationResultFromIntent(data)

            handleDriveAuthorization(activity, authorizationResult, onSuccess)
        } catch (e: ApiException) {
            Log.e(TAG, "Google Drive authorization failed", e)
            Toast.makeText(
                activity,
                "Google Drive authorization failed",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}