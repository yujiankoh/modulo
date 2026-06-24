package com.example.modulo

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs

/**
 * This enum class encapsulates the states of syncing
 */
enum class SyncState{
    OFFLINE, UNSYNCED, SYNCING, SYNCED
}

/**
 * This enum class encapsulates the states of app startup
 */
enum class StartupState{
    LOADING, TUTORIAL, SIGN_IN, AUTHENTICATE, READY
}

/**
 * This enum class encapsulates the sorting option of tasks
 */
enum class SortOption(val displayName: String) {
    DUE_DATE("Due Date"),
    MODULE_CODE("Module Code"),
    TYPE("Task Type")
}

enum class EducationLevel(val json: String, val displayName: String) {
    PRIMARY("primary", "Primary"),
    SECONDARY("secondary", "Secondary"),
    JC("jc", "JC"),
    POLY("poly", "Polytechnic"),
    UNIVERSITY("university", "University");
}

/**
 * This data class encapsulates the information of a task
 */
@Serializable
data class Task(
    val id: Long,
    val module: String = "",
    val title: String,
    val due: String = "",
    val type: String,
    val done: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

/**
 * This data class encapsulates the information of a timetable
 */
@Serializable
data class Timetable(
    val educationLevel: String,
    val modules: List<Module> = emptyList()
)

/**
 * This data class encapsulates the information of a module
 */
@Serializable
data class Module(
    val code: String,
    val name: String,
    val slots: List<Slot> = emptyList()
)

/**
 * This data class encapsulates the information of a module slot in the timetable
 */
@Serializable
data class Slot(
    val day: String,
    val start: String,
    val end: String,
    val location: String = "",
    val sessionType: String = "",
    val classNo: String = "",
    val week: String = "all"
)

/**
 * This data class encapsulates all the information that needs to be saved
 */
@Serializable
data class AppData(
    val schemaVersion: Int = 2,
    val educationLevel: String? = null,
    val updatedAt: String? = null,
    val tasks: List<Task> = emptyList(),
    val timetable: Timetable? = null
)
/**
 * This data class encapsulates all the timetable information to send to proxy
 */
@Serializable
data class ParsingData(
    val image: String,
    val mimeType: String,
    val educationLevel: String
)

@Serializable
val syncJsonParser = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

sealed interface TimetableState {
    object Idle : TimetableState
    object Processing : TimetableState
    data class ReviewData(val timetable: Timetable) : TimetableState
    data class Error(val message: String) : TimetableState
}

data class ModuleColor(val container: Color, val onContainer: Color)

val lightColors = listOf(
    ModuleColor(Color(0xFFE3F2FD), Color(0xFF0D47A1)), // Blue
    ModuleColor(Color(0xFFFCE4EC), Color(0xFF880E4F)), // Pink
    ModuleColor(Color(0xFFE8F5E9), Color(0xFF1B5E20)), // Green
    ModuleColor(Color(0xFFFFF3E0), Color(0xFFE65100)), // Orange
    ModuleColor(Color(0xFFF3E5F5), Color(0xFF4A148C))  // Purple
)

val darkColors = listOf(
    ModuleColor(Color(0xFF0D47A1), Color(0xFFE3F2FD)), // Dark Blue
    ModuleColor(Color(0xFF880E4F), Color(0xFFFCE4EC)), // Dark Pink
    ModuleColor(Color(0xFF1B5E20), Color(0xFFE8F5E9)), // Dark Green
    ModuleColor(Color(0xFFE65100), Color(0xFFFFF3E0)), // Dark Orange
    ModuleColor(Color(0xFF4A148C), Color(0xFFF3E5F5))  // Dark Purple
)

@Composable
fun getModuleColor(code: String): ModuleColor {
    val colors = if (isSystemInDarkTheme()) darkColors else lightColors
    val index = abs(code.hashCode()) % colors.size
    return colors[index]
}