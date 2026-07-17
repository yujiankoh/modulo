package com.example.modulo.helpers

import android.accounts.Account
import android.content.Context
import android.util.Log
import com.example.modulo.AppData
import com.example.modulo.syncJsonParser
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.json.GoogleJsonResponseException
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

private const val NOTE_KIND = "note"
private const val NOTE_QUERY = "appProperties has { key='moduloKind' and value='$NOTE_KIND' }"
private const val NOTE_FIELDS = "id, name, size, mimeType, appProperties, modifiedTime"

class DriveNoteException(message: String) : Exception(message)

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
    
    private fun File.toNote(): NotesHelper.Note {
        val props = appProperties ?: emptyMap()
        return NotesHelper.Note(
            id = id,
            name = name ?: "",
            size = getSize(),
            mimeType = mimeType ?: "",
            module = props["module"] ?: "",
            handbook = props["handbook"] ?: "",
            modifiedTime = modifiedTime?.toStringRfc3339() ?: ""
        )
    }

    private fun driveError(e: Exception): DriveNoteException {
        if (e is GoogleJsonResponseException) {
            val reason = e.details?.errors?.firstOrNull()?.reason ?: ""
            if (e.statusCode == 401) return DriveNoteException("Google sign-in expired — please reconnect and try again.")
            if (reason == "storageQuotaExceeded") return DriveNoteException("Your Google Drive is full — free up space or delete some notes.")
            return DriveNoteException("Google Drive request failed (${e.statusCode}). Please try again.")
        }
        return DriveNoteException(e.message ?: "Google Drive request failed. Please try again.")
    }

    suspend fun uploadNote(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        module: String,
        handbookId: String
    ): NotesHelper.Note = withContext(Dispatchers.IO) {
        try {
            val metadata = File().apply {
                name = fileName
                parents = listOf("appDataFolder")
                appProperties = mapOf(
                    "moduloKind" to NOTE_KIND,
                    "module" to module,
                    "handbook" to handbookId
                )
            }
            val content = ByteArrayContent(mimeType, bytes)
            return@withContext driveService.files().create(metadata, content)
                .setFields(NOTE_FIELDS)
                .execute()
                .toNote()
        } catch (e: Exception) {
            throw driveError(e)
        }
    }

    suspend fun listNotes(): List<NotesHelper.Note> = withContext(Dispatchers.IO) {
        try {
            val notes = mutableListOf<NotesHelper.Note>()
            var pageToken: String? = null
            do {
                val result = driveService.files().list()
                    .setSpaces("appDataFolder")
                    .setQ(NOTE_QUERY)
                    .setFields("nextPageToken, files($NOTE_FIELDS)")
                    .setPageSize(100)
                    .setPageToken(pageToken)
                    .execute()
                result.files?.forEach { notes.add(it.toNote()) }
                pageToken = result.nextPageToken
            } while (!pageToken.isNullOrEmpty())
            return@withContext notes
        } catch (e: Exception) {
            throw driveError(e)
        }
    }

    suspend fun downloadNote(id: String): ByteArray = withContext(Dispatchers.IO) {
        try {
            val outputStream = ByteArrayOutputStream()
            driveService.files().get(id).executeMediaAndDownloadTo(outputStream)
            return@withContext outputStream.toByteArray()
        } catch (e: Exception) {
            throw driveError(e)
        }
    }

    suspend fun renameNote(id: String, newName: String): NotesHelper.Note = withContext(Dispatchers.IO) {
        try {
            val metadata = File().apply { name = newName }
            return@withContext driveService.files().update(id, metadata, null)
                .setFields(NOTE_FIELDS)
                .execute()
                .toNote()
        } catch (e: Exception) {
            throw driveError(e)
        }
    }

    // A 404 is success: already deleted elsewhere reaches the same goal state.
    suspend fun deleteNote(id: String) = withContext(Dispatchers.IO) {
        try {
            driveService.files().delete(id).execute()
        } catch (e: GoogleJsonResponseException) {
            if (e.statusCode != 404) throw driveError(e)
        } catch (e: Exception) {
            throw driveError(e)
        }
    }
}