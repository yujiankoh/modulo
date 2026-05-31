package com.example.modulo

import android.content.Context
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.apply

class SyncingHelper(private val driveService: Drive) {
    private val TAG = "DriveSync"
    private val FILENAME = "modulo-data.json"

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

            return SyncingHelper(drive)
        }
    }

    suspend fun uploadAppData(jsonString: String) = withContext(Dispatchers.IO) {
        try {
            // Search for the JSON file
            val fileList = driveService.files().list()
                .setSpaces("appDataFolder")
                .setQ("name='$FILENAME'")
                .execute()

            val  content = ByteArrayContent.fromString("application/json", jsonString)

            if (fileList.files.isNotEmpty()) {
                // If file exist on the drive
                val existingFileId = fileList.files[0].id
                driveService.files().update(existingFileId, null, content).execute()
                Log.d(TAG, "Successfully updated existing $FILENAME")
            } else {
                val fileMetadata = File().apply {
                    name = FILENAME
                    parents = listOf("appDataFolder")
                }

                driveService.files().create(fileMetadata, content).execute()
                Log.d(TAG, "Successfully created new $FILENAME")
            }

            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload to Google Drive: ${e.message}")
            e.printStackTrace()
            return@withContext false
        }
    }

    suspend fun downloadAppData() : String? = withContext(Dispatchers.IO) {
        return@withContext null
    }

}