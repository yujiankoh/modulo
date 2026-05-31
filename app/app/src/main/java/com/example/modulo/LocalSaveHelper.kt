package com.example.modulo

import android.content.Context
import android.util.Log
import kotlinx.serialization.encodeToString
import java.io.File

class LocalSaveHelper(private val context: Context) {
    private val TAG = "Local Save"
    private val FILENAME = "modulo-data.json"

    fun saveData(appData: AppData) {
        try {
            val jsonString = syncJsonParser.encodeToString(appData)
            context.openFileOutput(FILENAME, Context.MODE_PRIVATE).use { output ->
                output.write(jsonString.toByteArray())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save locally")
        }
    }

    fun loadData() : AppData {
        return try {
            val file = File(context.filesDir, FILENAME)
            if (file.exists()) {
                val jsonString = file.readText()
                syncJsonParser.decodeFromString<AppData>(jsonString)
            } else {
                AppData()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load locally", e)
            AppData()
        }
    }
}