package com.example.modulo

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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