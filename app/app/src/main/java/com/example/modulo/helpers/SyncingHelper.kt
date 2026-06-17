package com.example.modulo.helpers

import android.accounts.Account
import android.content.Context
import android.util.Log
import com.example.modulo.AppData
import com.example.modulo.syncJsonParser
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.ByteArrayOutputStream
import kotlin.apply

private const val TAG = "DriveSync"
private const val FILENAME = "modulo-data.json"

class SyncingHelper(private val driveService: Drive) {
    companion object {
        fun getSyncService(context: Context, userEmail: String) : SyncingHelper {

            val credential = GoogleAccountCredential.usingOAuth2(
                context,
                listOf(DriveScopes.DRIVE_APPDATA)
            ).apply {
                selectedAccount = Account(userEmail, "com.google")
            }

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

    suspend fun uploadAppData(appData: AppData) = withContext(Dispatchers.IO) {
        try {
            // Search for the JSON file
            val fileList = driveService.files().list()
                .setSpaces("appDataFolder")
                .setQ("name='$FILENAME'")
                .execute()

            val jsonString = syncJsonParser.encodeToString(appData)

            val  content = ByteArrayContent.fromString("application/json", jsonString)

            if (fileList.files.isNotEmpty()) {
                // If file exist on the drive
                val fileId = fileList.files[0].id
                driveService.files().update(fileId, null, content).execute()
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

    suspend fun downloadAppData() : AppData? = withContext(Dispatchers.IO) {
        try {
            val fileList = driveService.files().list()
                .setSpaces("appDataFolder")
                .setQ("name='$FILENAME'")
                .setFields("files(id, name)")
                .execute()

            val files = fileList.files

            if (files.isNullOrEmpty()) {
                Log.d(TAG, "No saved data found in Google Drive.")
                return@withContext null
            }

            val fileId = files[0].id

            val outputStream = ByteArrayOutputStream()
            driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)

            val jsonString = outputStream.toString("UTF-8")
            Log.d(TAG, "Successfully downloaded JSON string from Drive")

            val downloadedData = syncJsonParser.decodeFromString<AppData>(jsonString)

            return@withContext downloadedData
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read from Google Drive", e)
            return@withContext null
        }
    }
}