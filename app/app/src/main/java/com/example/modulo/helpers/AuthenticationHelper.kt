package com.example.modulo.helpers

import android.app.Activity
import android.content.Context
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
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
    private const val TAG = "DriveAuthorization"
    private const val WEB_CLIENT_ID = "332114614658-87cqh1e2u8luh9b5q15sf22sb30i3nda.apps.googleusercontent.com"
    private const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

    /**
     * Main function to start the authentication of user and authorization of permission
     */
    fun authenticateThenAuthorize(
        activity: Activity,
        scope: CoroutineScope,
        credentialManager: CredentialManager,
        onLaunchIntent: (IntentSenderRequest, String) -> Unit,
        onSuccess: (String) -> Unit
    ) {
        scope.launch {
            try {
                // Attempt to log in for returning users
                val googleUser = try {
                    signIn(activity, credentialManager, true)
                } catch (e: NoCredentialException) {
                    signIn(activity, credentialManager, false)
                }

                val userEmail = googleUser.id
                Log.d(TAG, "Signed in: $userEmail")
                requestDriveAuthorization(activity, userEmail, onLaunchIntent, onSuccess)

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
        email: String,
        onLaunchIntent: (IntentSenderRequest, String) -> Unit,
        onSuccess: (String) -> Unit
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
                        onLaunchIntent(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                            email
                        )
                    }
                } else {
                    // Access was previously granted
                    handleDriveAuthorization(activity, email, authorizationResult, onSuccess)
                }
            }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to authorize Drive", e) }
    }

    /**
     * Implement Sign in with Google
     */
    private suspend fun signIn(
        context: Context,
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

        val result = credentialManager.getCredential(context = context, request = request)
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
        email: String,
        authorizationResult: AuthorizationResult,
        onSuccess: (String) -> Unit
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
        onSuccess(email)
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
        email: String,
        onSuccess: (String) -> Unit
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

            handleDriveAuthorization(activity, email, authorizationResult, onSuccess)
        } catch (e: ApiException) {
            Log.e(TAG, "Google Drive authorization failed", e)
            Toast.makeText(
                activity,
                "Google Drive authorization failed",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Silently attempt to sign in
     */
    suspend fun silentSignIn(
        context: Context,
        credentialManager: CredentialManager,
        onSuccess: (String) -> Unit,
        onFailure: () -> Unit
    ) {
        try {
            // Select accounts that have already logged in
            val googleUser = signIn(context, credentialManager, true)
            onSuccess(googleUser.id)

        } catch (e: GetCredentialException) {
            // No saved session found
            Log.d(TAG, "Silent sign-in failed")
            onFailure()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during silent sign-in", e)
            onFailure()
        }
    }

    suspend fun signOut(context: Context) {
        try {
            val credentialManager = CredentialManager.create(context)
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.e(TAG, "Sign out failed", e)
        }
    }
}