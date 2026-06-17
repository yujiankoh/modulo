package com.example.modulo.helpers

import android.util.Log
import com.example.modulo.ParsingData
import com.example.modulo.Timetable
import com.example.modulo.syncJsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "ParsingHelper"
private const val PROXY_URL = "https://modulo-proxy.onrender.com/parse-timetable"

class ParsingHelper {
    suspend fun parseTimetable(parsingData: ParsingData): Timetable? = withContext(Dispatchers.IO) {
        try {
            val url = URL(PROXY_URL)
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true

            val jsonString = syncJsonParser.encodeToString(parsingData)

            OutputStreamWriter(connection.outputStream).use { os ->
                os.write(jsonString)
                os.flush()
            }

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                return@withContext syncJsonParser.decodeFromString<Timetable>(jsonString)
            } else {
                val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "Server Error: $responseCode - $errorResponse")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network Error", e)
            return@withContext null
        }
    }
}