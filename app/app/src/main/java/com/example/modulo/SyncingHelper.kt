package com.example.modulo

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncingHelper(private val driveService: Drive) {
    companion object {
        fun getSyncService(context: Context, userEmail: String) : SyncingHelper {
            val credential = GoogleAccountCredential.usingOAuth2(
                context,
                listOf(DriveScopes.DRIVE_APPDATA)
            )
            credential.selectedAccountName = userEmail

            val drive = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            )
                .setApplicationName("modulo")
                .build()

            return SyncingHelper(drive);
        }
    }

    suspend fun uploadAppData(content: String) = withContext(Dispatchers.IO) {

    }

    suspend fun downloadAppData() : String? = withContext(Dispatchers.IO) {
        return@withContext null
    }

}